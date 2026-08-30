package com.earlln.pianocode.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {

    @Test
    fun `pitch classes wrap correctly`() {
        assertEquals(0, Note(0, 0).pitchClass)      // C
        assertEquals(11, Note(0, -1).pitchClass)    // Cb sounds as B
        assertEquals(0, Note(6, 1).pitchClass)      // B# sounds as C
        assertEquals(6, Note(3, 1).pitchClass)      // F#
        assertEquals(6, Note(4, -1).pitchClass)     // Gb
    }

    @Test
    fun `transposing keeps the letter distance the interval demands`() {
        val bFlat = Note(6, -1)
        assertEquals("D", bFlat.transposeBy(2, 4).name)   // major third
        assertEquals("Ab", bFlat.transposeBy(6, 10).name) // minor seventh
        assertEquals("F", bFlat.transposeBy(4, 7).name)   // perfect fifth
    }

    @Test
    fun `parses written note names`() {
        assertEquals(Note(3, 1), Note.parse("F#"))
        assertEquals(Note(6, -1), Note.parse("Bb"))
        assertEquals(Note(2, 0), Note.parse("E"))
        assertEquals(Note(4, 1), Note.parse("G♯"))
        assertNull(Note.parse("H"))
        assertNull(Note.parse(""))
    }

    @Test
    fun `midi numbers follow the standard where middle C is 60`() {
        assertEquals(60, Pitch(Note(0, 0), 4).midi)
        assertEquals(69, Pitch(Note(5, 0), 4).midi)  // A4 = 440 Hz
        assertEquals(61, Pitch(Note(0, 1), 4).midi)  // C#4
        assertEquals(61, Pitch(Note(1, -1), 4).midi) // Db4 sounds the same
    }
}

class ChordSpellingTest {

    private fun notesOf(symbol: String): String =
        ChordParser.parse(symbol)!!.notes.joinToString(" ") { it.name }

    @Test
    fun `triads are spelled with thirds, never enharmonics`() {
        assertEquals("C E G", notesOf("C"))
        assertEquals("A C E", notesOf("Am"))
        assertEquals("Bb D F", notesOf("Bb"))
        assertEquals("F# A# C#", notesOf("F#"))
        assertEquals("Eb Gb Bbb", notesOf("Ebdim"))
        assertEquals("C E G#", notesOf("Caug"))
    }

    @Test
    fun `seventh chords keep the seventh on the seventh letter`() {
        assertEquals("C E G Bb", notesOf("C7"))
        assertEquals("C E G B", notesOf("Cmaj7"))
        assertEquals("D F A C", notesOf("Dm7"))
        assertEquals("B D F A", notesOf("Bm7b5"))
        assertEquals("C Eb Gb Bbb", notesOf("Cdim7"))
        assertEquals("A C E G#", notesOf("AmMaj7"))
    }

    @Test
    fun `extensions land on the right letters past the octave`() {
        assertEquals("C E G Bb D", notesOf("C9"))
        assertEquals("C E G B D", notesOf("Cmaj9"))
        assertEquals("C E G Bb D A", notesOf("C13"))
        assertEquals("C E G Bb Db", notesOf("C7b9"))
        assertEquals("C E G Bb D#", notesOf("C7#9"))
        assertEquals("C E G Bb D F#", notesOf("C7#11"))
    }

    @Test
    fun `every catalogue chord on every root stays readable`() {
        for (root in ChordLibrary.ROOTS) {
            for (quality in ChordQuality.ALL) {
                val chord = Chord(root, quality)
                assertEquals(
                    "${chord.symbol} should have ${quality.toneCount} notes",
                    quality.toneCount,
                    chord.notes.size,
                )
                for (note in chord.notes) {
                    assertTrue(
                        "${chord.symbol} produced an unreadable spelling ${note.name}",
                        note.isPractical,
                    )
                }
            }
        }
    }

    @Test
    fun `chord intervals match the published formulas`() {
        assertEquals(listOf(0, 4, 7), ChordQuality.MAJOR.intervals)
        assertEquals(listOf(0, 3, 7), ChordQuality.MINOR.intervals)
        assertEquals(listOf(0, 3, 6, 9), ChordQuality.DIMINISHED_7.intervals)
        assertEquals(listOf(0, 3, 6, 10), ChordQuality.HALF_DIMINISHED_7.intervals)
        assertEquals(listOf(0, 4, 7, 10, 14), ChordQuality.DOMINANT_9.intervals)
        assertEquals(listOf(0, 5, 7, 10), ChordQuality.DOMINANT_7_SUS4.intervals)
    }

