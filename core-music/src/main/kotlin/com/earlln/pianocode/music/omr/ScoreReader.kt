package com.earlln.pianocode.music.omr

import com.earlln.pianocode.music.Pitch

/** One note as read off the page, placed in time. */
data class ReadNote(
    val pitch: Pitch,
    /** When it starts, counted in crotchets from the beginning of the piece. */
    val startQuarters: Double,
    val quarters: Double,
    val staffIndex: Int,
    /** Where it was found, so the app can show its reading over the page. */
    val x: Double,
    val y: Double,
)

/** What one staff turned out to be. */
data class ReadStaff(
    val staff: Staff,
    val clef: Clef,
    val key: KeySignature,
    val noteCount: Int,
)

/** A whole page, read. */
data class ReadScore(
    val notes: List<ReadNote>,
    val staves: List<ReadStaff>,
    val scale: PageScale?,
) {
    val isEmpty: Boolean get() = notes.isEmpty()

    /** Length in crotchets, which is what a player needs to know when to stop. */
    val lengthQuarters: Double
        get() = notes.maxOfOrNull { it.startQuarters + it.quarters } ?: 0.0
}

/**
 * Reads a page of music from ink to notes in time.
 *
 * The pieces before this each answer one question — where are the staves, where are the
 * heads, how long is this one, what key is it in — and this puts the answers in order. Two
 * decisions live here and nowhere else: which staves are played together, and where in
 * time each note falls.
 *
 * What it does not read is rests. Nothing here can tell where silence goes, so notes
 * follow one another without gaps, which is right for a melody written without rests and
 * short of the truth for anything else. It is stated plainly to the reader rather than
 * papered over.
 */
object ScoreReader {

    fun read(image: MonoImage): ReadScore {
        val scale = PageScale.estimate(image) ?: return ReadScore(emptyList(), emptyList(), null)
        val staves = StaffDetector.detect(image, scale)
        if (staves.isEmpty()) return ReadScore(emptyList(), emptyList(), scale)

        val ink = StaffLines.remove(image, staves, scale)
        val read = staves.mapIndexed { index, staff ->
            readStaff(
                ink,
                staff,
                Reach.between(staff, staves.getOrNull(index - 1), staves.getOrNull(index + 1)),
            )
        }
        val notes = placeInTime(read, systemsOf(staves))
        return ReadScore(
            notes = notes,
            staves = read.mapIndexed { index, one ->
                ReadStaff(one.staff, one.clef, one.key, notes.count { it.staffIndex == index })
            },
            scale = scale,
        )
    }

    private fun readStaff(ink: MonoImage, staff: Staff, reach: Reach): StaffReading {
        val space = staff.space
        val clef = ClefReader.detect(ink, staff, reach)
        val clefEnd = ClefReader.endOf(ink, staff, reach)
        val key = KeyReader.detect(ink, staff, clefEnd)
        // Leave room for the clef, the signature and the time signature after it. A note is
        // never written this early, so nothing is lost and several look-alikes are skipped.
        val signCount = key.sharps + key.flats
        val musicFrom = clefEnd + (space * (2.0 + signCount * 1.15)).toInt()
        // Ledger notes reach away from the staff, but never past halfway to the next
        // one — on a page of piano music that would be the other hand's notes.
        val heads = HeadDetector.detect(
            ink,
            staff,
            topStep = staff.stepOf(reach.top.toDouble()).coerceAtMost(14),
            bottomStep = staff.stepOf(reach.bottom.toDouble()).coerceAtLeast(-6),
            fromX = musicFrom,
        )
        val symbols = SymbolReader.read(ink, staff, heads)
        val signs = symbols.mapNotNull { symbol ->
            AccidentalReader.before(ink, staff, symbol.head)?.let { symbol to it }
        }.toMap()
        return StaffReading(staff, clef, key, symbols, BarLines.detect(ink, staff, musicFrom), signs)
    }

