package com.earlln.pianocode.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the case that put one page in two keys: a scanner merging a chord row with the
 * lyric row printed underneath it, on 손경민 "충만".
 *
 * Every chord on that row is at least two characters except `A` and `D`. Judging the whole
 * merged line as prose dropped exactly those two and kept the rest, so `F#m7` moved and `A`
 * stayed — the symptom reported from the built app.
 */
class SheetTextFilterTest {

    /** Bars 6-8: the chord row on its own, as a clean scan would return it. */
    private val chordRow = listOf("A", "E/G#", "F#m7", "C#m7", "D", "A/C#")

    /** The same row after the scanner ran it together with the lyric line beneath. */
    private val mergedRow = chordRow + listOf(
        "무", "명이", "어", "도", "공", "허", "하지", "않", "은", "것은", "예", "수", "안에", "난",
    )

    private fun taken(words: List<String>) =
        SheetTextFilter.chordIndices(words).map { SheetTextFilter.clean(words[it]) }

    @Test
    fun `a clean chord row keeps every symbol`() {
        assertFalse(SheetTextFilter.looksLikeLyrics(chordRow))
        assertEquals(chordRow, taken(chordRow))
    }

    @Test
    fun `a chord row merged with lyrics still keeps its one-letter chords`() {
        // The ratio says prose, because the lyrics outnumber the chords.
        assertTrue(SheetTextFilter.looksLikeLyrics(mergedRow))
        // The chords survive anyway — including A and D, which is the whole point.
        assertEquals(chordRow, taken(mergedRow))
    }

    @Test
    fun `lyrics alone yield no chords`() {
        val lyrics = listOf("무", "명이", "어", "도", "공", "허", "하지", "않", "은", "것은")
        assertEquals(emptyList<String>(), taken(lyrics))
    }

    @Test
    fun `a stray letter in English prose is not taken as a chord`() {
        val prose = listOf("Sing", "A", "song", "of", "praise", "and", "be", "glad", "today")
        assertTrue(SheetTextFilter.looksLikeLyrics(prose))
        assertEquals(emptyList<String>(), taken(prose))
    }

    @Test
    fun `a lone letter is kept when it runs with real chords`() {
        assertTrue(SheetTextFilter.runSupportsShortSymbol(listOf("A", "E/G#"), 0))
        assertTrue(SheetTextFilter.runSupportsShortSymbol(listOf("Bm7", "E7", "A"), 2))
        // Three chords in a row is a chord row even with nothing long in it.
        assertTrue(SheetTextFilter.runSupportsShortSymbol(listOf("A7", "D7", "G7", "C7"), 0))
        // A single letter between two ordinary words is a word.
        assertFalse(SheetTextFilter.runSupportsShortSymbol(listOf("Sing", "A", "song"), 1))
    }

    @Test
    fun `an English word that happens to parse is not taken on its own`() {
        // "Go" reads as G diminished, so it needs the company of other chords too.
        val prose = listOf("Let", "Go", "And", "Sing", "Now")
        assertEquals(emptyList<String>(), taken(prose))
    }

    @Test
    fun `every chord row of the sheet survives being merged with its lyrics`() {
        val lyricWords = listOf("예", "수로", "충", "만", "하네", "난", "세", "상", "모든", "것", "들도")
        val rows = listOf(
            listOf("A", "E/G#", "F#m7", "C#m7", "D", "A/C#"),
            listOf("Bm7", "E7", "A", "E/G#", "F#m7", "C#7"),
            listOf("D", "A/C#", "Bm7", "E7", "A", "D", "E7"),
            listOf("Bm7", "E7", "A", "E/G#", "F#m7", "C#m7"),
            listOf("D", "A/C#", "Bm7", "E7", "A"),
        )
        for (row in rows) {
            assertEquals("row $row lost a chord once merged", row, taken(row + lyricWords))
        }
    }

    @Test
    fun `a lone symbol among Korean lyrics is not stamped into the words`() {
        // A syllable misread as a chord would otherwise be overwritten mid-lyric.
        val lyricsWithNoise = listOf("살", "아", "계", "A", "시", "네")
        assertEquals(emptyList<String>(), taken(lyricsWithNoise))

        // Even a longer misreading needs chords around it once Hangul is on the line.
        assertEquals(emptyList<String>(), taken(listOf("원", "한왕", "Cmaj7", "내", "안에")))
    }

    @Test
    fun `chords still survive on a line that also carries Korean lyrics`() {
        // The merged chord+lyric row must keep working; only lone strays are dropped.
        assertEquals(chordRow, taken(mergedRow))
    }

    @Test
    fun `a stray reading among lyrics is named as such, a real chord row is not`() {
        // The lone letter is refused, and the reason travels with it so a later pass that
        // sees only a cropped scrap of the same row cannot quietly accept it.
        val strayInLyrics = listOf("살", "아", "계", "A", "시", "네")
        assertEquals(setOf(3), SheetTextFilter.lyricRejections(strayInLyrics))

        // A genuine chord row merged with its lyrics refuses nothing.
        assertEquals(emptySet<Int>(), SheetTextFilter.lyricRejections(mergedRow))

        // A row with no Korean on it is not the lyric case at all.
        assertEquals(emptySet<Int>(), SheetTextFilter.lyricRejections(chordRow))
    }

    @Test
    fun `punctuation left by a scan is trimmed off`() {
        assertEquals("Cmaj7", SheetTextFilter.clean("|Cmaj7|"))
        assertEquals("A", SheetTextFilter.clean("(A)"))
        assertEquals("Bm7", SheetTextFilter.clean("Bm7,"))
        assertEquals(listOf("A", "E7"), taken(listOf("|A", "E7|")))
    }

    @Test
    fun `chord-shaped text is recognised even when it does not parse`() {
        assertTrue(SheetTextFilter.looksLikeAChord("Bb7sus"))
        assertTrue(SheetTextFilter.looksLikeAChord("F#m11"))
        assertFalse(SheetTextFilter.looksLikeAChord("Come"))
        assertFalse(SheetTextFilter.looksLikeAChord("Every"))
        assertFalse(SheetTextFilter.looksLikeAChord("것은"))
    }
}
