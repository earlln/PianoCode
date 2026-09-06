package com.earlln.pianocode.music.omr

/**
 * A summed-area table over the ink, so "how black is this rectangle?" costs four lookups.
 *
 * The head search asks that question at every pixel of every staff and for several
 * rectangles at each one. Counted pixel by pixel that is tens of millions of tests on a
 * scanned page; counted this way it is a handful of additions each.
 */
internal class InkSums(image: MonoImage) {
    private val width = image.width
    private val height = image.height
    private val sums = IntArray((width + 1) * (height + 1))

    init {
        for (y in 0 until height) {
            var row = 0
            for (x in 0 until width) {
                if (image.isInk(x, y)) row++
                sums[(y + 1) * (width + 1) + x + 1] = sums[y * (width + 1) + x + 1] + row
            }
        }
    }

    /** Ink pixels inside the inclusive rectangle, clipped to the page. */
    fun count(x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val left = x0.coerceIn(0, width)
        val right = (x1 + 1).coerceIn(0, width)
        val top = y0.coerceIn(0, height)
        val bottom = (y1 + 1).coerceIn(0, height)
        if (right <= left || bottom <= top) return 0
        return sums[bottom * (width + 1) + right] -
            sums[top * (width + 1) + right] -
            sums[bottom * (width + 1) + left] +
            sums[top * (width + 1) + left]
    }

    /**
     * How much of the rectangle is ink, measured against the rectangle as asked for rather
     * than as clipped — a shape running off the edge of the page really is that empty.
     */
    fun ratio(x0: Int, y0: Int, x1: Int, y1: Int): Double {
        val area = (x1 - x0 + 1).toDouble() * (y1 - y0 + 1).toDouble()
        if (area <= 0) return 0.0
        return count(x0, y0, x1, y1) / area
    }
}