    @Test
    fun `quality ids and symbols are unique`() {
        val ids = ChordQuality.ALL.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        val symbols = ChordQuality.ALL.map { it.symbol }
        assertEquals("duplicate symbols: ${symbols.groupBy { it }.filterValues { it.size > 1 }.keys}",
            symbols.size, symbols.distinct().size)
    }

    @Test
    fun `each family has exactly one base chord`() {
        for (family in ChordFamily.entries) {
            val bases = ChordQuality.byFamily(family).filter { it.isBase }
            assertEquals("family ${family.id}", 1, bases.size)
        }
    }
}

class VoicingTest {

    @Test
    fun `root position stacks upward without collisions`() {
        val voicing = Chord(Note(0, 0), ChordQuality.MAJOR_7).voicing(startOctave = 4)
        assertEquals(listOf("C4", "E4", "G4", "B4"), voicing.map { it.toString() })
        assertEquals(voicing.map { it.midi }.sorted(), voicing.map { it.midi })
    }

    @Test
    fun `wide chords roll into the next octave`() {
        val voicing = Chord(Note(0, 0), ChordQuality.DOMINANT_13).voicing(startOctave = 3)
        assertEquals(listOf("C3", "E3", "G3", "Bb3", "D4", "A4"), voicing.map { it.toString() })
    }

    @Test
    fun `inversions lift the bottom notes an octave`() {
        val chord = Chord(Note(0, 0), ChordQuality.MAJOR)
        assertEquals(listOf("C4", "E4", "G4"), chord.voicing(4, 0).map { it.toString() })
        assertEquals(listOf("E4", "G4", "C5"), chord.voicing(4, 1).map { it.toString() })
        assertEquals(listOf("G4", "C5", "E5"), chord.voicing(4, 2).map { it.toString() })
        assertEquals(listOf("C4", "E4", "G4"), chord.voicing(4, 3).map { it.toString() })
    }

    @Test
    fun `a slash bass sits below the chord`() {
        val chord = ChordParser.parse("C/E")!!
        val voicing = chord.voicing(startOctave = 4)
        assertEquals("E3", voicing.first().toString())
        assertTrue(voicing.first().midi < voicing[1].midi)
    }
}

class ChordParserTest {

    @Test
    fun `reads the common symbols`() {
        assertEquals("Cmaj7", ChordParser.parse("Cmaj7")!!.symbol)
        assertEquals("F#m7", ChordParser.parse("F#m7")!!.symbol)
        assertEquals("Bbm7b5", ChordParser.parse("Bbm7b5")!!.symbol)
        assertEquals("Gsus4", ChordParser.parse("Gsus4")!!.symbol)
        assertEquals("Adim7", ChordParser.parse("Adim7")!!.symbol)
        assertEquals("E7#9", ChordParser.parse("E7#9")!!.symbol)
    }

    @Test
    fun `case decides between major and minor sevenths`() {
        assertEquals(ChordQuality.MAJOR_7, ChordParser.parse("CM7")!!.quality)
        assertEquals(ChordQuality.MINOR_7, ChordParser.parse("Cm7")!!.quality)
        assertEquals(ChordQuality.MAJOR, ChordParser.parse("CM")!!.quality)
        assertEquals(ChordQuality.MINOR, ChordParser.parse("Cm")!!.quality)
    }

    @Test
    fun `accepts the glyphs printed on real sheet music`() {
        assertEquals("Bbmaj7", ChordParser.parse("B♭maj7")!!.symbol)
        assertEquals("F#m7", ChordParser.parse("F♯m7")!!.symbol)
        assertEquals("Cmaj7", ChordParser.parse("CΔ7")!!.symbol)
        assertEquals("Cdim", ChordParser.parse("C°")!!.symbol)
    }

    @Test
    fun `reads slash chords`() {
        val chord = ChordParser.parse("Am7/G")!!
        assertEquals(Note(5, 0), chord.root)
        assertEquals(ChordQuality.MINOR_7, chord.quality)
        assertEquals(Note(4, 0), chord.bass)
        assertEquals("Am7/G", chord.symbol)
    }

    @Test
    fun `rejects text that is not a chord`() {
        assertNull(ChordParser.parse("Hello"))
        assertNull(ChordParser.parse("Verse"))
        assertNull(ChordParser.parse("123"))
        assertNull(ChordParser.parse("Cm7zz"))
        assertNull(ChordParser.parse(""))
    }

