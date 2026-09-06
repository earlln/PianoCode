package com.earlln.pianocode.music.omr

import kotlin.math.roundToInt

/**
 * A note head together with everything attached to it that says how long it lasts.
 *
 * Written music splits a note's identity in two: the head's height is the pitch, and the
 * head's colour plus whatever hangs off it is the duration. This is the second half.
 */
data class NoteSymbol(
    val head: NoteHead,
    /** null when nothing is attached, which only a semibreve is. */
    val stemUp: Boolean?,
    /** Beams across the stem, or flags on it — both mean the same thing to the count. */
    val beams: Int,
    val dots: Int,
) {
    val step: Int get() = head.step
    val x: Double get() = head.x

    /** Length in crotchets, which is the unit the rest of the library counts in. */
    val quarters: Double
        get() {
            val plain = when {
                !head.filled && stemUp == null -> 4.0
                !head.filled -> 2.0
                else -> when (beams) {
                    0 -> 1.0
                    1 -> 0.5
                    2 -> 0.25
                    else -> 0.125
                }
            }
            // Each dot adds half of what is already there, so two dots make it 1.75x.
            var total = plain
            var add = plain / 2
            repeat(dots.coerceAtMost(2)) {
                total += add
                add /= 2
            }
            return total
        }
}

/**
 * Reads the duration marks around each head.
 *
 * This runs on the page with the staff lines already taken off, which matters more here
 * than anywhere else: a staff line is a very wide horizontal stretch of ink, and a beam is
 * recognised by being a wide horizontal stretch of ink. Where a stem crosses a line the
 * line survives removal, but only across the stem's own two or three columns, so it is no
 * wider than the stem and never mistaken for a beam.
 */
object SymbolReader {

    fun read(inkWithoutStaffLines: MonoImage, staff: Staff, heads: List<NoteHead>): List<NoteSymbol> =
        heads.map { read(inkWithoutStaffLines, staff, it, heads) }

    private fun read(image: MonoImage, staff: Staff, head: NoteHead, all: List<NoteHead>): NoteSymbol {
        val space = staff.space
        val up = stemLength(image, head, space, up = true)
        val down = stemLength(image, head, space, up = false)
        val stemUp = when {
            up.length < space * MIN_STEM && down.length < space * MIN_STEM -> null
            up.length >= down.length -> true
            else -> false
        }
        val stem = when (stemUp) {
            null -> null
            true -> up
            false -> down
        }
        // The other notes of a chord hang off this same stem, and a note head is exactly
        // as wide as a beam is. Their heights are handed to the beam count so it can step
        // over them instead of counting each one as a beam.
        val others = all
            .filter { it !== head && kotlin.math.abs(it.x - head.x) < space * 1.4 }
            .map { it.y }
        val beams = if (stem == null) 0 else countBeams(image, stem, space, others)
        return NoteSymbol(head, stemUp, beams, countDots(image, head, space))
    }

    /**
     * How far the stem runs from the side of the head, and where it ends.
     *
     * An engraver puts the stem on the right of the head going up, or on the left going
     * down, and the two are looked for separately rather than guessed from the pitch —
     * inner voices and beamed groups break that rule all the time.
     */
    private fun stemLength(image: MonoImage, head: NoteHead, space: Double, up: Boolean): Stem {
        val near = (space * 0.30).roundToInt()
        val far = (space * 0.90).roundToInt()
        val columns = if (up) {
            (head.x.roundToInt() + near)..(head.x.roundToInt() + far)
        } else {
            (head.x.roundToInt() - far)..(head.x.roundToInt() - near)
        }
        // Start just clear of the head so its own ink is not counted as stem.
        val start = head.y.roundToInt() + if (up) -(space * 0.40).roundToInt() else (space * 0.40).roundToInt()
        var best = Stem(head.x.roundToInt(), start, 0, up)
        for (x in columns) {
            var y = start
            var run = 0
            // A stem photographs with the odd pale pixel in it; one blank row is not the end.
            var blanks = 0
            while (y in 0 until image.height) {
                if (image.isInk(x, y)) {
                    run += blanks + 1
                    blanks = 0
                } else {
                    blanks++
                    if (blanks > 1) break
                }
                y += if (up) -1 else 1
            }
            if (run > best.length) best = Stem(x, y + if (up) 1 else -1, run, up)
        }
        return best
    }