    /**
     * Groups the staves into what is played at once.
     *
     * Piano music braces two staves together and plays them at the same time; a lead sheet
     * has one staff a line and plays them one after another. The two look identical to
     * everything before this and differ in one measurable way: a braced pair sits closer
     * together than two consecutive systems do. So the gaps are compared, and only a page
     * whose gaps clearly alternate is treated as a grand staff.
     */
    private fun systemsOf(staves: List<Staff>): List<List<Int>> {
        if (staves.size < 2) return staves.indices.map { listOf(it) }
        val gaps = staves.zipWithNext { a, b -> b.top - a.bottom }
        if (staves.size % 2 != 0) return staves.indices.map { listOf(it) }

        val inner = gaps.filterIndexed { index, _ -> index % 2 == 0 }
        val outer = gaps.filterIndexed { index, _ -> index % 2 == 1 }
        if (outer.isEmpty()) {
            // Exactly two staves: a brace is likely when they sit closer than a stave is tall.
            return if (gaps.first() < staves.first().space * 5) listOf(listOf(0, 1))
            else listOf(listOf(0), listOf(1))
        }
        val braced = inner.max() < outer.min() * 0.65
        return if (braced) staves.indices.chunked(2) else staves.indices.map { listOf(it) }
    }

    /**
     * Lays the read symbols out in time.
     *
     * Heads sharing a column are one chord and start together; systems follow one another,
     * and the staves inside a system all start where the system does. Written music says
     * far more about timing than this — bar lengths, rests, ties — but a melody read this
     * way comes out in the right order at the right relative lengths, which is what turns
     * a page into something you can hear.
     */
    private fun placeInTime(
        readings: List<StaffReading>,
        systems: List<List<Int>>,
    ): List<ReadNote> {
        val notes = mutableListOf<ReadNote>()
        var systemStart = 0.0
        for (system in systems) {
            var systemEnd = systemStart
            for (index in system) {
                val reading = readings.getOrNull(index) ?: continue
                var time = systemStart
                for (chord in chordsOf(reading)) {
                    val length = chord.maxOf { it.symbol.quarters }
                    for (voice in chord) {
                        notes += ReadNote(
                            pitch = voice.pitch,
                            startQuarters = time,
                            quarters = voice.symbol.quarters,
                            staffIndex = index,
                            x = voice.symbol.x,
                            y = voice.symbol.head.y,
                        )
                    }
                    time += length
                }
                if (time > systemEnd) systemEnd = time
            }
            systemStart = systemEnd
        }
        return notes.sortedWith(compareBy({ it.startQuarters }, { it.pitch.midi }))
    }

    /**
     * Turns one staff's symbols into chords, applying every accidental on the way.
     *
     * A sign in front of a note holds to the end of the bar for that letter in that
     * octave, which is why the bar lines were found; without them a single sharp would be
     * either forgotten at once or held to the end of the piece.
     */
    private fun chordsOf(reading: StaffReading): List<List<Voice>> {
        val space = reading.staff.space
        val chords = mutableListOf<List<Voice>>()
        val inBar = mutableMapOf<Pair<Int, Int>, Int>()
        var barIndex = 0

        var index = 0
        val symbols = reading.symbols
        while (index < symbols.size) {
            var end = index + 1
            while (end < symbols.size && symbols[end].x - symbols[end - 1].x < space * 0.75) end++

            val at = symbols[index].x
            while (barIndex < reading.bars.size && reading.bars[barIndex] < at) {
                inBar.clear()
                barIndex++
            }

            chords += symbols.subList(index, end).map { symbol ->
                val letter = reading.clef.letterAt(symbol.step)
                val octave = (reading.clef.bottomLineDiatonic + symbol.step).floorDiv(7)
                val written = reading.signs[symbol]
                val accidental = when {
                    written != null -> written.semitones.also { inBar[letter to octave] = it }
                    inBar.containsKey(letter to octave) -> inBar.getValue(letter to octave)
                    else -> reading.key.accidentalFor(letter)
                }
                Voice(symbol, reading.clef.pitchAt(symbol.step, accidental))
            }
            index = end
        }
        return chords
    }

    private class StaffReading(
        val staff: Staff,
        val clef: Clef,
        val key: KeySignature,
        val symbols: List<NoteSymbol>,
        val bars: List<Int>,
        /** The sign standing in front of each head, where there is one. */
        val signs: Map<NoteSymbol, Accidental>,
    )

    private data class Voice(val symbol: NoteSymbol, val pitch: Pitch)
}
