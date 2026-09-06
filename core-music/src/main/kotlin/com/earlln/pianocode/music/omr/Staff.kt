package com.earlln.pianocode.music.omr

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How big the engraving is on this particular page.
 *
 * Everything in written music is measured in staff spaces, not pixels — a note head is
 * about one space wide, a stem about three and a half spaces long, a beam about half a
 * space thick. Measure the space once and every later threshold can be written in those
 * terms, which is what lets the same code read a phone photo and a 300dpi scan.
 */
data class PageScale(val lineThickness: Int, val space: Int) {
    /** Line centre to line centre. */
    val step: Int get() = space + lineThickness

    companion object {
        /**
         * Estimates the scale from how long the black and white stretches are down a column.
         *
         * Down any column that crosses a staff, the ink runs are staff lines and the gaps
         * between them are staff spaces — and there are more of those than of anything else
         * on a page of music. So the most common ink run is the line thickness and the most
         * common gap is the space, with no need to have found a staff first.
         */
        fun estimate(image: MonoImage): PageScale? {
            val inkRuns = IntArray(image.height + 1)
            val gapRuns = IntArray(image.height + 1)
            for (x in 0 until image.width) {
                var run = 0
                var inInk = false
                var sawInkAbove = false
                for (y in 0 until image.height) {
                    val here = image.isInk(x, y)
                    if (here == inInk) {
                        run++
                        continue
                    }
                    if (run > 0 && run <= image.height) {
                        if (inInk) {
                            inkRuns[run]++
                            sawInkAbove = true
                        } else if (sawInkAbove) {
                            // Only gaps bracketed by ink count; the blank margin above the
                            // first staff is not a staff space and would swamp the tally.
                            gapRuns[run]++
                        }
                    }
                    inInk = here
                    run = 1
                }
                if (inInk && run in 1..image.height) inkRuns[run]++
            }

            val thickness = modeOf(inkRuns) ?: return null
            val space = modeOf(gapRuns, from = thickness + 1) ?: return null
            return if (space >= 2) PageScale(thickness, space) else null
        }

        private fun modeOf(counts: IntArray, from: Int = 1): Int? {
            var best = -1
            var bestCount = 0
            for (value in from until counts.size) {
                if (counts[value] > bestCount) {
                    bestCount = counts[value]
                    best = value
                }
            }
            return if (best > 0) best else null
        }
    }
}

/**
 * One five-line staff, and the arithmetic for turning a height on the page into a pitch.
 *
 * Written pitch is a count of positions, not a distance: each line and each space is one
 * step of the scale, so half a staff space upward is always the next letter, whatever the
 * clef and whatever the key. [stepOf] answers in those positions and leaves letters to the
 * clef, which is the only thing that knows where the counting starts.
 */
data class Staff(
    /** The five line centres, top line first. */
    val lineY: List<Double>,
    val left: Int,
    val right: Int,
) {
    init {
        require(lineY.size == LINES) { "a staff has $LINES lines, got ${lineY.size}" }
    }

    val space: Double get() = (lineY.last() - lineY.first()) / (LINES - 1)
    val top: Double get() = lineY.first()
    val bottom: Double get() = lineY.last()

    /**
     * Positions above the bottom line: 0 is the bottom line, 1 the space above it, and so
     * on up, with negative numbers below the staff on ledger lines.
     */
    fun stepOf(y: Double): Int = ((bottom - y) / (space / 2.0)).roundToInt()

    /** Where a note on [step] sits, which is what draws a read note back onto the page. */
    fun yOfStep(step: Int): Double = bottom - step * (space / 2.0)

    /** True when [y] is close enough to belong to this staff rather than its neighbour. */
    fun covers(y: Double, ledgerSteps: Int = 8): Boolean {
        val reach = ledgerSteps * (space / 2.0)
        return y >= top - reach && y <= bottom + reach
    }

    companion object {
        const val LINES = 5
    }
}

/**
 * Finds the staves on a page.
 *
 * The staff lines are the only things on a page of music that run nearly its whole width,
 * so the search is for rows made mostly of long horizontal runs. Counting only the long
 * runs is what keeps a dense row of lyrics or a beam from being mistaken for a line.
 */
object StaffDetector {

    fun detect(image: MonoImage, scale: PageScale): List<Staff> {
        val minRun = (scale.space * 4).coerceAtLeast(8)
        val strength = DoubleArray(image.height)
        var peak = 0.0
        for (y in 0 until image.height) {
            val value = image.rowInkInRuns(y, minRun).toDouble()
            strength[y] = value
            if (value > peak) peak = value
        }
        if (peak <= 0.0) return emptyList()

        // Half the strongest row: a staff line broken by note heads and bar lines still
        // clears this, while a beam — wide but only a few columns long — does not.
        val floor = peak * 0.5
        val bands = mutableListOf<Band>()
        var y = 0
        while (y < image.height) {
            if (strength[y] < floor) {
                y++
                continue
            }
            var end = y
            while (end + 1 < image.height && strength[end + 1] >= floor) end++
            // A band far thicker than a line is a beam or a block of black, not a line.
            if (end - y + 1 <= scale.lineThickness * 3 + 1) {
                bands += bandOf(image, y, end, minRun, strength)
            }
            y = end + 1
        }
        return groupIntoStaves(bands, scale)
    }

    private fun bandOf(
        image: MonoImage,
        from: Int,
        to: Int,
        minRun: Int,
        strength: DoubleArray,
    ): Band {
        // Weighted centre rather than the middle of the band: a line thickened on one side
        // by the note heads sitting on it should not drag the pitch grid half a step.
        var weight = 0.0
        var moment = 0.0
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (y in from..to) {
            weight += strength[y]
            moment += strength[y] * y
            image.rowExtent(y, minRun)?.let {
                if (it.first < left) left = it.first
                if (it.last > right) right = it.last
            }
        }
        val centre = if (weight > 0) moment / weight else (from + to) / 2.0
        return Band(centre, if (left == Int.MAX_VALUE) 0 else left, if (right == Int.MIN_VALUE) 0 else right)
    }

    /**
     * Walks the lines top to bottom, taking five at a time whose spacing agrees.
     *
     * Grouping by spacing rather than by simply chopping into fives means a stray line —
     * an underline in the lyrics, the edge of a box — breaks one staff instead of shifting
     * every staff below it onto the wrong rows.
     */
    private fun groupIntoStaves(bands: List<Band>, scale: PageScale): List<Staff> {
        val staves = mutableListOf<Staff>()
        var index = 0
        while (index + Staff.LINES <= bands.size) {
            val window = bands.subList(index, index + Staff.LINES)
            val gaps = window.zipWithNext { a, b -> b.centre - a.centre }
            val mean = gaps.average()
            val even = gaps.all { abs(it - mean) <= mean * 0.35 }
            val plausible = mean >= scale.step * 0.6 && mean <= scale.step * 1.8
            if (even && plausible) {
                staves += Staff(
                    lineY = window.map { it.centre },
                    left = window.minOf { it.left },
                    right = window.maxOf { it.right },
                )
                index += Staff.LINES
            } else {
                index++
            }
        }
        return staves
    }

    private data class Band(val centre: Double, val left: Int, val right: Int)
}
