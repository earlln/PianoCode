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

    suspend fun recognize(bitmap: Bitmap): SheetScan {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result: Text = suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val detected = mutableListOf<DetectedChord>()
        val missed = mutableListOf<MissedCandidate>()
        var index = 0

        for (block in result.textBlocks) {
            for (line in block.lines) {
                val elements = line.elements
                if (elements.isEmpty()) continue

                val words = elements.map { it.text }
                val taken = SheetTextFilter.chordIndices(words).toSet()
                val lineIsLyric = SheetTextFilter.looksLikeLyrics(words)

                elements.forEachIndexed { position, element ->
                    val bounds = element.boundingBox ?: return@forEachIndexed
                    val text = SheetTextFilter.clean(element.text)
                    if (text.isEmpty()) return@forEachIndexed

                    if (position in taken) {
                        val chord = ChordParser.parse(text, requireUppercaseRoot = true)
                            ?: return@forEachIndexed
                        detected += DetectedChord(
                            id = "chord-$index",
                            chord = chord,
                            rawText = text,
                            bounds = bounds,
                            confidence = SheetTextFilter.confidenceOf(
                                text = text,
                                lineIsLyric = lineIsLyric,
                                hasChordNeighbour = SheetTextFilter
                                    .runSupportsShortSymbol(words, position),
                            ),
                        )
                        index++
                    } else if (SheetTextFilter.looksLikeAChord(text)) {
                        // Shaped like a chord but not taken — say so instead of dropping it.
                        missed += MissedCandidate(text, bounds)
                    }
                }
            }
        }

        return SheetScan(
            chords = detected.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })),
            missed = missed.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })),
        )
    }

    fun close() = recognizer.close()
}
