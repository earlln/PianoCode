package com.earlln.pianocode.music.omr

/** A patch of ink standing on its own, found by the blank columns around it. */
internal data class Blob(
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
    val ink: Int,
    val upperInk: Int,
    val upperWidth: Int,
) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
    val centreY: Double get() = (top + bottom) / 2.0

    /** How much of the ink sits in the lower half — a flat's bowl, if it is a flat. */
    val lowerShare: Double get() = if (ink == 0) 0.0 else (ink - upperInk).toDouble() / ink
}

/**
 * Cuts a strip of the page into glyphs at its blank columns.
 *
 * Written music sets its symbols side by side with paper between them, so for the things
 * read here — a clef, the accidentals of a key signature, the sign in front of a note —
 * the blank column is a reliable boundary and costs one pass.
 */
internal object Blobs {

    fun inStrip(
        image: MonoImage,
        x0: Int,
        x1: Int,
        top: Int,
        bottom: Int,
        maxGap: Int = 1,
    ): List<Blob> {
        val yFrom = top.coerceAtLeast(0)
        val yTo = bottom.coerceAtMost(image.height - 1)
        if (yFrom > yTo) return emptyList()

        val found = mutableListOf<Blob>()
        var x = x0.coerceAtLeast(0)
        val end = x1.coerceAtMost(image.width - 1)
        while (x <= end) {
            if (!hasInk(image, x, yFrom, yTo)) {
                x++
                continue
            }
            var right = x
            var blank = 0
            var probe = x
            while (probe <= end) {
                if (hasInk(image, probe, yFrom, yTo)) {
                    right = probe
                    blank = 0
                } else {
                    blank++
                    if (blank > maxGap) break
                }
                probe++
            }
            found += measure(image, x, right, yFrom, yTo)
            x = right + 1
        }
        return found
    }

    private fun hasInk(image: MonoImage, x: Int, top: Int, bottom: Int): Boolean {
        for (y in top..bottom) if (image.isInk(x, y)) return true
        return false
    }

    private fun measure(image: MonoImage, left: Int, right: Int, top: Int, bottom: Int): Blob {
        var first = Int.MAX_VALUE
        var last = Int.MIN_VALUE
        var ink = 0
        for (y in top..bottom) {
            for (x in left..right) {
                if (image.isInk(x, y)) {
                    ink++
                    if (y < first) first = y
                    if (y > last) last = y
                }
            }
        }
        if (first > last) return Blob(left, right, top, top, 0, 0, 0)

        val middle = (first + last) / 2
        var upperInk = 0
        var upperLeft = Int.MAX_VALUE
        var upperRight = Int.MIN_VALUE
        val upperEnd = first + (last - first) / 3
        for (y in first..middle) {
            for (x in left..right) {
                if (!image.isInk(x, y)) continue
                upperInk++
                if (y <= upperEnd) {
                    if (x < upperLeft) upperLeft = x
                    if (x > upperRight) upperRight = x
                }
            }
        }
        val upperWidth = if (upperLeft > upperRight) 0 else upperRight - upperLeft + 1
        return Blob(left, right, first, last, ink, upperInk, upperWidth)
    }
}
