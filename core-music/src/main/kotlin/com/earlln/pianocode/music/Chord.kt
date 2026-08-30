package com.earlln.pianocode.music

/**
 * A concrete chord: a quality rooted on a note, optionally over a different bass note
 * (a slash chord such as `C/E`).
 */
data class Chord(
    val root: Note,
    val quality: ChordQuality,
    val bass: Note? = null,
) {
    /** The chord tones, spelled for this root: C7 gives C E G B♭, never C E G A♯. */
    val notes: List<Note>
        get() = quality.tones.map { root.transposeBy(it.letterSteps, it.semitones) }

    /** Notes paired with the degree they play, for the "구성음" table. */
    val tonesWithNotes: List<Pair<ChordTone, Note>>
        get() = quality.tones.map { it to root.transposeBy(it.letterSteps, it.semitones) }

    /** ASCII symbol — `Bbm7b5`, `C/E`. Safe to write back onto a sheet. */
    val symbol: String
        get() = root.name + quality.symbol + (bass?.let { "/${it.name}" } ?: "")

    /** Display symbol using ♯/♭ glyphs. */
    val prettySymbol: String
        get() = root.prettyName + quality.prettySymbol + (bass?.let { "/${it.prettyName}" } ?: "")

    val displayName: String get() = "${root.prettyName} ${quality.koreanName}"

    /** True when a slash bass is present and is not simply the root. */
    val hasSlashBass: Boolean get() = bass != null && bass.pitchClass != root.pitchClass

    /** Pitch classes sounded, bass included, with duplicates removed. */
    val pitchClasses: List<Int>
        get() = (listOfNotNull(bass?.pitchClass) + notes.map { it.pitchClass }).distinct()

    /**
     * Lays the chord out on a keyboard.
     *
     * Tones are stacked upward from [startOctave]: each note is placed in the lowest octave
     * that keeps it above the previous one, which is how the chord is actually played.
     * [inversion] lifts that many bottom notes up an octave, and a slash bass is added below.
     */
    fun voicing(startOctave: Int = 3, inversion: Int = 0): List<Pitch> {
        val stacked = ArrayList<Pitch>(quality.tones.size)
        var previousMidi = Int.MIN_VALUE
        for (tone in quality.tones) {
            val note = root.transposeBy(tone.letterSteps, tone.semitones)
            var octave = startOctave
            var pitch = Pitch(note, octave)
            while (pitch.midi <= previousMidi) {
                octave++
                pitch = Pitch(note, octave)
            }
            previousMidi = pitch.midi
            stacked += pitch
        }

        val steps = if (stacked.isEmpty()) 0 else Math.floorMod(inversion, stacked.size)
        val inverted = ArrayList(stacked)
        repeat(steps) {
            val lowest = inverted.removeAt(0)
            var raised = Pitch(lowest.note, lowest.octave + 1)
            val highestMidi = inverted.lastOrNull()?.midi ?: Int.MIN_VALUE
            while (raised.midi <= highestMidi) raised = Pitch(raised.note, raised.octave + 1)
            inverted += raised
        }

        val slash = bass?.takeIf { hasSlashBass }?.let { bassNote ->
            var octave = inverted.firstOrNull()?.octave ?: startOctave
            var pitch = Pitch(bassNote, octave)
            val lowestMidi = inverted.firstOrNull()?.midi ?: Int.MAX_VALUE
            while (pitch.midi >= lowestMidi) {
                octave--
                pitch = Pitch(bassNote, octave)
            }
            pitch
        }
        return listOfNotNull(slash) + inverted
    }

    /** How many inversions this chord has, counting root position as one position. */
    val positionCount: Int get() = quality.tones.size

    /** `루트 포지션`, `1st 자리바꿈`, … for the inversion selector. */
    fun positionLabel(inversion: Int): String = when (Math.floorMod(inversion, positionCount)) {
        0 -> "루트 포지션"
        1 -> "1전위 (1st inversion)"
        2 -> "2전위 (2nd inversion)"
        3 -> "3전위 (3rd inversion)"
        else -> "${Math.floorMod(inversion, positionCount)}전위"
    }

    /** Transposes by [semitones], respelling for the target key when one is given. */
    fun transpose(semitones: Int, targetKey: Key? = null): Chord {
        val newRoot = transposeNote(root, semitones, targetKey)
        val newBass = bass?.let { transposeNote(it, semitones, targetKey) }
        return copy(root = newRoot, bass = newBass)
            .readable(preferFlats = targetKey?.prefersFlats ?: (semitones < 0))
    }

    /**
     * The same chord, respelled when the written root forces absurd accidentals.
     *
     * A key such as C♯ major genuinely writes E♯, but stacking an altered dominant on E♯
     * would need F triple-sharp for its ♯9. Players write F7alt there, so when any tone
     * runs past a double accidental the root is swapped for its enharmonic twin.
     */
    fun readable(preferFlats: Boolean = root.accidental < 0): Chord {
        if (root.isPractical && notes.all { it.isPractical }) return this
        val candidates = listOf(
            Note.fromPitchClass(root.pitchClass, preferFlats),
            Note.fromPitchClass(root.pitchClass, !preferFlats),
        )
        val betterRoot = candidates.firstOrNull { candidate ->
            copy(root = candidate).notes.all { it.isPractical }
        } ?: return this
        val betterBass = bass?.takeIf { !it.isPractical }
            ?.let { Note.fromPitchClass(it.pitchClass, preferFlats) } ?: bass
        return copy(root = betterRoot, bass = betterBass)
    }

    override fun toString(): String = symbol

    companion object {
        /**
         * Moves a written note by [semitones]. When a [targetKey] is supplied the result is
         * respelled to that key's accidentals, so a transposition into F major prints B♭
         * rather than A♯. Without a key the interval spelling is kept, which is the
         * theoretically exact choice, and is only simplified when it would need a triple sharp.
         */
        fun transposeNote(note: Note, semitones: Int, targetKey: Key?): Note {
            if (targetKey != null) {
                return targetKey.spell(Math.floorMod(note.pitchClass + semitones, 12))
            }
            val letterSteps = defaultLetterSteps(Math.floorMod(semitones, 12))
            val octaveSteps = 7 * Math.floorDiv(semitones, 12)
            val moved = note.transposeBy(letterSteps + octaveSteps, semitones)
            return if (moved.isPractical) moved else moved.simplify(preferFlats = semitones < 0)
        }

        /** Letter distance of the smallest sensible spelling of each ascending interval. */
        private fun defaultLetterSteps(semitones: Int): Int = when (semitones) {
            0 -> 0; 1 -> 1; 2 -> 1; 3 -> 2; 4 -> 2; 5 -> 3; 6 -> 3
            7 -> 4; 8 -> 5; 9 -> 5; 10 -> 6; 11 -> 6
            else -> 0
        }
    }
}
