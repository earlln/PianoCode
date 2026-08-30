package com.earlln.pianocode.music

/**
 * A note written the way it appears on a staff: a letter plus an accidental.
 *
 * Two notes can sound identical yet be spelled differently (D# and Eb), and which
 * spelling is correct depends on the surrounding key. Keeping the letter and the
 * accidental apart — instead of collapsing everything to a pitch class — is what
 * lets the library print `Db` in Db major and `C#` in A major.
 */
data class Note(val letter: Int, val accidental: Int) : Comparable<Note> {

    init {
        require(letter in 0..6) { "letter must be 0..6 (C..B), was $letter" }
    }

    /** 0..11, where 0 is C. Enharmonic spellings share a pitch class. */
    val pitchClass: Int get() = ((LETTER_SEMITONES[letter] + accidental) % 12 + 12) % 12

    val name: String get() = LETTER_NAMES[letter] + accidentalText(accidental)

    /** Unicode spelling for display: uses ♯ / ♭ / ♮ instead of ASCII. */
    val prettyName: String get() = LETTER_NAMES[letter] + prettyAccidentalText(accidental)

    /** Korean note name, e.g. `C` -> `도`. Naturals only get a syllable; accidentals keep the sign. */
    val koreanName: String get() = KOREAN_SYLLABLES[letter] + prettyAccidentalText(accidental)

    /**
     * Moves this note by a generic interval: [letterSteps] steps along the alphabet and
     * [semitones] semitones. The accidental is whatever it takes to make both true, which
     * is exactly how chord tones are spelled (a third is always two letters up).
     */
    fun transposeBy(letterSteps: Int, semitones: Int): Note {
        val newLetter = Math.floorMod(letter + letterSteps, 7)
        val octaves = Math.floorDiv(letter + letterSteps, 7)
        val naturalSemitones = LETTER_SEMITONES[newLetter] + 12 * octaves
        val targetSemitones = LETTER_SEMITONES[letter] + accidental + semitones
        return Note(newLetter, targetSemitones - naturalSemitones)
    }

    /** True when the spelling needs more than a double sharp/flat, which no one wants to read. */
    val isPractical: Boolean get() = accidental in -2..2

    /** The same sound, respelled with at most one accidental, preferring [preferFlats]. */
    fun simplify(preferFlats: Boolean): Note = fromPitchClass(pitchClass, preferFlats)

    /** Ordering by written position, so C# sorts after C and before Db's letter D. */
    override fun compareTo(other: Note): Int =
        compareValuesBy(this, other, { it.letter }, { it.accidental })

    override fun toString(): String = name

    companion object {
        /** Semitone offset of each natural letter above C. */
        val LETTER_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val LETTER_NAMES = arrayOf("C", "D", "E", "F", "G", "A", "B")
        private val KOREAN_SYLLABLES = arrayOf("도", "레", "미", "파", "솔", "라", "시")

        val C = Note(0, 0)

        private val SHARP_SPELLINGS = arrayOf(
            Note(0, 0), Note(0, 1), Note(1, 0), Note(1, 1), Note(2, 0), Note(3, 0),
            Note(3, 1), Note(4, 0), Note(4, 1), Note(5, 0), Note(5, 1), Note(6, 0),
        )
        private val FLAT_SPELLINGS = arrayOf(
            Note(0, 0), Note(1, -1), Note(1, 0), Note(2, -1), Note(2, 0), Note(3, 0),
            Note(4, -1), Note(4, 0), Note(5, -1), Note(5, 0), Note(6, -1), Note(6, 0),
        )

        fun fromPitchClass(pitchClass: Int, preferFlats: Boolean = false): Note {
            val pc = Math.floorMod(pitchClass, 12)
            return if (preferFlats) FLAT_SPELLINGS[pc] else SHARP_SPELLINGS[pc]
        }

        fun accidentalText(accidental: Int): String = when {
            accidental > 0 -> "#".repeat(accidental)
            accidental < 0 -> "b".repeat(-accidental)
            else -> ""
        }

        fun prettyAccidentalText(accidental: Int): String = when (accidental) {
            2 -> "𝄪"      // 𝄪 double sharp
            1 -> "♯"            // ♯
            0 -> ""
            -1 -> "♭"           // ♭
            -2 -> "𝄫"     // 𝄫 double flat
            else -> accidentalText(accidental)
        }

        /**
         * Parses a written note such as `C`, `F#`, `Bb`, `E♭` or `G##`.
         * Returns null when [text] is not a note name.
         */
        fun parse(text: String): Note? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val letter = LETTER_NAMES.indexOf(trimmed.first().uppercaseChar().toString())
            if (letter < 0) return null
            var accidental = 0
            for (ch in trimmed.drop(1)) {
                when (ch) {
                    '#', '♯' -> accidental += 1
                    'b', 'B', '♭' -> accidental -= 1
                    'x', '\uD834' -> Unit // handled by the surrogate pair below
                    '\uDD2A' -> accidental += 2
                    '\uDD2B' -> accidental -= 2
                    '♮' -> Unit // natural sign changes nothing
                    else -> return null
                }
            }
            // A bare `x` in chord text means a double sharp (Cx == C##).
            if (trimmed.drop(1).contains('x')) accidental += 2
            return Note(letter, accidental)
        }

        /** The twelve roots in written order, spelled with sharps or flats. */
        fun chromaticRoots(preferFlats: Boolean): List<Note> =
            (0..11).map { fromPitchClass(it, preferFlats) }
    }
}

/** Absolute pitch: a [Note] placed in an octave, so it can be drawn on a keyboard. */
data class Pitch(val note: Note, val octave: Int) {
    /** MIDI number, where middle C (C4) is 60. */
    val midi: Int get() = (octave + 1) * 12 + Note.LETTER_SEMITONES[note.letter] + note.accidental

    override fun toString(): String = "${note.name}$octave"
}
