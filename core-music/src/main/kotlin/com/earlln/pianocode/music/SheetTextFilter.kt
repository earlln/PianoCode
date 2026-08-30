package com.earlln.pianocode.music

/**
 * Decides which pieces of text scanned off a page are chord symbols.
 *
 * This is the judgement half of sheet recognition, kept away from the scanner so it can be
 * tested against real pages. Everything here works on plain strings: the words of one
 * recognised line, in reading order.
 */
object SheetTextFilter {

    /** Longer than this and it is prose, not a chord symbol. */
    const val MAX_SYMBOL_LENGTH = 12

    /** Punctuation that clings to a symbol on a scan and means nothing musically. */
    val TRIM_CHARS = setOf(
        '|', '(', ')', '[', ']', '{', '}', ',', '.', ':', ';', '"', '\'', '*', '-',
        '_', '~', '!', '`', '=', '<', '>',
    )

    /**
     * A note letter, an optional accidental, then only the pieces a chord suffix is made of.
     * Words that happen to start on a note letter — "Come", "And", "Every" — fail on their
     * second character, so prose is not mistaken for a chord nobody could read.
     */
    private val CHORD_SHAPE = Regex(
        "^[A-G][#b♯♭]?(maj|min|dim|aug|sus|add|alt|M|m|o|°|ø|Δ|[0-9#b♯♭/()+-]|[A-G])*$"
    )

    /** Strips the punctuation a scan leaves on both ends of a symbol. */
    fun clean(raw: String): String = raw.trim().trim { it in TRIM_CHARS }

    /** True when [text] has the shape of a chord symbol, whether or not it parses. */
    fun looksLikeAChord(text: String): Boolean =
        text.isNotEmpty() && text.length <= MAX_SYMBOL_LENGTH && CHORD_SHAPE.matches(text)

    /** A symbol long enough that it could not be a stray letter: `E/G#`, `F#m7`, `Bm7`. */
    fun isUnmistakableChord(text: String): Boolean =
        text.length >= 2 && ChordParser.isChordSymbol(text, requireUppercaseRoot = true)

    /**
     * Whether a recognised line reads as lyrics rather than as a row of chords.
     *
     * A chord row is nearly all symbols and a lyric row nearly none, so the ratio separates
     * them well — but only when the scanner kept them apart. See [keepsShortSymbol] for what
     * happens when it did not.
     */
    fun looksLikeLyrics(words: List<String>): Boolean {
        if (words.size < 2) return false
        val chordish = words.count { ChordParser.isChordSymbol(clean(it), requireUppercaseRoot = true) }
        return chordish * 2 < words.size
    }

    /**
     * Whether a short symbol at [index] is backed up by the chords around it.
     *
     * Bare `A` and `D` are real chords on a lead sheet and ordinary letters in a sentence,
     * and the line they sit on does not settle it: a scanner regularly merges the chord row
     * with the lyric row printed under it, which makes a genuine chord row read as prose.
     * Dropping every short symbol there converts `F#m7` and `E/G#` while leaving `A` and `D`
     * behind, which puts one page into two keys at once.
     *
     * So the run decides. Look at the unbroken stretch of neighbouring words that all parse
     * as chords: a stretch holding something no lyric would contain — `E/G#`, `C#m7` — or
     * running three words or longer is a chord row, whatever the rest of the line looks like.
     * A lone `A` between two ordinary words is a stretch of one, and stays a word.
     */
    fun runSupportsShortSymbol(words: List<String>, index: Int): Boolean {
        val parses = { word: String ->
            ChordParser.parse(clean(word), requireUppercaseRoot = true) != null
        }
        if (!parses(words[index])) return false

        var start = index
        while (start > 0 && parses(words[start - 1])) start--
        var end = index
        while (end < words.lastIndex && parses(words[end + 1])) end++

        // A run of one has no company at all: on a line carrying lyrics that is the scanner
        // misreading a syllable, however chord-like the reading looks on its own.
        val run = (start..end).map { clean(words[it]) }
        return run.size >= 3 || (run.size >= 2 && run.any { it.length >= 3 })
    }

    /**
     * Picks the chord symbols out of one recognised line.
     *
     * Returns the index of every word to convert. Words that look like chords but were not
     * taken are reported separately by the caller so nothing disappears unexplained.
     */
    fun chordIndices(words: List<String>): List<Int> {
        val lineIsLyric = looksLikeLyrics(words)
        // Hangul on the line proves lyrics are mixed into it. A chord there has to be part
        // of a run of chords; a lone symbol among the words is the scanner misreading a
        // syllable, and converting it stamps a chord into the middle of the lyrics.
        val hasLyricText = words.any { word -> word.any { it.isHangul() } }
        return words.indices.filter { index ->
            val text = clean(words[index])
            when {
                text.isEmpty() || text.length > MAX_SYMBOL_LENGTH -> false
                ChordParser.parse(text, requireUppercaseRoot = true) == null -> false
                hasLyricText -> runSupportsShortSymbol(words, index)
                // Anything long enough to be unmistakable is taken as read.
                text.length >= 3 -> true
                // Short symbols on a plain chord row are fine; on a row the scanner mixed
                // with lyrics they need the company of other chords to earn their place.
                !lineIsLyric -> true
                else -> runSupportsShortSymbol(words, index)
            }
        }
    }

    /** Hangul syllables and jamo — the alphabet the lyrics on these sheets are set in. */
    private fun Char.isHangul(): Boolean =
        this in '\uAC00'..'\uD7A3' || this in '\u1100'..'\u11FF' || this in '\u3130'..'\u318F'

    /** How much to trust a symbol, shown next to the low-confidence ones for review. */
    fun confidenceOf(text: String, lineIsLyric: Boolean, hasChordNeighbour: Boolean): Float {
        var score = 0.6f
        // A suffix (m7, maj9, sus4) is strong evidence; a bare letter is weak on its own.
        if (text.length >= 2) score += 0.2f
        if (text.length >= 3) score += 0.1f
        if (text.any { it.isDigit() || it == '#' || it == 'b' }) score += 0.1f
        if (hasChordNeighbour) score += 0.15f
        if (lineIsLyric) score -= 0.35f
        return score.coerceIn(0f, 1f)
    }
}
