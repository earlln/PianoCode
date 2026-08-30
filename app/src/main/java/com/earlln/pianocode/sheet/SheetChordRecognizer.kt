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
        collectInto(found, bitmap, offset = Rect(0, 0, bitmap.width, bitmap.height), scale = 1f)

        for (tile in tilesOf(bitmap)) {
            val slice = Bitmap.createBitmap(bitmap, tile.left, tile.top, tile.width(), tile.height())
            val scale = scaleFor(tile)
            val enlarged = if (scale == 1f) slice else Bitmap.createScaledBitmap(
                slice,
                (slice.width * scale).toInt(),
                (slice.height * scale).toInt(),
                true,
            )
            try {
                collectInto(found, enlarged, offset = tile, scale = scale)
            } finally {
                if (enlarged != slice) enlarged.recycle()
                slice.recycle()
            }
        }

        val merged = dropOddSizes(merge(found))
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

    /** Enlarges a tile towards the size the recogniser reads small print most reliably at. */
    private fun scaleFor(tile: Rect): Float =
        (TILE_TARGET_WIDTH.toFloat() / tile.width()).coerceIn(1f, 3f)

    /**
     * The page cut into overlapping tiles: a band per system, each split left and right.
     *
     * A full-width band enlarged to fit the recogniser's window leaves each chord symbol
     * only a handful of pixels; halving the width doubles what every glyph gets. Tiles
     * overlap on both axes so a symbol sitting on a seam is whole in the neighbouring tile.
     */
    private fun tilesOf(bitmap: Bitmap): List<Rect> {
        val bandCount = (bitmap.height / 420).coerceIn(3, 12)
        val step = bitmap.height / bandCount
        val overlapY = (step * 0.2f).toInt()
        val halfWidth = bitmap.width / 2
        val overlapX = (halfWidth * 0.12f).toInt()

        return (0 until bandCount).flatMap { index ->
            val top = (index * step - overlapY).coerceAtLeast(0)
            val bottom = ((index + 1) * step + overlapY).coerceAtMost(bitmap.height)
            listOf(
                Rect(0, top, (halfWidth + overlapX).coerceAtMost(bitmap.width), bottom),
                Rect((halfWidth - overlapX).coerceAtLeast(0), top, bitmap.width, bottom),
            )
        }.filter { it.width() > 0 && it.height() > 0 }
    }

    private suspend fun collectInto(
        into: MutableList<Candidate>,
        bitmap: Bitmap,
        offset: Rect,
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
                            (box.left / scale).toInt() + offset.left,
                            (box.top / scale).toInt() + offset.top,
                            (box.right / scale).toInt() + offset.left,
                            (box.bottom / scale).toInt() + offset.top,
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

    /**
     * Drops readings whose box is the wrong size for a chord symbol.
     *
     * Every chord on a printed page is set in the same size, so the median height of what
     * was found describes them all. A reading far off that median is not a chord: it is a
     * note head, a slur or a stretch of staff that happened to resolve into a letter. Left
     * in, one of those is converted and written back at the size of its own box, stamping a
     * huge letter across the music.
     */
    private fun dropOddSizes(candidates: List<Candidate>): List<Candidate> {
        val chords = candidates.filter { it.chord != null }
        if (chords.size < MIN_FOR_SIZE_CHECK) return candidates

        val median = chords.map { it.bounds.height() }.sorted()[chords.size / 2]
        if (median <= 0) return candidates

        return candidates.map { candidate ->
            val height = candidate.bounds.height()
            val plausible = height >= median * MIN_HEIGHT_RATIO &&
                height <= median * MAX_HEIGHT_RATIO
            // Kept as a missed candidate rather than dropped, so an over-eager filter still
            // shows up in the count of what stayed in the original key.
            if (plausible) candidate else candidate.copy(chord = null)
        }
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

    private companion object {
        /** Width each tile is enlarged towards before it is read. */
        const val TILE_TARGET_WIDTH = 2200

        /** Below this many readings the median says too little to filter on. */
        const val MIN_FOR_SIZE_CHECK = 5

        /** How far a chord's height may sit from the page's median and still be one. */
        const val MIN_HEIGHT_RATIO = 0.55f
        const val MAX_HEIGHT_RATIO = 1.8f
    }
}
