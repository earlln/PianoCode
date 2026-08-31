package com.earlln.pianocode.sheet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * What the user taught the app about one page.
 *
 * Recognition is deterministic, so reading the same photo twice makes the same mistakes
 * twice. Without this, correcting a page is work that has to be redone every time it is
 * opened — and a page is opened again precisely because the first pass was not finished.
 */
data class SheetNotes(
    /** Spots said not to be chords at all, whether flagged or read as one. */
    val dismissed: List<Rect> = emptyList(),
    /** Spots whose reading was corrected, and what the page actually says there. */
    val corrections: List<PlacedChord> = emptyList(),
    /** Chords placed by hand where the reader saw nothing. */
    val additions: List<PlacedChord> = emptyList(),
    /** Readings switched off, kept on the page in the original key. */
    val disabled: List<Rect> = emptyList(),
) {
    val isEmpty: Boolean
        get() = dismissed.isEmpty() && corrections.isEmpty() &&
            additions.isEmpty() && disabled.isEmpty()
}

data class PlacedChord(val bounds: Rect, val symbol: String)

/**
 * Remembers corrections against the page they were made on.
 *
 * A page is identified by what it looks like rather than where it came from: the same sheet
 * reaches the app as a gallery uri one time and a camera capture the next, and neither the
 * uri nor the file name survives that. A coarse greyscale thumbprint does, and it also
 * survives the re-compression a photo picks up being sent through a messenger.
 */
object SheetMemoryStore {

    private const val PREFS = "sheet_memory"
    private const val KEY_PAGES = "pages"
    /** Pages remembered before the oldest is dropped. */
    private const val CAPACITY = 24
    /** Side of the thumbprint grid; 32x32 is coarse enough to ignore compression noise. */
    private const val PRINT_SIDE = 32
    /** Greyscale levels kept per cell, so a slightly darker scan still matches. */
    private const val PRINT_LEVELS = 16

    /**
     * A stable name for this page.
     *
     * Shrinking to a small grid throws away everything a re-encode changes and keeps the
     * layout of ink on paper, which is what makes one sheet different from another.
     */
    fun fingerprintOf(bitmap: Bitmap): String {
        val small = Bitmap.createScaledBitmap(bitmap, PRINT_SIDE, PRINT_SIDE, true)
        val pixels = IntArray(PRINT_SIDE * PRINT_SIDE)
        small.getPixels(pixels, 0, PRINT_SIDE, 0, 0, PRINT_SIDE, PRINT_SIDE)
        if (small != bitmap) small.recycle()

        val cells = ByteArray(pixels.size)
        pixels.forEachIndexed { index, pixel ->
            val grey = (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000
            cells[index] = (grey * PRINT_LEVELS / 256).toByte()
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(cells)
            .joinToString("") { "%02x".format(it) }
            .take(32)
    }

    fun load(context: Context, fingerprint: String): SheetNotes {
        val pages = readPages(context)
        for (index in 0 until pages.length()) {
            val page = pages.optJSONObject(index) ?: continue
            if (page.optString("id") == fingerprint) return page.toNotes()
        }
        return SheetNotes()
    }

    /** Stores what is known about this page, moving it to the front as most recently used. */
    fun save(context: Context, fingerprint: String, notes: SheetNotes) {
        val pages = readPages(context)
        val kept = JSONArray()
        if (!notes.isEmpty) {
            kept.put(notes.toJson(fingerprint))
        }
        for (index in 0 until pages.length()) {
            if (kept.length() >= CAPACITY) break
            val page = pages.optJSONObject(index) ?: continue
            if (page.optString("id") == fingerprint) continue
            kept.put(page)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_PAGES, kept.toString())
        }
    }

    fun forget(context: Context, fingerprint: String) =
        save(context, fingerprint, SheetNotes())

    private fun readPages(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PAGES, null) ?: return JSONArray()
        return try {
            JSONArray(raw)
        } catch (error: Exception) {
            JSONArray()
        }
    }

    // --- shapes on the wire -------------------------------------------------

    private fun SheetNotes.toJson(fingerprint: String) = JSONObject().apply {
        put("id", fingerprint)
        put("at", System.currentTimeMillis())
        put("dismissed", dismissed.toRectArray())
        put("disabled", disabled.toRectArray())
        put("corrections", corrections.toChordArray())
        put("additions", additions.toChordArray())
    }

    private fun JSONObject.toNotes() = SheetNotes(
        dismissed = optJSONArray("dismissed").toRects(),
        disabled = optJSONArray("disabled").toRects(),
        corrections = optJSONArray("corrections").toPlacedChords(),
        additions = optJSONArray("additions").toPlacedChords(),
    )

    private fun List<Rect>.toRectArray() = JSONArray().also { array ->
        forEach { array.put(it.toJson()) }
    }

    private fun List<PlacedChord>.toChordArray() = JSONArray().also { array ->
        forEach { placed ->
            array.put(placed.bounds.toJson().put("symbol", placed.symbol))
        }
    }

    private fun Rect.toJson() = JSONObject()
        .put("l", left).put("t", top).put("r", right).put("b", bottom)

    private fun JSONObject.toRect() =
        Rect(optInt("l"), optInt("t"), optInt("r"), optInt("b"))

    private fun JSONArray?.toRects(): List<Rect> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toRect() }
    }

    private fun JSONArray?.toPlacedChords(): List<PlacedChord> {
        val array = this ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val symbol = item.optString("symbol").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PlacedChord(item.toRect(), symbol)
        }
    }
}
