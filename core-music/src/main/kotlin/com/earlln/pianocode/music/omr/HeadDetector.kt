package com.earlln.pianocode.music.omr

import kotlin.math.abs
import kotlin.math.roundToInt

/** A note head found on a staff, with the position it is written on. */
data class NoteHead(
    val x: Double,
    val y: Double,
    /** Positions above the bottom line: 0 the bottom line, 1 the space above it. */
    val step: Int,
    /** Filled heads are quarters and shorter; open ones are minims and semibreves. */
    val filled: Boolean,
    val score: Double,
)

/**
 * Finds note heads by shape rather than by cutting the page into pieces.
 *
 * Splitting the ink into connected blobs is the obvious approach and the wrong one here: a
 * beamed group is one blob containing four heads, four stems and two beams, and there is no
 * clean place to cut it. Measuring instead — is there a head-sized, head-shaped patch of
 * ink centred here? — finds heads inside a beamed group as readily as a lone crotchet,
 * because the question never depends on what the head is attached to.
 *
 * Four rectangles answer it:
 *  - the **core**, well inside the head: solid for a filled head, empty for an open one;
 *  - the **body**, the whole head: mostly ink either way;
 *  - the **flanks**, its thick left and right sides: ink for an open head, which is how an
 *    open head is told from a gap between two other things;
 *  - a **wide** strip reaching well past the head: mostly white for a head, and nearly
 *    solid for a beam, which is the one other thing on a page shaped like a filled head.
 */
object HeadDetector {

    fun detect(
        inkWithoutStaffLines: MonoImage,
        staff: Staff,
        topStep: Int = 14,
        bottomStep: Int = -6,
    ): List<NoteHead> {
        val sums = InkSums(inkWithoutStaffLines)
        val space = staff.space
        if (space < 4) return emptyList()

        // Every window is a little smaller than the head it looks for, so a head that
        // photographed thin still fills it. Measuring the head's exact outline would be
        // more precise and far more fragile.
        val rx = (space * 0.50).roundToInt().coerceAtLeast(2)
        val ry = (space * 0.38).roundToInt().coerceAtLeast(2)
        val coreX = (space * 0.24).roundToInt().coerceAtLeast(1)
        val coreY = (space * 0.16).roundToInt().coerceAtLeast(1)
        val wideX = (space * 1.15).roundToInt().coerceAtLeast(rx + 2)
        val flankW = (space * 0.20).roundToInt().coerceAtLeast(1)
        val flankH = (space * 0.18).roundToInt().coerceAtLeast(1)

        val yTop = staff.yOfStep(topStep).roundToInt().coerceAtLeast(0)
        val yBottom = staff.yOfStep(bottomStep).roundToInt()
            .coerceAtMost(inkWithoutStaffLines.height - 1)

        val found = mutableListOf<NoteHead>()
        for (y in yTop..yBottom) {
            for (x in staff.left..staff.right) {
                val core = sums.ratio(x - coreX, y - coreY, x + coreX, y + coreY)
                val body = sums.ratio(x - rx, y - ry, x + rx, y + ry)
                if (body < MIN_BODY) continue
                val wide = sums.ratio(x - wideX, y - coreY, x + wideX, y + coreY)
                if (wide > MAX_WIDE) continue

                val filled = core >= FILLED_CORE && body >= FILLED_BODY
                if (filled) {
                    // A beam is the one other solid, head-sized thing on a page, and near
                    // its end it passes every test above. It gives itself away by being
                    // thin: about half a staff space, where a head is a whole one. Measured
                    // down the middle of the candidate, where a head is at its tallest and
                    // no stem reaches.
                    val tall = columnRun(inkWithoutStaffLines, x, y)
                    if (tall < space * MIN_HEAD_HEIGHT || tall > space * MAX_HEAD_HEIGHT) continue
                    // And a beam that has merged with a staff line is thick enough to pass
                    // the height test, so measure the other way too: a head is about a
                    // space and a third wide, a ledger line twice that, a beam far more.
                    val long = rowRun(inkWithoutStaffLines, x, y)
                    if (long > space * MAX_HEAD_WIDTH) continue
                    found += NoteHead(x.toDouble(), y.toDouble(), 0, true, body)
                    continue
                }
                if (core > OPEN_CORE) continue
                val left = sums.ratio(x - rx, y - coreY, x - rx + flankW, y + coreY)
                val right = sums.ratio(x + rx - flankW, y - coreY, x + rx, y + coreY)
                if (left < OPEN_FLANK || right < OPEN_FLANK) continue
                // The hole has to be enclosed, not merely flanked. Without this, the edge
                // of any horizontal bar reads as an open head: white above it, ink below,
                // which looks the same as a ring when only the left and right are checked.
                val above = sums.ratio(x - coreX, y - ry, x + coreX, y - ry + flankH)
                val below = sums.ratio(x - coreX, y + ry - flankH, x + coreX, y + ry)
                if (above < OPEN_ENCLOSE || below < OPEN_ENCLOSE) continue
                // The ring has to close around the hole, not merely sit above and below
                // it. The slot between two beams is bounded top and bottom by the beams
                // and passes every test so far, and gives itself away by running the whole
                // length of the beam where a note's hole is barely a staff space across.
                if (whiteRowRun(inkWithoutStaffLines, x, y) > space * MAX_HOLE_WIDTH) continue
                found += NoteHead(x.toDouble(), y.toDouble(), 0, false, (left + right) / 2)
            }
        }
        return suppress(found, staff)
    }

