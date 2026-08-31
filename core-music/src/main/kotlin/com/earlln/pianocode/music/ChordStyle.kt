package com.earlln.pianocode.music

/**
 * Writes a converted chord in the hand the sheet already uses.
 *
 * A page that says `G(add2)` should come back saying `C(add2)`, not `Cadd9`: they are the
 * same chord, but the player is reading a page they know, and a symbol rewritten into the
 * catalogue's preferred spelling reads as a different chord at a glance. The same goes for
 * `CΔ7` against `Cmaj7`, or `C-7` against `Cm7` — the quality is ours to work out, the way
 * of writing it is the sheet's.
 */
object ChordStyle {

    /**
     * Spells [converted] the way [printed] spelled [original].
     *
     * Falls back to the catalogue's own symbol whenever the printed text cannot be trusted
     * to describe [original] — it was misread and corrected by hand, or it does not parse
     * at all — because a suffix lifted from the wrong chord would be worse than a plain one.
     */
    fun restyle(printed: String, original: Chord, converted: Chord): String {
        val spelling = ChordParser.spellingOf(printed) ?: return converted.symbol
        if (ChordParser.parse(printed) != original) return converted.symbol

        val suffix = closeBrackets(spelling.suffixText)
        val bass = converted.bass?.let { "/${it.name}" } ?: ""
        return converted.root.name + suffix + bass
    }

    /**
     * Repairs a bracket the scan lost.
     *
     * Printed brackets are thin and one side often does not survive, so `G(add2` is read
     * whole and would otherwise be answered with `C(add2` — an opening bracket the new page
     * never closes. The reading was right; only the drawing of it was incomplete.
     */
    private fun closeBrackets(suffix: String): String {
        val open = suffix.count { it == '(' }
        val close = suffix.count { it == ')' }
        return when {
            open > close -> suffix + ")".repeat(open - close)
            close > open -> "(".repeat(close - open) + suffix
            else -> suffix
        }
    }
}
