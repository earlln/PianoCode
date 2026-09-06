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
    /** The event it came from, so a correction can be tied back to what is heard. */
    val eventId: Int = 0,
)

/** What one staff turned out to be. */
data class ReadStaff(
    val staff: Staff,
    val clef: Clef,
    val key: KeySignature,
    val noteCount: Int,
)

/**
 * A whole page, read.
 *
 * What is stored is the events in the order they are written, not the notes with their
 * start times: the times are worked out from the events whenever they are wanted. That is
 * what makes the reading correctable — lengthen a note and everything after it moves on
 * its own, with nothing left to forget to update.
 */
data class ReadScore(
    val events: List<ScoreEvent>,
    val staves: List<ReadStaff>,
    /** Staff indices that sound together; each group follows the one before it. */
    val systems: List<List<Int>>,
    val scale: PageScale?,
) {
    val notes: List<ReadNote> get() = Timeline.notesOf(events, systems)

    val timeline: List<TimedEvent> get() = Timeline.of(events, systems)

    val isEmpty: Boolean get() = events.none { !it.isRest }

    /** Length in crotchets, which is what a player needs to know when to stop. */
    val lengthQuarters: Double get() = Timeline.lengthQuarters(events, systems)

    fun withEvents(events: List<ScoreEvent>): ReadScore = copy(events = events)

    fun staffOf(event: ScoreEvent): ReadStaff? = staves.getOrNull(event.staffIndex)
}

/**
 * Reads a page of music from ink to notes in time.
 *
 * The pieces before this each answer one question — where are the staves, where are the
 * heads, how long is this one, what key is it in — and this puts the answers in order. Two
 * decisions live here and nowhere else: which staves are played together, and where in
 * time each note falls.
 *
 * Rests take their time alongside the notes, which is what keeps a bar in time; without
 * them every silence shortens the piece and everything after it arrives early. Ties,
 * grace notes and triplets are still not read, and a semiquaver rest is counted as a
 * crotchet rest rather than guessed at.
 */
object ScoreReader {

    fun read(image: MonoImage): ReadScore {
        val scale = PageScale.estimate(image)
            ?: return ReadScore(emptyList(), emptyList(), emptyList(), null)
        val staves = StaffDetector.detect(image, scale)
        if (staves.isEmpty()) return ReadScore(emptyList(), emptyList(), emptyList(), scale)

        val ink = StaffLines.remove(image, staves, scale)
        val read = staves.mapIndexed { index, staff ->
            readStaff(
                ink,
                staff,
                Reach.between(staff, staves.getOrNull(index - 1), staves.getOrNull(index + 1)),
            )
        }
        val systems = systemsOf(staves)
        val events = eventsOf(read)
        return ReadScore(
            events = events,
            staves = read.mapIndexed { index, one ->
                ReadStaff(
                    staff = one.staff,
                    clef = one.clef,
                    key = one.key,
                    noteCount = events.count { it.staffIndex == index && !it.isRest },
                )
            },
            systems = systems,
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
        val rests = RestReader.detect(ink, staff, musicFrom, heads)
        return StaffReading(
            staff = staff,
            clef = clef,
            key = key,
            symbols = symbols,
            bars = BarLines.detect(ink, staff, musicFrom),
            signs = signs,
            rests = rests,
        )
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
     * Turns each staff's reading into the events that make it up.
     *
     * Heads sharing a column are one chord and become one event; a rest is an event with
     * nothing in it. When each of these happens is not decided here — that falls out of
     * the order they are in, so a correction later cannot leave the timing stale.
     */
    private fun eventsOf(readings: List<StaffReading>): List<ScoreEvent> {
        val events = mutableListOf<ScoreEvent>()
        var id = 1
        readings.forEachIndexed { index, reading ->
            for (moment in momentsOf(reading)) {
                events += ScoreEvent(
                    id = id++,
                    staffIndex = index,
                    pitches = moment.voices.map { it.pitch },
                    quarters = moment.quarters,
                    x = moment.x,
                    y = moment.voices.minByOrNull { it.symbol.head.y }?.symbol?.head?.y
                        ?: reading.staff.top,
                )
            }
        }
        return events
    }

    /**
     * Turns one staff's symbols into chords, applying every accidental on the way.
     *
     * A sign in front of a note holds to the end of the bar for that letter in that
     * octave, which is why the bar lines were found; without them a single sharp would be
     * either forgotten at once or held to the end of the piece.
     */
    /**
     * Everything that happens on one staff, in the order it is written.
     *
     * A rest is a moment with no notes in it: it takes its time and sounds nothing. Merging
     * the two by where they sit on the page is what keeps a bar in time — without it every
     * silence shortens the piece and everything after it arrives early.
     */
    private fun momentsOf(reading: StaffReading): List<Moment> {
        val chords = chordsOf(reading).map { voices ->
            Moment(
                x = voices.first().symbol.x,
                quarters = voices.maxOf { it.symbol.quarters },
                voices = voices,
            )
        }
        val silences = reading.rests.map { Moment(it.x, it.quarters, emptyList()) }
        return (chords + silences).sortedBy { it.x }
    }

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
        val rests: List<Rest>,
    )

    /** One thing that happens on a staff: a chord, a single note, or a silence. */
    private class Moment(val x: Double, val quarters: Double, val voices: List<Voice>)

    private data class Voice(val symbol: NoteSymbol, val pitch: Pitch)
}
