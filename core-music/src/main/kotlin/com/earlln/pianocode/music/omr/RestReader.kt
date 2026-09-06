package com.earlln.pianocode.music.omr

import kotlin.math.abs

/** A silence found on the staff, and how long it lasts. */
data class Rest(val x: Double, val quarters: Double)

/**
 * Finds the rests.
 *
 * Without these a bar's notes run into one another and the piece drifts further out of
 * time with every silence it passes, which is the difference between a tune you recognise
 * and one you do not.
 *
 * Rests are read by where they sit as much as by their shape, because two of them have no
 * shape to speak of. A semibreve rest and a minim rest are the same small black brick; the
 * only thing separating four beats of silence from two is that one hangs under a line and
 * the other sits on top of one. The rest of the family is told apart by height: each
 * halving of the value adds roughly half a staff space of squiggle.
 */
object RestReader {

    fun detect(
        inkWithoutStaffLines: MonoImage,
        staff: Staff,
        fromX: Int,
        heads: List<NoteHead>,
    ): List<Rest> {
        val space = staff.space
        if (space < 6) return emptyList()

        val blobs = Blobs.inStrip(
            image = inkWithoutStaffLines,
            x0 = fromX,
            x1 = staff.right,
            // A rest lives inside the staff. Looking wider only invites lyrics and slurs.
            top = (staff.top - space * 0.4).toInt(),
            bottom = (staff.bottom + space * 0.4).toInt(),
            maxGap = 0,
        )

        return blobs.mapNotNull { blob ->
            // Anything sharing a column with a note head is part of that note, not a rest.
            val centre = (blob.left + blob.right) / 2.0
            if (heads.any { abs(it.x - centre) < space * 0.9 }) return@mapNotNull null
            valueOf(blob, staff)?.let { Rest(centre, it) }
        }
    }

    private fun valueOf(blob: Blob, staff: Staff): Double? {
        val space = staff.space
        val width = blob.width / space
        val height = blob.height / space

        // A brick: wide, flat, and attached to a line. Which line, and which of its own
        // edges is doing the attaching, is the whole of the difference between four beats
        // of silence and two — the bricks themselves are identical. Counting in staff
        // positions is too coarse to see it, because the two overlap by most of their
        // height; the edges have to be measured against the lines in pixels.
        if (width in BRICK_WIDTH && height in BRICK_HEIGHT) {
            val hangsBelow = abs(blob.top - staff.yOfStep(WHOLE_LINE))
            val sitsAbove = abs(blob.bottom - staff.yOfStep(HALF_LINE))
            val nearest = minOf(hangsBelow, sitsAbove)
            if (nearest > space * BRICK_TOLERANCE) return null
            return if (hangsBelow <= sitsAbove) 4.0 else 2.0
        }

        if (width !in SQUIGGLE_WIDTH) return null
        // A crotchet or quaver rest is written about the middle of the staff. Requiring
        // that keeps a bar line's stub and a stray mark from being counted as silence.
        val middle = staff.stepOf((blob.top + blob.bottom) / 2.0)
        if (abs(middle - HALF_LINE) > 2) return null
        return when {
            height in QUAVER_HEIGHT -> 0.5
            height in CROTCHET_HEIGHT -> 1.0
            else -> null
        }
    }

    /** The second line from the top, which a semibreve rest hangs beneath. */
    private const val WHOLE_LINE = 6

    /** The middle line, which a minim rest sits on top of. */
    private const val HALF_LINE = 4

    /** How close a brick's edge must sit to its line, in staff spaces. */
    private const val BRICK_TOLERANCE = 0.35

    private val BRICK_WIDTH = 0.55..1.60
    private val BRICK_HEIGHT = 0.20..0.70
    private val SQUIGGLE_WIDTH = 0.35..1.20

    // A quaver rest is about two staff spaces of hook and stroke; a crotchet rest runs
    // most of the height of the staff. A semiquaver rest is a quaver rest with a second
    // hook and comes out near a crotchet's height, so it is read as a crotchet rather than
    // guessed at — being out by a beat is bad, and being out by four is worse.
    private val QUAVER_HEIGHT = 1.45..2.35
    private val CROTCHET_HEIGHT = 2.35..3.60
}
