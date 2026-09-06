package com.earlln.pianocode.score

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** A run of words found on the page, and where it sits. */
data class PageText(
    val text: String,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
) {
    val centreY: Double get() = (top + bottom) / 2.0
}

/**
 * Reads whatever is written on the page as plain text.
 *
 * The converter's recogniser hunts for chord symbols and throws away everything that is
 * not one — which is exactly backwards here, where the words under the staff are the
 * point. This asks the same engine the plain question and keeps the answer whole.
 *
 * Lyrics are not aligned to notes, only placed where they were printed. Matching syllable
 * to note is a further guess on top of a reading that is already guessing, and printing
 * the words where the page prints them is enough to find your place, which is what they
 * are for here.
 */
class PageTextReader {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(bitmap: Bitmap): List<PageText> {
        val result: Text = suspendCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

        val found = mutableListOf<PageText>()
        for (block in result.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val text = line.text.trim()
                if (text.isEmpty()) continue
                found += PageText(text, box.left, box.right, box.top, box.bottom)
            }
        }
        return found
    }

    fun close() = recognizer.close()
}

/**
 * Decides which staff each line of words belongs under.
 *
 * Lyrics are printed below the staff they are sung to and above the next one, so the band
 * between the two settles nearly every line. What falls outside every band — a title, a
 * page number, the composer's name — belongs to no staff and is left out rather than
 * pinned to the nearest one, where it would read as a lyric that is not there.
 */
object Lyrics {

    fun assign(
        text: List<PageText>,
        staffBottoms: List<Double>,
        staffTops: List<Double>,
        space: Double,
    ): Map<Int, List<PageText>> {
        if (staffBottoms.isEmpty()) return emptyMap()
        val byStaff = mutableMapOf<Int, MutableList<PageText>>()
        for (line in text) {
            val staff = staffFor(line, staffBottoms, staffTops, space) ?: continue
            byStaff.getOrPut(staff) { mutableListOf() } += line
        }
        return byStaff.mapValues { (_, lines) -> lines.sortedBy { it.left } }
    }

    private fun staffFor(
        line: PageText,
        bottoms: List<Double>,
        tops: List<Double>,
        space: Double,
    ): Int? {
        val y = line.centreY
        for (index in bottoms.indices) {
            val from = bottoms[index] + space * 0.4
            // Stop short of the next staff so its chord symbols stay out of the lyrics.
            val nextTop = tops.getOrNull(index + 1)
            val to = if (nextTop != null) {
                minOf(bottoms[index] + space * 4.5, nextTop - space * 1.2)
            } else {
                bottoms[index] + space * 4.5
            }
            if (y in from..to) return index
        }
        return null
    }
}
