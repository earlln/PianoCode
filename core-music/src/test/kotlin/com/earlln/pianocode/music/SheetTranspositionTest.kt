package com.earlln.pianocode.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transposes a real lead sheet — 손경민, "충만" — from A major to C major.
 *
 * This is the case that exposed the bug worth guarding against: a conversion that
 * rewrites most symbols but silently leaves a few in the original key, producing a page
 * in two keys at once. Every chord on the page is checked here, so a partial conversion
 * fails the build rather than reaching a musician's music stand.
 */
class SheetTranspositionTest {

    /** Every chord symbol printed on the A major original, in reading order. */
    private val originalChords = listOf(
        // bars 1-5
        "A", "E/G#", "F#m7", "C#m7", "D", "A/C#", "Bm7", "E7",
        // bars 6-8
        "A", "E/G#", "F#m7", "C#m7", "D", "A/C#",
        // bars 9-11
        "Bm7", "E7", "A", "E/G#", "F#m7", "C#7",
        // bars 12-14
        "D", "A/C#", "Bm7", "E7", "A", "D", "E7",
        // bars 15-17
        "A", "E/G#", "F#m7", "C#m7", "D", "A/C#",
        // bars 18-20
        "Bm7", "E7", "A", "E/G#", "F#m7", "C#m7",
        // bars 21-23
        "D", "A/C#", "Bm7", "E7", "A",
    )

    /** The same sheet in C major. Every entry is a minor third above its counterpart. */
    private val expectedInC = listOf(
        "C", "G/B", "Am7", "Em7", "F", "C/E", "Dm7", "G7",
        "C", "G/B", "Am7", "Em7", "F", "C/E",
        "Dm7", "G7", "C", "G/B", "Am7", "E7",
        "F", "C/E", "Dm7", "G7", "C", "F", "G7",
        "C", "G/B", "Am7", "Em7", "F", "C/E",
        "Dm7", "G7", "C", "G/B", "Am7", "Em7",
        "F", "C/E", "Dm7", "G7", "C",
    )

    private val aMajor = Key(Note(5, 0), ScaleType.MAJOR)
    private val cMajor = Key(Note(0, 0), ScaleType.MAJOR)

    @Test
    fun `every chord on the sheet moves from A major to C major`() {
        val parsed = originalChords.map { symbol ->
            ChordParser.parse(symbol).also { assertNotNull("could not read $symbol", it) }!!
        }
        val converted = Transposer
            .convertAll(parsed, aMajor, cMajor, ConversionMode.TRANSPOSE)
            .map { it.converted.symbol }

        assertEquals(expectedInC, converted)
    }

    @Test
    fun `no chord is left behind in the original key`() {
        val parsed = originalChords.map { ChordParser.parse(it)!! }
        val converted = Transposer.convertAll(parsed, aMajor, cMajor, ConversionMode.TRANSPOSE)

        // A-major-only accidentals must not survive the move into C major, which has none.
        for (conversion in converted) {
            val notes = listOfNotNull(conversion.converted.root, conversion.converted.bass)
            for (note in notes) {
                assertTrue(
                    "${conversion.original.symbol} -> ${conversion.converted.symbol} kept an " +
                        "accidental that C major does not use",
                    note.accidental == 0,
                )
            }
        }
    }

    @Test
    fun `the shift from A to C is a minor third`() {
        assertEquals(3, aMajor.semitonesTo(cMajor))
    }

    @Test
    fun `slash chords keep the interval between root and bass`() {
        for (symbol in listOf("A/C#", "E/G#", "D/F#", "Bm7/A")) {
            val original = ChordParser.parse(symbol)!!
            val moved = Transposer.convert(original, aMajor, cMajor, ConversionMode.TRANSPOSE)
                .converted
            assertEquals(
                "$symbol changed the distance between its root and bass",
                Math.floorMod(original.bass!!.pitchClass - original.root.pitchClass, 12),
                Math.floorMod(moved.bass!!.pitchClass - moved.root.pitchClass, 12),
            )
        }
    }

    @Test
    fun `the sheet survives a round trip back to A major`() {
        val parsed = originalChords.map { ChordParser.parse(it)!! }
        val toC = Transposer.convertAll(parsed, aMajor, cMajor, ConversionMode.TRANSPOSE)
        val backToA = Transposer.convertAll(
            toC.map { it.converted }, cMajor, aMajor, ConversionMode.TRANSPOSE,
        )
        assertEquals(originalChords, backToA.map { it.converted.symbol })
    }

    @Test
    fun `transposing the sheet into every key never produces a doubtful symbol`() {
        val parsed = originalChords.map { ChordParser.parse(it)!! }
        for (target in Key.COMMON_KEYS) {
            for (conversion in Transposer.convertAll(parsed, aMajor, target, ConversionMode.TRANSPOSE)) {
                val symbol = conversion.converted.symbol
                assertNotNull(
                    "$symbol in ${target.name} cannot be read back",
                    ChordParser.parse(symbol),
                )
                assertTrue(
                    "$symbol in ${target.name} needs an impractical accidental",
                    conversion.converted.notes.all { it.isPractical },
                )
            }
        }
    }
}