    @Test
    fun `strict mode ignores lower-case roots so lyrics do not become chords`() {
        assertNotNull(ChordParser.parse("a"))
        assertNull(ChordParser.parse("a", requireUppercaseRoot = true))
        assertNotNull(ChordParser.parse("Am", requireUppercaseRoot = true))
    }

    @Test
    fun `every catalogue symbol parses back to the same chord`() {
        for (root in ChordLibrary.ROOTS) {
            for (quality in ChordQuality.ALL) {
                val chord = Chord(root, quality)
                val reparsed = ChordParser.parse(chord.symbol)
                assertNotNull("failed to parse ${chord.symbol}", reparsed)
                assertEquals(chord.symbol, reparsed!!.symbol)
            }
        }
    }

    @Test
    fun `finds the chords in a chord line`() {
        val found = ChordParser.findChords("| Cmaj7  Am7 | Dm7  G7 |", requireUppercaseRoot = true)
        assertEquals(listOf("Cmaj7", "Am7", "Dm7", "G7"), found.map { it.chord.symbol })
        assertEquals("Cmaj7", "| Cmaj7  Am7 | Dm7  G7 |".substring(found[0].start, found[0].end))
    }
}

class KeyTest {

    @Test
    fun `key signatures count the right accidentals`() {
        assertEquals(0, Key(Note(0, 0)).fifths)                 // C major
        assertEquals(1, Key(Note(4, 0)).fifths)                 // G major
        assertEquals(-1, Key(Note(3, 0)).fifths)                // F major
        assertEquals(-2, Key(Note(6, -1)).fifths)               // Bb major
        assertEquals(6, Key(Note(3, 1)).fifths)                 // F# major
        assertEquals(0, Key(Note(5, 0), ScaleType.NATURAL_MINOR).fifths)  // A minor
        assertEquals(-3, Key(Note(0, 0), ScaleType.NATURAL_MINOR).fifths) // C minor
    }

    @Test
    fun `scales are spelled one note per letter`() {
        assertEquals(
            listOf("C", "D", "E", "F", "G", "A", "B"),
            Key(Note(0, 0)).notes.map { it.name },
        )
        assertEquals(
            listOf("F#", "G#", "A#", "B", "C#", "D#", "E#"),
            Key(Note(3, 1)).notes.map { it.name },
        )
        assertEquals(
            listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"),
            Key(Note(2, -1)).notes.map { it.name },
        )
    }

    @Test
    fun `major harmony gives the familiar roman numerals`() {
        val triads = Key(Note(0, 0)).diatonicChords()
        assertEquals(listOf("C", "Dm", "Em", "F", "G", "Am", "Bdim"), triads.map { it.chord.symbol })
        assertEquals(listOf("I", "ii", "iii", "IV", "V", "vi", "vii°"), triads.map { it.romanNumeral })
    }

    @Test
    fun `major sevenths give the jazz harmony`() {
        val sevenths = Key(Note(0, 0)).diatonicChords(seventh = true)
        assertEquals(
            listOf("Cmaj7", "Dm7", "Em7", "Fmaj7", "G7", "Am7", "Bm7b5"),
            sevenths.map { it.chord.symbol },
        )
    }

    @Test
    fun `minor harmony is built on the natural minor scale`() {
        val triads = Key(Note(5, 0), ScaleType.NATURAL_MINOR).diatonicChords()
        assertEquals(listOf("Am", "Bdim", "C", "Dm", "Em", "F", "G"), triads.map { it.chord.symbol })
    }

    @Test
    fun `a key spells accidentals the way its signature does`() {
        assertEquals("Bb", Key(Note(3, 0)).spell(10).name)   // F major writes Bb
        assertEquals("A#", Key(Note(6, 0)).spell(10).name)   // B major writes A#
    }
}

class TransposerTest {

    private fun convert(symbol: String, from: Key, to: Key, mode: ConversionMode): String =
        Transposer.convert(ChordParser.parse(symbol)!!, from, to, mode).converted.symbol

    @Test
    fun `transposing keeps the harmony and respells for the target key`() {
        val c = Key(Note(0, 0))
        val f = Key(Note(3, 0))
        assertEquals("F", convert("C", c, f, ConversionMode.TRANSPOSE))
        assertEquals("Bb", convert("F", c, f, ConversionMode.TRANSPOSE))
        assertEquals("Gm7", convert("Dm7", c, f, ConversionMode.TRANSPOSE))
        assertEquals("C7", convert("G7", c, f, ConversionMode.TRANSPOSE))
        assertEquals("Am7b5", convert("Em7b5", c, f, ConversionMode.TRANSPOSE))
    }

