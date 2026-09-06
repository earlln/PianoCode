package com.earlln.pianocode.music.omr

/**
 * A page reduced to two colours: ink and paper.
 *
 * Everything downstream — staff lines, note heads, stems — is a question about which pixels
 * are ink, so the whole pipeline works on this one flat structure rather than on the
 * platform's bitmap type. That keeps the reader pure Kotlin and testable against pictures
 * drawn by hand in a test, which is the only way to know the reading is right without a
 * scanner and a device.
 */
class MonoImage(
    val width: Int,
    val height: Int,
    private val ink: BooleanArray,
) {
    init {
        require(width > 0 && height > 0) { "an image needs a width and a height" }
        require(ink.size == width * height) { "expected ${width * height} pixels, got ${ink.size}" }
    }

    fun isInk(x: Int, y: Int): Boolean =
        x >= 0 && y >= 0 && x < width && y < height && ink[y * width + x]

    /** A copy that can be written to, for the passes that erase what they have consumed. */
    fun mutableCopy(): MutableMonoImage = MutableMonoImage(width, height, ink.copyOf())

    /** Ink pixels on one row. */
    fun rowInk(y: Int): Int {
        if (y < 0 || y >= height) return 0
        var count = 0
        val base = y * width
        for (x in 0 until width) if (ink[base + x]) count++
        return count
    }

    /**
     * Ink on one row that belongs to a horizontal run at least [minRun] wide.
     *
     * Staff lines are the widest horizontal thing on a page by a long way, so measuring a
     * row this way separates them from lyrics, chord symbols and note heads, all of which
     * are short in x. Without it a row of text can out-vote a staff line on a busy page.
     */
    fun rowInkInRuns(y: Int, minRun: Int): Int {
        if (y < 0 || y >= height || minRun <= 0) return 0
        val base = y * width
        var total = 0
        var run = 0
        for (x in 0 until width) {
            if (ink[base + x]) {
                run++
            } else {
                if (run >= minRun) total += run
                run = 0
            }
        }
        if (run >= minRun) total += run
        return total
    }

    /** The first and last column carrying ink on [y], or null when the row is blank. */
    fun rowExtent(y: Int, minRun: Int = 1): IntRange? {
        if (y < 0 || y >= height) return null
        val base = y * width
        var first = -1
        var last = -1
        var run = 0
        for (x in 0 until width) {
            if (ink[base + x]) {
                run++
                if (run >= minRun) {
                    if (first < 0) first = x - run + 1
                    last = x
                }
            } else {
                run = 0
            }
        }
        return if (first < 0) null else first..last
    }

    companion object {
        /**
         * Thresholds a greyscale page into ink and paper, judging each pixel against its own
         * neighbourhood rather than against one number for the whole page.
         *
         * A photograph of a sheet is never evenly lit — a shadow from the phone, a page that
         * curves away near the spine — and one global threshold turns the dark half solid
         * black and the bright half blank. Comparing against a local mean survives all of
         * that, and costs one integral image.
         *
         * [bias] is how far below its surroundings a pixel must sit to count as ink; 0.12
         * keeps thin staff lines while ignoring the grey of the paper itself.
         */
        fun fromGray(
            width: Int,
            height: Int,
            gray: IntArray,
            window: Int = 0,
            bias: Double = 0.12,
        ): MonoImage {
            require(gray.size == width * height) { "expected ${width * height} samples" }
            val radius = ((if (window > 0) window else maxOf(15, minOf(width, height) / 40)) / 2)
                .coerceAtLeast(1)

            // Summed-area table, one row and column of zeroes at the top and left so every
            // window can be read as four lookups without bounds tests in the inner loop.
            val sums = LongArray((width + 1) * (height + 1))
            for (y in 0 until height) {
                var rowSum = 0L
                for (x in 0 until width) {
                    rowSum += gray[y * width + x]
                    sums[(y + 1) * (width + 1) + x + 1] = sums[y * (width + 1) + x + 1] + rowSum
                }
            }

            val ink = BooleanArray(width * height)
            for (y in 0 until height) {
                val top = (y - radius).coerceAtLeast(0)
                val bottom = (y + radius).coerceAtMost(height - 1)
                for (x in 0 until width) {
                    val left = (x - radius).coerceAtLeast(0)
                    val right = (x + radius).coerceAtMost(width - 1)
                    val area = (right - left + 1).toLong() * (bottom - top + 1).toLong()
                    val total = sums[(bottom + 1) * (width + 1) + right + 1] -
                        sums[top * (width + 1) + right + 1] -
                        sums[(bottom + 1) * (width + 1) + left] +
                        sums[top * (width + 1) + left]
                    val mean = total.toDouble() / area
                    ink[y * width + x] = gray[y * width + x] < mean * (1.0 - bias)
                }
            }
            return MonoImage(width, height, ink)
        }
    }
}

/** A [MonoImage] whose pixels can be cleared, for passes that erase what they have read. */
class MutableMonoImage(
    val width: Int,
    val height: Int,
    private val ink: BooleanArray,
) {
    fun isInk(x: Int, y: Int): Boolean =
        x >= 0 && y >= 0 && x < width && y < height && ink[y * width + x]

    fun erase(x: Int, y: Int) {
        if (x in 0 until width && y in 0 until height) ink[y * width + x] = false
    }

    fun frozen(): MonoImage = MonoImage(width, height, ink.copyOf())
}
