package com.earlln.pianocode.sheet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One replacement to paint: cover [bounds] and write [replacement] in its place. */
data class ChordReplacement(
    val bounds: Rect,
    val replacement: String,
)

/**
 * Paints converted chord symbols back onto the sheet photo.
 *
 * Each old symbol is covered with the paper colour sampled from around its box, then the
 * new symbol is drawn in the ink colour sampled from inside it. Matching the page's own
 * colours is what keeps the result looking like a marked-up sheet rather than a collage.
 */
object SheetRenderer {

    /** How far past its original box a longer symbol may extend before it is shrunk. */
    private const val MAX_WIDTH_GROWTH = 2.4f

    fun render(source: Bitmap, replacements: List<ChordReplacement>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        if (replacements.isEmpty()) return output

        val canvas = Canvas(output)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val sorted = replacements.sortedBy { it.bounds.left }
        sorted.forEachIndexed { index, replacement ->
            val bounds = replacement.bounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return@forEachIndexed

            val paper = samplePaperColor(source, bounds)
            val ink = sampleInkColor(source, bounds, paper)

            // Cover the old symbol, with a small bleed so anti-aliased edges disappear too.
            val bleedX = max(2, (bounds.width() * 0.06f).roundToInt())
            val bleedY = max(2, (bounds.height() * 0.12f).roundToInt())
            val erase = Rect(
                (bounds.left - bleedX).coerceAtLeast(0),
                (bounds.top - bleedY).coerceAtLeast(0),
                (bounds.right + bleedX).coerceAtMost(output.width),
                (bounds.bottom + bleedY).coerceAtMost(output.height),
            )
            fillPaint.color = paper
            canvas.drawRect(erase, fillPaint)

            // A longer symbol may spill right, but never onto the next chord or off the page.
            val nextLeft = sorted.drop(index + 1)
                .firstOrNull { it.bounds.top < bounds.bottom && it.bounds.bottom > bounds.top }
                ?.bounds?.left ?: output.width
            val available = min(
                bounds.width() * MAX_WIDTH_GROWTH,
                (min(nextLeft, output.width) - bounds.left).toFloat(),
            ).coerceAtLeast(bounds.width().toFloat())

            textPaint.color = ink
            textPaint.textSize = fittingTextSize(
                textPaint, replacement.replacement, available, bounds.height().toFloat(),
            )

            val metrics = textPaint.fontMetrics
            // Sit the new text on the same baseline the old symbol used.
            val baseline = bounds.bottom - (bounds.height() * 0.08f) - metrics.descent * 0.4f
            canvas.drawText(replacement.replacement, bounds.left.toFloat(), baseline, textPaint)
        }
        return output
    }

    /** Largest text size that fits [maxWidth] while staying near the original cap height. */
    private fun fittingTextSize(
        paint: Paint,
        text: String,
        maxWidth: Float,
        boxHeight: Float,
    ): Float {
        // ML Kit's box hugs the glyphs, so the drawn size needs a little headroom above it.
        var size = boxHeight * 1.18f
        paint.textSize = size
        val width = paint.measureText(text)
        if (width > maxWidth && width > 0f) {
            size *= maxWidth / width
        }
        return size.coerceAtLeast(8f)
    }

    /**
     * The page colour around a symbol: the brighter half of a ring of pixels just outside
     * the box, which skips any ink that leaked into the sample.
     */
    private fun samplePaperColor(bitmap: Bitmap, bounds: Rect): Int {
        val margin = max(3, (bounds.height() * 0.35f).roundToInt())
        val samples = mutableListOf<Int>()
        val top = (bounds.top - margin).coerceAtLeast(0)
        val bottom = (bounds.bottom + margin).coerceAtMost(bitmap.height - 1)
        val left = (bounds.left - margin).coerceAtLeast(0)
        val right = (bounds.right + margin).coerceAtMost(bitmap.width - 1)
        val stepX = max(1, (right - left) / 24)

        var x = left
        while (x <= right) {
            samples += bitmap.getPixel(x, top)
            samples += bitmap.getPixel(x, bottom)
            x += stepX
        }
        val stepY = max(1, (bottom - top) / 12)
        var y = top
        while (y <= bottom) {
            samples += bitmap.getPixel(left, y)
            samples += bitmap.getPixel(right, y)
            y += stepY
        }
        if (samples.isEmpty()) return Color.WHITE

        val brighter = samples.sortedByDescending { luminance(it) }
            .take(max(1, samples.size / 2))
        return averageColor(brighter)
    }

    /** The ink colour: the darkest pixels inside the box, falling back to a contrast colour. */
    private fun sampleInkColor(bitmap: Bitmap, bounds: Rect, paper: Int): Int {
        val samples = mutableListOf<Int>()
        val stepX = max(1, bounds.width() / 18)
        val stepY = max(1, bounds.height() / 10)
        var y = bounds.top.coerceAtLeast(0)
        while (y < min(bounds.bottom, bitmap.height)) {
            var x = bounds.left.coerceAtLeast(0)
            while (x < min(bounds.right, bitmap.width)) {
                samples += bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        if (samples.isEmpty()) return contrastColor(paper)

        val darkest = samples.sortedBy { luminance(it) }.take(max(1, samples.size / 6))
        val ink = averageColor(darkest)
        // If the box was mostly paper the "ink" sample is unusable; pick by contrast instead.
        return if (kotlin.math.abs(luminance(ink) - luminance(paper)) < 40) {
            contrastColor(paper)
        } else {
            ink
        }
    }

    private fun contrastColor(background: Int): Int =
        if (luminance(background) > 128) Color.rgb(20, 20, 24) else Color.rgb(245, 245, 250)

    private fun averageColor(colors: List<Int>): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        colors.forEach {
            r += Color.red(it)
            g += Color.green(it)
            b += Color.blue(it)
        }
        val size = colors.size.coerceAtLeast(1)
        return Color.rgb((r / size).toInt(), (g / size).toInt(), (b / size).toInt())
    }

    private fun luminance(color: Int): Int =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color))
            .roundToInt()
}