    /**
     * Counts the bars crossing the stem near its far end.
     *
     * Beams and flags are counted together on purpose. They mean exactly the same thing —
     * one of either halves the note — and telling them apart would only be needed to
     * redraw the page, which this never does.
     */
    private fun countBeams(
        image: MonoImage,
        stem: Stem,
        space: Double,
        otherHeads: List<Double>,
    ): Int {
        if (stem.length < space * MIN_STEM) return 0
        val wide = space * BEAM_WIDTH
        val reach = (space * 2.4).roundToInt().coerceAtMost(stem.length - (space * 0.7).roundToInt())
        if (reach <= 0) return 0

        var bands = 0
        var inBand = false
        var bandRows = 0
        for (offset in 0 until reach) {
            val y = if (stem.up) stem.tipY + offset else stem.tipY - offset
            if (otherHeads.any { kotlin.math.abs(it - y) < space * 0.60 }) continue
            val run = rowRun(image, stem.x, y)
            if (run >= wide) {
                bandRows++
                inBand = true
            } else if (inBand) {
                bands += bandsIn(bandRows, space)
                inBand = false
                bandRows = 0
            }
        }
        if (inBand) bands += bandsIn(bandRows, space)
        return bands.coerceAtMost(4)
    }

    /**
     * Beams that touch come through as one thick band, so a band is measured rather than
     * counted: each beam is about half a staff space thick with a quarter-space gap.
     */
    private fun bandsIn(rows: Int, space: Double): Int =
        (rows / (space * 0.7)).roundToInt().coerceAtLeast(1)

    /**
     * Looks for the dot that adds half a note's length again.
     *
     * It sits after the head, always in a space — pushed up out of the way when the head
     * is on a line — so the search covers both heights and takes any small blob it finds.
     */
    private fun countDots(image: MonoImage, head: NoteHead, space: Double): Int {
        val from = (head.x + space * 0.75).roundToInt()
        val to = (head.x + space * 2.4).roundToInt()
        val top = (head.y - space * 0.75).roundToInt()
        val bottom = (head.y + space * 0.75).roundToInt()
        val biggest = space * 0.45
        val smallest = space * 0.16

        var dots = 0
        var x = from
        // A ledger line runs out from under the head into the very place a dot would be.
        // Step over anything still joined to the head before looking for one.
        while (x <= to && (top..bottom).any { image.isInk(x, it) }) x++
        while (x <= to) {
            if ((top..bottom).none { image.isInk(x, it) }) {
                x++
                continue
            }
            var end = x
            while (end + 1 <= to && (top..bottom).any { image.isInk(end + 1, it) }) end++
            val rows = (top..bottom).filter { y -> (x..end).any { image.isInk(it, y) } }
            val width = end - x + 1
            val height = if (rows.isEmpty()) 0 else rows.last() - rows.first() + 1
            // A dot is small in both directions and about as tall as it is wide. A stub
            // of ledger line is small too, but flat.
            val round = height in (width / 2)..(width * 2)
            if (width <= biggest && height <= biggest &&
                width >= smallest && height >= smallest && round
            ) {
                dots++
            }
            x = end + 1
        }
        return dots.coerceAtMost(2)
    }

    private fun rowRun(image: MonoImage, x: Int, y: Int): Int {
        if (!image.isInk(x, y)) return 0
        var left = x
        while (image.isInk(left - 1, y)) left--
        var right = x
        while (image.isInk(right + 1, y)) right++
        return right - left + 1
    }

    private data class Stem(val x: Int, val tipY: Int, val length: Int, val up: Boolean)

    private const val MIN_STEM = 1.8
    private const val BEAM_WIDTH = 0.95
}
