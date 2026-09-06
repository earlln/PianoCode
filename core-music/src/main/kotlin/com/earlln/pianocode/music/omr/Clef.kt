package com.earlln.pianocode.music.omr

import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.Pitch

/**
 * Where the counting starts.
 *
 * A position on a staff is only a pitch once the clef says what the bottom line is called.
 * The number held here is that line as a count of letters from C0 — seven to an octave —
 * because a step up the staff is always one letter, never a fixed number of semitones.
 */
enum class Clef(val bottomLineDiatonic: Int, val koreanName: String) {
    /** Bottom line E4. */
    TREBLE(4 * 7 + 2, "높은음자리표"),

    /** Bottom line G2. */
    BASS(2 * 7 + 4, "낮은음자리표"),
    ;

    /**
     * The written note at [step] positions above the bottom line, with [accidental] in
     * semitones as the key signature or a sign in front of the note dictates.
     */
    fun pitchAt(step: Int, accidental: Int = 0): Pitch {
        val diatonic = bottomLineDiatonic + step
        return Pitch(Note(Math.floorMod(diatonic, 7), accidental), Math.floorDiv(diatonic, 7))
    }

    /** The letter alone, which is what a key signature is stated in. */
    fun letterAt(step: Int): Int = Math.floorMod(bottomLineDiatonic + step, 7)

    /** Where [pitch] is written on this staff, which is what draws it back onto one. */
    fun stepOf(pitch: Pitch): Int =
        pitch.octave * 7 + pitch.note.letter - bottomLineDiatonic
}
