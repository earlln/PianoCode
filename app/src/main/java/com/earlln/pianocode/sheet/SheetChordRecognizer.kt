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

/**
 * What one pass over a page found.
 *
 * [missed] matters as much as [chords]: text that looks like a chord but could not be read
 * is text the converter will leave untouched, and a page where only some symbols moved is
 * a page in two keys at once. The UI warns about these rather than quietly shipping them.
 */
data class SheetScan(
    val chords: List<DetectedChord>,
    val missed: List<MissedCandidate>,
)

/** Chord-looking text the parser refused, kept so the user can be told what was skipped. */
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
 * apart from a lyric. Chord symbols are short, start with an upper-case letter A–G, and
 * sit alone in their box, so those three rules filter out almost all prose while keeping
 * the symbols intact.
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
                val lineIsLyric = looksLikeLyrics(line.text)
                for (element in line.elements) {
                    val bounds = element.boundingBox ?: continue
                    val text = element.text.trim()
                    if (text.isEmpty()) continue

                    val cleaned = text.trim { it in TRIM_CHARS }
                    if (cleaned.isEmpty() || cleaned.length > MAX_SYMBOL_LENGTH) continue

                    val chord = ChordParser.parse(cleaned, requireUppercaseRoot = true)
                    if (chord == null) {
                        if (looksLikeAChord(cleaned) && !lineIsLyric) {
                            missed += MissedCandidate(cleaned, bounds)
                        }
                        continue
                    }

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
        return SheetScan(
            chords = detected.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })),
            missed = missed.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })),
        )
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

        /**
         * Whether unparsed text still has the shape of a chord symbol: it starts on a note
         * letter and is short. `Bb7sus`, `Amaj`, `F#m11` and OCR debris like `Cm7|` all
         * qualify, so the user hears about them instead of finding them on the output.
         */
        fun looksLikeAChord(text: String): Boolean =
            text.length <= MAX_SYMBOL_LENGTH && CHORD_SHAPE.matches(text)

        /**
         * A note letter, an optional accidental, then only the pieces a chord suffix is made
         * of. Words that happen to start on a note letter — "Come", "And", "Every" — fail on
         * their second character, so prose is not reported as a missed chord.
         */
        val CHORD_SHAPE = Regex(
            "^[A-G][#b♯♭]?" +
                "(maj|min|dim|aug|sus|add|alt|M|m|o|°|ø|Δ|[0-9#b♯♭/()+-]|[A-G])*$"
        )

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