    /** How tall the unbroken ink is through this column, counting [y] itself. */
    private fun columnRun(image: MonoImage, x: Int, y: Int): Int {
        if (!image.isInk(x, y)) return 0
        var top = y
        while (image.isInk(x, top - 1)) top--
        var bottom = y
        while (image.isInk(x, bottom + 1)) bottom++
        return bottom - top + 1
    }

    /** How far the paper runs uninterrupted across this row, counting [x] itself. */
    private fun whiteRowRun(image: MonoImage, x: Int, y: Int): Int {
        if (image.isInk(x, y)) return 0
        var left = x
        while (left > 0 && !image.isInk(left - 1, y)) left--
        var right = x
        while (right < image.width - 1 && !image.isInk(right + 1, y)) right++
        return right - left + 1
    }

    /** How wide the unbroken ink is through this row, counting [x] itself. */
    private fun rowRun(image: MonoImage, x: Int, y: Int): Int {
        if (!image.isInk(x, y)) return 0
        var left = x
        while (image.isInk(left - 1, y)) left--
        var right = x
        while (image.isInk(right + 1, y)) right++
        return right - left + 1
    }

    /**
     * Keeps the best candidate in each neighbourhood.
     *
     * Every head answers yes over a patch of pixels, so the raw list holds a cluster per
     * head. The window is narrower than the gap between two heads a third apart, so a
     * chord keeps all of its notes: the notes of a chord differ by a whole space or more
     * in height, or else the engraver offsets them sideways, and either way they survive.
     */
    private fun suppress(candidates: List<NoteHead>, staff: Staff): List<NoteHead> {
        val nearX = staff.space * 0.75
        val nearY = staff.space * 0.80
        val kept = mutableListOf<NoteHead>()
        for (head in candidates.sortedByDescending { it.score }) {
            val clash = kept.any { abs(it.x - head.x) < nearX && abs(it.y - head.y) < nearY }
            if (!clash) kept += head
        }
        // Reading order, and a chord is one place in it. The heads of a chord never
        // land on exactly the same column once measured, so they are gathered by how far
        // apart they are and then ordered top note first, the way a chord is written.
        val byX = kept.map { it.copy(step = staff.stepOf(it.y)) }.sortedBy { it.x }
        val ordered = mutableListOf<NoteHead>()
        var index = 0
        while (index < byX.size) {
            var end = index + 1
            while (end < byX.size && byX[end].x - byX[end - 1].x < nearX) end++
            ordered += byX.subList(index, end).sortedByDescending { it.step }
            index = end
        }
        return ordered
    }

    // Measured against heads drawn at the sizes engravers use: a head is about 1.1 staff
    // spaces wide and just under one tall, and a beam is about half a space thick.
    private const val MIN_BODY = 0.30
    private const val MAX_WIDE = 0.80
    private const val FILLED_CORE = 0.85
    private const val FILLED_BODY = 0.60
    private const val OPEN_CORE = 0.40
    private const val OPEN_FLANK = 0.40
    private const val OPEN_ENCLOSE = 0.35
    private const val MIN_HEAD_HEIGHT = 0.62
    private const val MAX_HEAD_HEIGHT = 1.60
    private const val MAX_HEAD_WIDTH = 2.60
    private const val MAX_HOLE_WIDTH = 1.00
}
