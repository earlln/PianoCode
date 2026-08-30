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

    /** Room above a glyph-hugging box so the redrawn symbol matches the printed one. */
    private const val TEXT_HEADROOM = 1.18f

    /** How far above the page's median a symbol may be drawn, whatever its box says. */
    private const val MAX_HEIGHT_RATIO = 1.6f

    /** Default marking colour, used until a page has been looked at. */
    val CONVERTED_INK: Int get() = MarkingColor.VIOLET.argb

    /**
     * Picks a colour for the converted symbols that the page is not already written in.
     *
     * Sheets are usually black on white, where violet stands out plainly — but a sheet
     * printed or annotated in blue or purple would swallow it, and the marking would say
     * nothing. So the page's own ink is measured first and the most distant readable colour
     * wins, subject to still being dark enough against the paper to read.
     */
    fun pickMarkingColor(source: Bitmap, boxes: List<Rect>): MarkingColor {
        val samples = boxes.take(12).filter { it.width() > 0 && it.height() > 0 }
        if (samples.isEmpty()) return MarkingColor.VIOLET

        val paper = averageColor(samples.map { samplePaperColor(source, it) })
        val ink = averageColor(samples.map { sampleInkColor(source, it, paper) })

        val readable = MarkingColor.entries.filter {
            kotlin.math.abs(luminance(it.argb) - luminance(paper)) >= 90
        }
        val choices = readable.ifEmpty { MarkingColor.entries.toList() }
        return choices.maxByOrNull { distance(it.argb, ink) } ?: MarkingColor.VIOLET
    }

    /** How far apart two colours look, weighted the way the eye weighs the channels. */
    private fun distance(a: Int, b: Int): Double {
        val dr = (Color.red(a) - Color.red(b)) * 0.30
        val dg = (Color.green(a) - Color.green(b)) * 0.59
        val db = (Color.blue(a) - Color.blue(b)) * 0.11
        return dr * dr + dg * dg + db * db
    }

    /**
     * Paints [replacements] onto a copy of [source].
     *
     * [highlightInk] writes every converted symbol in one colour so it stands out from the
     * symbols still in the original key; passing null matches the page's own ink instead,
     * for a clean print.
     */
    fun render(
        source: Bitmap,
        replacements: List<ChordReplacement>,
        banner: String? = null,
        highlightInk: Int? = CONVERTED_INK,
    ): Bitmap {
        val bannerHeight = if (banner == null) 0 else bannerHeightFor(source)
        val output = Bitmap.createBitmap(
            source.width,
            source.height + bannerHeight,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, bannerHeight.toFloat(), null)

        if (banner != null) drawBanner(canvas, source.width, bannerHeight, banner)
        if (replacements.isEmpty()) return output

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        val sorted = replacements
            .filter { it.bounds.width() > 0 && it.bounds.height() > 0 }
            .sortedBy { it.bounds.left }

        // Chord symbols are all one size on a page, so the median describes them. Capping
        // against it keeps a single bad box from stamping a huge letter over the music,
        // however it got through.
        val sizeCap = sorted.map { it.bounds.height() }.sorted()
            .takeIf { it.isNotEmpty() }
            ?.let { it[it.size / 2].toFloat() * MAX_HEIGHT_RATIO * TEXT_HEADROOM }
            ?: Float.MAX_VALUE

        // Colours are sampled from the untouched source, and every box is covered before
        // any text is drawn. Doing it in one pass let a later chord's cover rectangle clip
        // the tail of the symbol drawn just before it, which is how a long replacement such
        // as C#m7 -> Em7 could come out half-erased.
        val plans = sorted.map { replacement ->
            val bounds = replacement.bounds
            val paper = samplePaperColor(source, bounds)
            RenderPlan(
                replacement = replacement,
                paper = paper,
                ink = highlightInk ?: sampleInkColor(source, bounds, paper),
            )
        }

        for (plan in plans) {
            val bounds = plan.replacement.bounds
            val bleedX = max(2, (bounds.width() * 0.06f).roundToInt())
            val bleedY = max(2, (bounds.height() * 0.12f).roundToInt())
            fillPaint.color = plan.paper
            canvas.drawRect(
                (bounds.left - bleedX).coerceAtLeast(0).toFloat(),
                (bounds.top - bleedY).coerceAtLeast(0).toFloat() + bannerHeight,
                (bounds.right + bleedX).coerceAtMost(source.width).toFloat(),
                (bounds.bottom + bleedY).coerceAtMost(source.height).toFloat() + bannerHeight,
                fillPaint,
            )
        }

        plans.forEachIndexed { index, plan ->
            val bounds = plan.replacement.bounds

            // A longer symbol may spill right, but never onto the next chord or off the page.
            val nextLeft = plans.drop(index + 1)
                .firstOrNull {
                    it.replacement.bounds.top < bounds.bottom &&
                        it.replacement.bounds.bottom > bounds.top
                }
                ?.replacement?.bounds?.left ?: source.width
            val available = min(
                bounds.width() * MAX_WIDTH_GROWTH,
                (min(nextLeft, source.width) - bounds.left).toFloat(),
            ).coerceAtLeast(bounds.width().toFloat())

            textPaint.color = plan.ink
            textPaint.textSize = fittingTextSize(
                paint = textPaint,
                text = plan.replacement.replacement,
                maxWidth = available,
                boxHeight = bounds.height().toFloat(),
                sizeCap = sizeCap,
            )

            val metrics = textPaint.fontMetrics
            // Sit the new text on the same baseline the old symbol used.
            val baseline = bounds.bottom - (bounds.height() * 0.08f) - metrics.descent * 0.4f
            canvas.drawText(
                plan.replacement.replacement,
                bounds.left.toFloat(),
                baseline + bannerHeight,
                textPaint,
            )
        }
        return output
    }

    /** A replacement with the colours sampled for it, worked out before anything is painted. */
    private data class RenderPlan(
        val replacement: ChordReplacement,
        val paper: Int,
        val ink: Int,
    )

    /** Height of the band drawn above the page, needed to map a tap back onto the source. */
    fun bannerHeightFor(source: Bitmap): Int =
        (source.width * 0.045f).roundToInt().coerceIn(30, 96)

    /**
     * Writes what happened across the top of the page.
     *
     * The staff itself still carries the original key signature and melody — only the chord
     * symbols moved — so the sheet says so in its own margin rather than leaving a musician
     * to work out why the notes and the chords disagree.
     */
    private fun drawBanner(canvas: Canvas, width: Int, height: Int, text: String) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(27, 23, 37) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            textSize = height * 0.44f
        }
        val padding = height * 0.28f
        val maxWidth = width - padding * 2
        val measured = paint.measureText(text)
        if (measured > maxWidth) paint.textSize *= maxWidth / measured
        val metrics = paint.fontMetrics
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(text, padding, baseline, paint)
    }

    /** Largest text size that fits [maxWidth] while staying near the original cap height. */
    private fun fittingTextSize(
        paint: Paint,
        text: String,
        maxWidth: Float,
        boxHeight: Float,
        sizeCap: Float,
    ): Float {
        // The recogniser's box hugs the glyphs, so the drawn size needs a little headroom.
        var size = (boxHeight * TEXT_HEADROOM).coerceAtMost(sizeCap)
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
