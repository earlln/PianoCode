package com.earlln.pianocode.music.omr

import kotlin.math.abs

/** The three signs that move a written letter up or down. */
enum class Accidental(val semitones: Int, val koreanName: String) {
    FLAT(-1, "플랫"),
    NATURAL(0, "제자리표"),
    SHARP(1, "샤프"),
}

/**
 * The accidentals standing at the head of a staff.
 *
 * Only the count is read, never the position of each sign, because the order is fixed —
 * sharps go F C G D A E B and flats go the same way backwards — so the number alone says
 * which letters are altered. Counting is far steadier than deciding which line each sign
 * straddles, and it cannot disagree with itself the way reading positions can.
 */
data class KeySignature(val sharps: Int = 0, val flats: Int = 0) {

    /** Semitones this signature adds to [letter], where 0 is C. */
    fun accidentalFor(letter: Int): Int = when {
        sharps > 0 -> if (SHARP_ORDER.take(sharps).contains(letter)) 1 else 0
        flats > 0 -> if (FLAT_ORDER.take(flats).contains(letter)) -1 else 0
        else -> 0
    }

    val isEmpty: Boolean get() = sharps == 0 && flats == 0

    companion object {
        /** F C G D A E B, as letters where 0 is C. */
        private val SHARP_ORDER = listOf(3, 0, 4, 1, 5, 2, 6)

        /** B E A D G C F. */
        private val FLAT_ORDER = listOf(6, 2, 5, 1, 4, 0, 3)
    }
}

/**
 * Tells one written sign from another by its shape.
 *
 * A flat is the easy one: a thin stem with a bowl hung on the bottom, so most of its ink
 * is low and its top is narrow. Between a sharp and a natural there is only width — a
 * sharp's two bars cross well past its uprights, a natural's do not — which is thin
 * evidence, but the two differ by a semitone rather than by a note, so the cost of being
 * wrong is small and the alternative is not reading either.
 */
internal object SignReader {

    fun classify(blob: Blob, space: Double): Accidental? {
        if (blob.height < space * MIN_HEIGHT || blob.height > space * MAX_HEIGHT) return null
        if (blob.width < space * MIN_WIDTH || blob.width > space * MAX_WIDTH) return null
        val flat = blob.lowerShare >= FLAT_LOWER_SHARE &&
            blob.upperWidth <= blob.width * FLAT_TOP_WIDTH
        return when {
            flat -> Accidental.FLAT
            blob.width >= space * SHARP_WIDTH -> Accidental.SHARP
            else -> Accidental.NATURAL
        }
    }

    /** True when [blob] stands at the height of a note at [y], not a line or two away. */
    fun sitsAt(blob: Blob, y: Double, space: Double): Boolean =
        abs(blob.centreY - y) <= space * 0.85

    private const val MIN_HEIGHT = 1.10
    private const val MAX_HEIGHT = 2.80
    private const val MIN_WIDTH = 0.30
    private const val MAX_WIDTH = 1.40
    private const val FLAT_LOWER_SHARE = 0.62
    private const val FLAT_TOP_WIDTH = 0.55
    private const val SHARP_WIDTH = 0.72
}

/**
 * Which clef the staff is in, decided by how far the glyph reaches.
 *
 * A treble clef is drawn taller than the staff it sits on, curling above the top line and
 * below the bottom one; a bass clef fits inside. Nothing else about the two shapes needs
 * to be understood to tell them apart, and height survives a bad photograph where the
 * curls and dots do not.
 */
object ClefReader {

    fun detect(image: MonoImage, staff: Staff): Clef {
        val space = staff.space
        val blobs = Blobs.inStrip(
            image,
            x0 = staff.left,
            x1 = (staff.left + space * 4).toInt(),
            top = (staff.top - space * 3).toInt(),
            bottom = (staff.bottom + space * 3).toInt(),
        )
        // The first thing wide enough to be a glyph. A staff often opens with a thin bar
        // line, which is not one.
        val clef = blobs.firstOrNull { it.width >= space * 0.45 } ?: return Clef.TREBLE
        return if (clef.height >= space * TALL) Clef.TREBLE else Clef.BASS
    }

