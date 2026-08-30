package com.earlln.pianocode.sheet

import android.graphics.Bitmap
import android.graphics.Rect
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordParser
import com.earlln.pianocode.music.SheetTextFilter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * What one pass over a page found.
 *
 * [missed] matters as much as [chords]: text that looks like a chord but was not taken is
 * text the converter will leave untouched, and a page where only some symbols moved is a
 * page in two keys at once. The screen reports these rather than shipping them quietly.
 */
data class SheetScan(
    val chords: List<DetectedChord>,
    val missed: List<MissedCandidate>,
)

/** Chord-looking text that was not converted, kept so the user can be told what was skipped. */
data class MissedCandidate(
    val text: String,
    val bounds: Rect,
)

/** A chord symbol found on the page, with the box it occupies in the source bitmap. */
data class DetectedChord(
    val id: String,
    val chord: Chord,
    val rawText: String,
    val bounds: Rect,
    val confidence: Float,
) {
    /** Rough text height, used to size the replacement so it matches the page. */
    val textHeight: Int get() = bounds.height()
}

/**
 * Reads chord symbols off a photographed lead sheet.
 *
 * ML Kit gives back text elements with bounding boxes; the work here is telling a chord
 * apart from a lyric, which [SheetTextFilter] decides for a whole recognised line at once.
 * Judging each word alone is what used to lose bare `A` and `D` when the scanner ran the
 * chord row together with the lyrics beneath it.
 */
class SheetChordRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Reads the page.
     *
     * A whole lead sheet handed to the recogniser at once puts each chord symbol at around
     * twenty pixels tall, which is where small-text recall falls off — on a real page that
     * cost roughly a quarter of the chords, and the ones it missed stayed in the original
     * key. So the page is also read in overlapping horizontal bands, each enlarged, which
     * puts the same symbols at a size the recogniser is reliable on. The two passes are
     * merged: the bands find what the full page missed, and the overlap keeps a chord that
     * straddles a seam from falling between them.
     */
    suspend fun recognize(bitmap: Bitmap): SheetScan {
        val found = mutableListOf<Candidate>()
        collectInto(found, bitmap, offsetY = 0, scale = 1f)

        for (band in bandsOf(bitmap)) {
            val slice = Bitmap.createBitmap(bitmap, 0, band.top, bitmap.width, band.height())
            val scale = bandScaleFor(bitmap)
            val enlarged = if (scale == 1f) slice else Bitmap.createScaledBitmap(
                slice,
                (slice.width * scale).toInt(),
                (slice.height * scale).toInt(),
                true,
            )
            try {
                collectInto(found, enlarged, offsetY = band.top, scale = scale)
            } finally {
                if (enlarged != slice) enlarged.recycle()
                slice.recycle()
            }
        }

        val merged = merge(found)
        var index = 0
        val detected = merged.filter { it.chord != null }.map { candidate ->
            DetectedChord(
                id = "chord-${index++}",
                chord = candidate.chord!!,
                rawText = candidate.text,
                bounds = candidate.bounds,
                confidence = candidate.confidence,
            )
        }
        val missed = merged.filter { it.chord == null }.map {
            MissedCandidate(it.text, it.bounds)
        }

        return SheetScan(
            chords = detected.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })),
            missed = missed.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })),
        )
    }

    /** One symbol seen by one pass, before the passes are reconciled. */
    private data class Candidate(
        val text: String,
        val bounds: Rect,
        val chord: Chord?,
        val confidence: Float,
    )

    /** How much to enlarge a band so its chord symbols reach a legible size. */
    private fun bandScaleFor(bitmap: Bitmap): Float =
        if (bitmap.width >= 3000) 1.5f else 2f

    /** Overlapping horizontal bands, roughly one per system, so no seam splits a symbol. */
    private fun bandsOf(bitmap: Bitmap): List<Rect> {
        val bandCount = (bitmap.height / 500).coerceIn(3, 10)
        val step = bitmap.height / bandCount
        val overlap = (step * 0.2f).toInt()
        return (0 until bandCount).map { index ->
            val top = (index * step - overlap).coerceAtLeast(0)
            val bottom = ((index + 1) * step + overlap).coerceAtMost(bitmap.height)
            Rect(0, top, bitmap.width, bottom)
        }.filter { it.height() > 0 }
    }

    private suspend fun collectInto(
        into: MutableList<Candidate>,
        bitmap: Bitmap,
        offsetY: Int,
        scale: Float,
    ) {
        val result: Text = suspendCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val elements = line.elements
                if (elements.isEmpty()) continue

                val words = elements.map { it.text }
                val taken = SheetTextFilter.chordIndices(words).toSet()
                val lineIsLyric = SheetTextFilter.looksLikeLyrics(words)

                elements.forEachIndexed { position, element ->
                    val box = element.boundingBox ?: return@forEachIndexed
                    val text = SheetTextFilter.clean(element.text)
                    if (text.isEmpty()) return@forEachIndexed

                    val isChord = position in taken
                    if (!isChord && !SheetTextFilter.looksLikeAChord(text)) return@forEachIndexed

                    into += Candidate(
                        text = text,
                        bounds = Rect(
                            (box.left / scale).toInt(),
                            (box.top / scale).toInt() + offsetY,
                            (box.right / scale).toInt(),
                            (box.bottom / scale).toInt() + offsetY,
                        ),
                        chord = if (isChord) {
                            ChordParser.parse(text, requireUppercaseRoot = true)
                        } else {
                            null
                        },
                        confidence = SheetTextFilter.confidenceOf(
                            text = text,
                            lineIsLyric = lineIsLyric,
                            hasChordNeighbour = SheetTextFilter.runSupportsShortSymbol(words, position),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Reconciles the passes.
     *
     * The same symbol is usually seen more than once, so overlapping boxes collapse into
     * one. The fuller reading wins — a band that read `C#m7` beats a full-page pass that
     * only caught `C`, and a reading that parses beats one that does not.
     */
    private fun merge(candidates: List<Candidate>): List<Candidate> {
        val kept = mutableListOf<Candidate>()
        for (candidate in candidates.sortedByDescending { it.text.length }) {
            val clash = kept.indexOfFirst { overlaps(it.bounds, candidate.bounds) }
            if (clash < 0) {
                kept += candidate
                continue
            }
            if (isBetter(candidate, kept[clash])) kept[clash] = candidate
        }
        return kept
    }

    private fun isBetter(candidate: Candidate, existing: Candidate): Boolean = when {
        (candidate.chord != null) != (existing.chord != null) -> candidate.chord != null
        candidate.text.length != existing.text.length -> candidate.text.length > existing.text.length
        else -> candidate.confidence > existing.confidence
    }

    /** True when two boxes cover mostly the same ink, so they are the same symbol. */
    private fun overlaps(a: Rect, b: Rect): Boolean {
        val width = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val height = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        if (width <= 0 || height <= 0) return false
        val shared = width.toLong() * height
        val smaller = minOf(a.width().toLong() * a.height(), b.width().toLong() * b.height())
        return smaller > 0 && shared * 100 / smaller >= 40
    }

    fun close() = recognizer.close()
}
