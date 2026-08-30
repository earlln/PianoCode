package com.earlln.pianocode.sheet

import android.graphics.Bitmap
import android.graphics.Rect
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
 * apart from a lyric. Chord symbols are short, start with an upper-case letter A–G, and
 * sit alone in their box, so those three rules filter out almost all prose while keeping
 * the symbols intact.
 */
class SheetChordRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): List<DetectedChord> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result: Text = suspendCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val detected = mutableListOf<DetectedChord>()
        var index = 0
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val lineIsLyric = looksLikeLyrics(line.text)
                for (element in line.elements) {
                    val bounds = element.boundingBox ?: continue
                    val text = element.text.trim()
                    if (text.isEmpty()) continue

                    val cleaned = text.trim { it in TRIM_CHARS }
                    if (cleaned.isEmpty() || cleaned.length > MAX_SYMBOL_LENGTH) continue

                    val chord = ChordParser.parse(cleaned, requireUppercaseRoot = true) ?: continue

                    // A single letter inside a sentence is almost always a word, not a chord.
                    if (lineIsLyric && cleaned.length <= 2) continue

                    detected += DetectedChord(
                        id = "chord-$index",
                        chord = chord,
                        rawText = cleaned,
                        bounds = bounds,
                        confidence = confidenceOf(cleaned, lineIsLyric),
                    )
                    index++
                }
            }
        }
        return detected.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    fun close() = recognizer.close()

    private companion object {
        const val MAX_SYMBOL_LENGTH = 12
        val TRIM_CHARS = setOf('|', '(', ')', '[', ']', ',', '.', ':', ';', '"', '\'', '*', '-')

        /**
         * A line is treated as lyrics when most of its words are not chord symbols — the
         * chord line of a lead sheet is nearly all symbols, a lyric line is nearly none.
         */
        fun looksLikeLyrics(lineText: String): Boolean {
            val words = lineText.split(' ', '\t').filter { it.isNotBlank() }
            if (words.size < 2) return false
            val chordish = words.count {
                ChordParser.isChordSymbol(it.trim { ch -> ch in TRIM_CHARS }, requireUppercaseRoot = true)
            }
            return chordish * 2 < words.size
        }

        fun confidenceOf(text: String, lineIsLyric: Boolean): Float {
            var score = 0.6f
            // A suffix (m7, maj9, sus4) is strong evidence; a bare letter is weak evidence.
            if (text.length >= 2) score += 0.2f
            if (text.length >= 3) score += 0.1f
            if (text.any { it.isDigit() || it == '#' || it == 'b' }) score += 0.1f
            if (lineIsLyric) score -= 0.35f
            return score.coerceIn(0f, 1f)
        }
    }
}