    /** Where the clef ends, so the key signature can be looked for after it. */
    fun endOf(image: MonoImage, staff: Staff): Int {
        val space = staff.space
        val blobs = Blobs.inStrip(
            image,
            x0 = staff.left,
            x1 = (staff.left + space * 4).toInt(),
            top = (staff.top - space * 3).toInt(),
            bottom = (staff.bottom + space * 3).toInt(),
        )
        val clef = blobs.firstOrNull { it.width >= space * 0.45 } ?: return staff.left
        return clef.right
    }

    private const val TALL = 4.5
}

/**
 * Reads the key signature standing between the clef and the music.
 *
 * The signs of a key signature are set tight against one another, and the time signature
 * that follows is set apart from them, so the run stops at the first real gap. Without
 * that the 4 of a 4/4 would be counted as a sharp.
 */
object KeyReader {

    fun detect(image: MonoImage, staff: Staff, afterX: Int): KeySignature {
        val space = staff.space
        val blobs = Blobs.inStrip(
            image,
            x0 = (afterX + space * 0.25).toInt(),
            x1 = (afterX + space * 8).toInt(),
            top = (staff.top - space * 1.6).toInt(),
            bottom = (staff.bottom + space * 1.6).toInt(),
        )

        var sharps = 0
        var flats = 0
        var previousRight = -1
        for (blob in blobs) {
            if (previousRight >= 0 && blob.left - previousRight > space * MAX_GAP) break
            val sign = SignReader.classify(blob, space) ?: break
            when (sign) {
                Accidental.SHARP -> if (flats > 0) return KeySignature(0, flats) else sharps++
                Accidental.FLAT -> if (sharps > 0) return KeySignature(sharps, 0) else flats++
                // A natural among the opening signs is a change of key, not a key.
                Accidental.NATURAL -> return KeySignature(sharps, flats)
            }
            previousRight = blob.right
        }
        return KeySignature(sharps, flats)
    }

    private const val MAX_GAP = 1.2
}

/** Reads the sign standing immediately in front of a note head, if there is one. */
object AccidentalReader {

    fun before(image: MonoImage, staff: Staff, head: NoteHead): Accidental? {
        val space = staff.space
        val blobs = Blobs.inStrip(
            image,
            x0 = (head.x - space * 2.6).toInt(),
            x1 = (head.x - space * 0.55).toInt(),
            top = (head.y - space * 1.6).toInt(),
            bottom = (head.y + space * 1.6).toInt(),
        )
        // Nearest first: a sign belongs to the note it stands closest to.
        return blobs
            .sortedByDescending { it.right }
            .firstNotNullOfOrNull { blob ->
                if (SignReader.sitsAt(blob, head.y, space)) SignReader.classify(blob, space) else null
            }
    }
}

/**
 * Finds the bar lines, which is all that is needed to know how far an accidental reaches.
 *
 * A sign in front of a note holds until the end of the bar, so without the bars a single
 * sharp would either be forgotten immediately or held to the end of the piece.
 */
object BarLines {

    fun detect(image: MonoImage, staff: Staff, fromX: Int = staff.left): List<Int> {
        val space = staff.space
        val top = staff.top.toInt()
        val bottom = staff.bottom.toInt()
        val full = (bottom - top) * 0.85
        val bars = mutableListOf<Int>()
        // A treble clef is drawn with a full-height upright and would otherwise open every
        // piece with a bar line that is not there.
        var x = fromX.coerceAtLeast(staff.left)
        while (x <= staff.right) {
            if (!spansStaff(image, x, top, bottom, full)) {
                x++
                continue
            }
            var end = x
            while (end + 1 <= staff.right && spansStaff(image, end + 1, top, bottom, full)) end++
            // A thick run of columns is a note stem cluster or a final double bar; either
            // way its centre is the place the bar falls.
            if (end - x + 1 <= space * 0.9) bars += (x + end) / 2
            x = end + 1
        }
        return bars
    }

    private fun spansStaff(image: MonoImage, x: Int, top: Int, bottom: Int, full: Double): Boolean {
        var run = 0
        var best = 0
        for (y in top..bottom) {
            if (image.isInk(x, y)) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best >= full
    }
}
