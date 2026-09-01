package com.earlln.pianocode.music

/** Which hand a key is played with. */
enum class Hand(val koreanName: String) {
    LEFT("왼손"),
    RIGHT("오른손"),
}

/** One key, and the finger that goes on it. 1 is the thumb, 5 the little finger. */
data class FingerPlacement(val midi: Int, val hand: Hand, val finger: Int)

/**
 * Works out which finger goes on which key.
 *
 * A picture of lit-up keys says what to press but not how to press it, which is the part a
 * beginner is actually stuck on. The numbers here are the ones a teacher writes on the page:
 * thumb on the bottom, little finger on the top, and the middle chosen so the wider gap
 * falls where the hand can spread.
 */
object Fingering {

    /** One hand holds five keys; anything more is shared with the other. */
    private const val HAND_SPAN = 5

    /**
     * Fingers [pitches] as they would actually be played.
     *
     * The bass of a slash chord goes to the left hand, which is where it belongs on a lead
     * sheet — the right hand keeps the chord above it. Notes beyond one hand's five go to
     * the left hand too, lowest first.
     */
    @JvmOverloads
    fun forVoicing(pitches: List<Pitch>, slashBass: Boolean = false): List<FingerPlacement> {
        val sorted = pitches.sortedBy { it.midi }
        if (sorted.isEmpty()) return emptyList()

        var leftCount = if (slashBass && sorted.size >= 2) 1 else 0
        val overflow = sorted.size - leftCount - HAND_SPAN
        if (overflow > 0) leftCount += overflow

        val left = sorted.take(leftCount)
        val right = sorted.drop(leftCount)
        val rightFingers = rightHandFingers(right)

        // Going up in the left hand, the little finger is lowest and the thumb highest.
        return left.mapIndexed { index, pitch ->
            FingerPlacement(pitch.midi, Hand.LEFT, (HAND_SPAN - index).coerceAtLeast(1))
        } + right.mapIndexed { index, pitch ->
            FingerPlacement(pitch.midi, Hand.RIGHT, rightFingers[index])
        }
    }

    /** Convenience: the fingering for a chord's own voicing. */
    @JvmOverloads
    fun forChord(chord: Chord, startOctave: Int = 3, inversion: Int = 0): List<FingerPlacement> =
        forVoicing(chord.voicing(startOctave, inversion), chord.hasSlashBass)

    private fun rightHandFingers(notes: List<Pitch>): List<Int> = when (notes.size) {
        0 -> emptyList()
        1 -> listOf(1)
        2 -> listOf(1, 5)
        // The wider gap gets the wider reach. C E G rises by a third then a minor third and
        // is played 1-3-5; its first inversion E G C ends with a fourth, so the stretch is
        // put between the middle and the little finger and it becomes 1-2-5.
        3 -> listOf(1, if (notes[1].midi - notes[0].midi > notes[2].midi - notes[1].midi) 3 else 2, 5)
        // A seventh sits under the hand as it is: the little finger takes the top and the
        // ring finger is skipped, which is what keeps the reach comfortable.
        4 -> listOf(1, 2, 3, 5)
        else -> listOf(1, 2, 3, 4, 5)
    }
}
