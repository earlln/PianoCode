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

    /**
     * A sharp: two uprights with two bars crossing well past them.
     *
     * [cy] is the height the sign stands at, which is the height of the note it belongs to.
     */
    fun sharp(cx: Double, cy: Double, pitch: Double) {
        val half = pitch * 0.95
        val gap = pitch * 0.30
        vLine((cx - gap).toInt(), (cy - half).toInt(), (cy + half * 0.75).toInt(), thickness = 2)
        vLine((cx + gap).toInt(), (cy - half * 0.75).toInt(), (cy + half).toInt(), thickness = 2)
        val reach = pitch * 0.40
        hLine((cx - reach).toInt(), (cx + reach).toInt(), (cy - pitch * 0.30).toInt(), thickness = 3)
        hLine((cx - reach).toInt(), (cx + reach).toInt(), (cy + pitch * 0.24).toInt(), thickness = 3)
    }

    /** A flat: a thin upright with a bowl hung off the bottom of it. */
    fun flat(cx: Double, cy: Double, pitch: Double) {
        vLine((cx - pitch * 0.22).toInt(), (cy - pitch * 1.25).toInt(), (cy + pitch * 0.55).toInt(), thickness = 2)
        head(cx + pitch * 0.06, cy + pitch * 0.22, pitch * 0.36, pitch * 0.34, filled = true, tiltDegrees = 0.0)
    }

    /** A natural: the same two bars, but the uprights do not reach past them. */
    fun natural(cx: Double, cy: Double, pitch: Double) {
        val half = pitch * 0.95
        val gap = pitch * 0.22
        vLine((cx - gap).toInt(), (cy - half).toInt(), (cy + pitch * 0.30).toInt(), thickness = 2)
        vLine((cx + gap).toInt(), (cy - pitch * 0.30).toInt(), (cy + half).toInt(), thickness = 2)
        hLine((cx - gap).toInt(), (cx + gap).toInt(), (cy - pitch * 0.28).toInt(), thickness = 3)
        hLine((cx - gap).toInt(), (cx + gap).toInt(), (cy + pitch * 0.22).toInt(), thickness = 3)
    }

    /**
     * A treble clef, drawn only as tall as the real one is.
     *
     * The reader tells the clefs apart by height alone, so the curls are beside the point;
     * what has to be right is that this reaches above and below the staff.
     */
    fun trebleClef(cx: Double, staffTop: Double, staffBottom: Double, pitch: Double) {
        val top = staffTop - pitch * 1.2
        val bottom = staffBottom + pitch * 1.3
        vLine(cx.toInt(), top.toInt(), bottom.toInt(), thickness = 3)
        head(cx, staffBottom - pitch * 1.0, pitch * 0.85, pitch * 0.85, filled = false, tiltDegrees = 0.0)
        head(cx, top + pitch * 0.8, pitch * 0.55, pitch * 0.8, filled = false, tiltDegrees = 0.0)
    }

    /** A bass clef, which fits inside the staff and so is much the shorter of the two. */
    fun bassClef(cx: Double, staffTop: Double, pitch: Double) {
        head(cx, staffTop + pitch * 0.9, pitch * 0.9, pitch * 0.9, filled = true, tiltDegrees = 0.0)
        hLine(cx.toInt(), (cx + pitch * 0.9).toInt(), (staffTop + pitch * 0.2).toInt(), thickness = 3)
        box((cx + pitch * 1.2).toInt(), (staffTop + pitch * 0.5).toInt(), (cx + pitch * 1.5).toInt(), (staffTop + pitch * 0.8).toInt())
        box((cx + pitch * 1.2).toInt(), (staffTop + pitch * 1.3).toInt(), (cx + pitch * 1.5).toInt(), (staffTop + pitch * 1.6).toInt())
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
