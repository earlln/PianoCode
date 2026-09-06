package com.earlln.pianocode.music.omr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws a page of music so the reader can be tested against something whose answer is
 * known exactly.
 *
 * There is no scanner here and no device to run on, so the only way to know whether the
 * reader gets a pitch right is to engrave the page ourselves and ask. The shapes are crude
 * next to real engraving — an ellipse for a head, a rectangle for a stem — but they are the
 * shapes the reader actually measures, and being able to state "the head is on the third
 * line, so it must come back as B4" is worth more than fidelity.
 */
class SheetCanvas(val width: Int, val height: Int, private val paper: Int = 250) {
    private val gray = IntArray(width * height) { paper }

    fun hLine(x0: Int, x1: Int, y: Int, thickness: Int = 1, ink: Int = 20) {
        for (dy in 0 until thickness) {
            for (x in x0..x1) put(x, y + dy, ink)
        }
    }

    fun vLine(x: Int, y0: Int, y1: Int, thickness: Int = 1, ink: Int = 20) {
        for (dx in 0 until thickness) {
            for (y in y0..y1) put(x + dx, y, ink)
        }
    }

    fun box(x0: Int, y0: Int, x1: Int, y1: Int, ink: Int = 20) {
        for (y in y0..y1) for (x in x0..x1) put(x, y, ink)
    }

    /**
     * A note head: an ellipse tilted the way engravers tilt them, filled or open.
     *
     * The tilt matters — an upright ellipse and a tilted one sit differently against the
     * staff line running through them, and the reader has to find the centre either way.
     */
    fun head(
        cx: Double,
        cy: Double,
        rx: Double,
        ry: Double,
        filled: Boolean,
        tiltDegrees: Double = -20.0,
        ink: Int = 20,
        hollowCore: Double = 0.30,
    ) {
        val angle = tiltDegrees * PI / 180.0
        val ca = cos(angle)
        val sa = sin(angle)
        val reach = (maxOf(rx, ry) + 2).toInt()
        val innerScale = hollowCore
        for (y in (cy - reach).toInt()..(cy + reach).toInt()) {
            for (x in (cx - reach).toInt()..(cx + reach).toInt()) {
                val dx = x - cx
                val dy = y - cy
                val u = (dx * ca + dy * sa) / rx
                val v = (-dx * sa + dy * ca) / ry
                val r = u * u + v * v
                if (r > 1.0) continue
                if (!filled && r < innerScale) continue
                put(x, y, ink)
            }
        }
    }

    /** Staff lines for a five-line staff whose top line sits at [topY]. */
    fun staff(topY: Int, left: Int, right: Int, space: Int, thickness: Int = 1) {
        for (line in 0 until 5) hLine(left, right, topY + line * (space + thickness), thickness)
    }

    /** Dims one region, the way a phone's own shadow falls across a page. */
    fun shade(x0: Int, y0: Int, x1: Int, y1: Int, by: Int) {
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (x in 0 until width && y in 0 until height) {
                    val i = y * width + x
                    gray[i] = (gray[i] - by).coerceAtLeast(0)
                }
            }
        }
    }

    fun mono(bias: Double = 0.12): MonoImage = MonoImage.fromGray(width, height, gray, bias = bias)

    private fun put(x: Int, y: Int, ink: Int) {
        if (x in 0 until width && y in 0 until height) {
            val i = y * width + x
            if (ink < gray[i]) gray[i] = ink
        }
    }
}
