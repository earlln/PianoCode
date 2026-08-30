package com.earlln.pianocode.music

/**
 * Reads written chord symbols — `C`, `F#m7`, `B♭maj9`, `Am7/G`, `Ebdim7`.
 *
 * The catalogue in [ChordQuality] doubles as the vocabulary: every symbol and alias is
 * tried longest-first, so `maj7` is never mistaken for `m` followed by junk.
 */
object ChordParser {

    /** Suffix spellings mapped to their quality, longest first so the greedy match is correct. */
    private val suffixTable: List<Pair<String, ChordQuality>> by lazy {
        ChordQuality.ALL
            .flatMap { quality -> (listOf(quality.symbol) + quality.aliases).map { it to quality } }
            .filter { it.first.isNotEmpty() }
            .sortedByDescending { it.first.length }
    }

    /** Characters OCR commonly substitutes, normalised before parsing. */
    private val characterFixes = mapOf(
        '♯' to '#', '＃' to '#', '♭' to 'b', '∆' to 'Δ', '△' to 'Δ',
        '°' to 'o', 'º' to 'o', '˚' to 'o',
        '－' to '-', '–' to '-', '—' to '-', '‐' to '-',
        '／' to '/', '\\' to '/',
    )

    /**
     * Parses [text] as a complete chord symbol. Returns null when the text is anything else —
     * a lyric, a bar number, a dynamic marking.
     */
    @JvmOverloads
    fun parse(text: String, requireUppercaseRoot: Boolean = false): Chord? {
        val cleaned = normalise(text)
        if (cleaned.isEmpty()) return null
        // Lower-case roots are legal in typed input but, on a scanned page, a stray `a` or
        // `e` in a lyric would otherwise read as a chord. The recogniser asks for strictness.
        if (requireUppercaseRoot && !cleaned.first().isUpperCase()) return null

        val slashIndex = cleaned.lastIndexOf('/')
        val bodyText: String
        val bass: Note?
        if (slashIndex > 0) {
            val bassText = cleaned.substring(slashIndex + 1)
            val parsedBass = parseNote(bassText)
            if (parsedBass != null) {
                bodyText = cleaned.substring(0, slashIndex)
                bass = parsedBass
            } else {
                bodyText = cleaned
                bass = null
            }
        } else {
            bodyText = cleaned
            bass = null
        }

        val root = readRoot(bodyText) ?: return null
        val suffix = bodyText.substring(root.second)
        val quality = readQuality(suffix) ?: return null
        return Chord(root.first, quality, bass)
    }

    /** True when [text] looks like a chord symbol and nothing else. */
    @JvmOverloads
    fun isChordSymbol(text: String, requireUppercaseRoot: Boolean = false): Boolean =
        parse(text, requireUppercaseRoot) != null

    /** Parses a bare note name, used for the bass of a slash chord. */
    fun parseNote(text: String): Note? {
        val cleaned = normalise(text)
        val root = readRoot(cleaned) ?: return null
        return if (root.second == cleaned.length) root.first else null
    }

    /**
     * Finds every chord symbol inside a line of text, keeping where each one sits.
     * A sheet's chord line is usually just symbols separated by spaces, but bar lines and
     * repeat marks get mixed in, so each whitespace-separated token is tried on its own.
     */
    @JvmOverloads
    fun findChords(line: String, requireUppercaseRoot: Boolean = false): List<ParsedChord> {
        val results = mutableListOf<ParsedChord>()
        var index = 0
        while (index < line.length) {
            if (line[index].isWhitespace()) {
                index++
                continue
            }
            var end = index
            while (end < line.length && !line[end].isWhitespace()) end++
            val token = line.substring(index, end)
            val trimmed = token.trim { it in TRIM_CHARS }
            val offset = token.indexOf(trimmed).coerceAtLeast(0)
            if (trimmed.isNotEmpty()) {
                parse(trimmed, requireUppercaseRoot)?.let {
                    results += ParsedChord(it, index + offset, index + offset + trimmed.length, trimmed)
                }
            }
            index = end
        }
        return results
    }

    private val TRIM_CHARS = setOf('|', '(', ')', '[', ']', ',', '.', ':', ';', '"', '\'', '*')

    private fun normalise(text: String): String {
        val builder = StringBuilder(text.length)
        for (ch in text.trim()) {
            when {
                ch.isWhitespace() -> Unit
                characterFixes.containsKey(ch) -> builder.append(characterFixes[ch])
                else -> builder.append(ch)
            }
        }
        return builder.toString()
    }

    /** Reads the root note off the front, returning it with the index just past it. */
    private fun readRoot(text: String): Pair<Note, Int>? {
        if (text.isEmpty()) return null
        val letter = Note.LETTER_NAMES.indexOf(text.first().uppercaseChar().toString())
        if (letter < 0) return null
        var accidental = 0
        var index = 1
        loop@ while (index < text.length) {
            when (text[index]) {
                '#' -> accidental += 1
                // A lower-case b right after the letter is a flat; anything later belongs
                // to the suffix (the `b` of `b5`, `b9`), which the quality table handles.
                'b' -> if (index == 1) accidental -= 1 else break@loop
                else -> break@loop
            }
            index++
        }
        return Note(letter, accidental) to index
    }

    /**
     * Spellings whose meaning depends on capitalisation — `M7` is a major seventh while
     * `m7` is a minor seventh — so these only ever match case-sensitively.
     */
    private val caseSensitiveSpellings: Set<String> by lazy {
        suffixTable
            .groupBy({ it.first.lowercase() }, { it.second.id })
            .filterValues { it.distinct().size > 1 }
            .keys
    }

    private fun readQuality(suffix: String): ChordQuality? {
        if (suffix.isEmpty()) return ChordQuality.MAJOR
        suffixTable.firstOrNull { it.first == suffix }?.let { return it.second }
        val lowered = suffix.lowercase()
        if (lowered in caseSensitiveSpellings) return null
        return suffixTable.firstOrNull { it.first.lowercase() == lowered }?.second
    }
}

/** A chord found inside a line of text, with the character range it occupied. */
data class ParsedChord(
    val chord: Chord,
    val start: Int,
    val end: Int,
    val rawText: String,
)