    @Test
    fun `transposing into a sharp key uses sharps`() {
        val c = Key(Note(0, 0))
        val e = Key(Note(2, 0))
        assertEquals("E", convert("C", c, e, ConversionMode.TRANSPOSE))
        assertEquals("F#m", convert("Dm", c, e, ConversionMode.TRANSPOSE))
        assertEquals("C#m", convert("Am", c, e, ConversionMode.TRANSPOSE))
    }

    @Test
    fun `slash chords move with their bass`() {
        val result = convert("C/E", Key(Note(0, 0)), Key(Note(4, 0)), ConversionMode.TRANSPOSE)
        assertEquals("G/B", result)
    }

    @Test
    fun `diatonic mode recolours major into minor`() {
        val cMajor = Key(Note(0, 0))
        val cMinor = Key(Note(0, 0), ScaleType.NATURAL_MINOR)
        assertEquals("Cm", convert("C", cMajor, cMinor, ConversionMode.DIATONIC))
        assertEquals("Eb", convert("Em", cMajor, cMinor, ConversionMode.DIATONIC))
        assertEquals("Gm", convert("G", cMajor, cMinor, ConversionMode.DIATONIC))
    }

    @Test
    fun `diatonic mode keeps a chord as tall as it was`() {
        val cMajor = Key(Note(0, 0))
        val aMinor = Key(Note(5, 0), ScaleType.NATURAL_MINOR)
        val result = Transposer.convert(
            ChordParser.parse("Cmaj9")!!, cMajor, aMinor, ConversionMode.DIATONIC,
        )
        assertEquals(9, result.converted.quality.tones.maxOf { it.degree })
    }

    @Test
    fun `key detection follows the cadence`() {
        val chords = listOf("Dm7", "G7", "Cmaj7", "Am7", "Dm7", "G7", "Cmaj7")
            .map { ChordParser.parse(it)!! }
        val key = Transposer.detectKey(chords)!!
        assertEquals("C", key.tonic.name)
        assertEquals(ScaleType.MAJOR, key.type)
    }

    @Test
    fun `key detection recognises a minor progression`() {
        val chords = listOf("Am", "F", "C", "G", "Am").map { ChordParser.parse(it)!! }
        val key = Transposer.detectKey(chords)!!
        assertEquals("A", key.tonic.name)
        assertEquals(ScaleType.NATURAL_MINOR, key.type)
    }

    @Test
    fun `transposing every catalogue chord to every key stays readable`() {
        for (target in Key.COMMON_KEYS) {
            for (quality in listOf(ChordQuality.MAJOR_7, ChordQuality.MINOR_7, ChordQuality.DOMINANT_7_ALTERED)) {
                for (root in ChordLibrary.PRIMARY_ROOTS) {
                    val converted = Transposer.convert(
                        Chord(root, quality), Key(Note(0, 0)), target, ConversionMode.TRANSPOSE,
                    ).converted
                    assertTrue(
                        "unreadable root ${converted.root.name} in ${target.name}",
                        converted.root.isPractical,
                    )
                    converted.notes.forEach {
                        assertTrue("unreadable note ${it.name} in ${converted.symbol}", it.isPractical)
                    }
                }
            }
        }
    }
}

class ChordLibraryTest {

    @Test
    fun `every family exposes a base chord and variations`() {
        for (family in ChordFamily.entries) {
            val all = ChordLibrary.chordsIn(Note(0, 0), family)
            assertTrue("family ${family.id} is empty", all.isNotEmpty())
            assertEquals(all.first().symbol, ChordLibrary.baseChord(Note(0, 0), family).symbol)
            assertEquals(all.size - 1, ChordLibrary.variationsIn(Note(0, 0), family).size)
        }
    }

    @Test
    fun `search finds chords by symbol and by name`() {
        assertEquals("F#m7b5", ChordLibrary.search("F#m7b5").first().symbol)
        assertTrue(ChordLibrary.search("마이너").isNotEmpty())
        assertTrue(ChordLibrary.search("diminished").isNotEmpty())
        assertTrue(ChordLibrary.search("   ").isEmpty())
    }

    @Test
    fun `identify names a set of pitch classes`() {
        val found = ChordLibrary.identify(setOf(0, 4, 7))
        assertTrue(found.any { it.symbol == "C" })
    }

    @Test
    fun `the catalogue is large enough to be useful`() {
        assertTrue(ChordQuality.ALL.size >= 50)
        assertTrue(ChordLibrary.totalChordCount >= 800)
    }
}
