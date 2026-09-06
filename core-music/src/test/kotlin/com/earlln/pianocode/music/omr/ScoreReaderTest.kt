package com.earlln.pianocode.music.omr

import com.earlln.pianocode.music.Instrument
import com.earlln.pianocode.music.MidiWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Engraves whole pages so the reader can be asked the only question that finally matters:
 * given this page, what notes come out, in what order, and for how long?
 */
private class Engraver(val width: Int = 900, val height: Int = 320) {
    val space = 12
    val thickness = 2
    val pitch = (space + thickness).toDouble()
    val canvas = SheetCanvas(width, height)
    private val staffTops = mutableListOf<Int>()

    fun staff(topY: Int): Int {
        canvas.staff(topY, left = 20, right = width - 20, space = space, thickness = thickness)
        staffTops += topY
        return staffTops.lastIndex
    }

    fun bottomOf(index: Int): Double = staffTops[index] + 4.0 * pitch

    fun yOf(index: Int, step: Int): Double =
        bottomOf(index) + (thickness - 1) / 2.0 - step * (pitch / 2)

    fun treble(index: Int) =
        canvas.trebleClef(40.0, staffTops[index].toDouble(), bottomOf(index), pitch)

    fun bass(index: Int) = canvas.bassClef(40.0, staffTops[index].toDouble(), pitch)

    fun sharps(index: Int, count: Int) {
        val steps = listOf(8, 5, 9, 6, 3, 7, 4)
        for (i in 0 until count) canvas.sharp(78.0 + i * pitch * 1.15, yOf(index, steps[i]), pitch)
    }

    fun flats(index: Int, count: Int) {
        val steps = listOf(4, 7, 3, 6, 2, 5, 1)
        for (i in 0 until count) canvas.flat(78.0 + i * pitch * 1.15, yOf(index, steps[i]), pitch)
    }

    /** A note, and whatever an engraver would attach to it. */
    fun note(
        index: Int,
        x: Int,
        step: Int,
        filled: Boolean = true,
        stem: Boolean = true,
        dot: Boolean = false,
        flags: Int = 0,
    ) {
        val cy = yOf(index, step)
        canvas.head(x.toDouble(), cy, pitch * 0.62, pitch * 0.47, filled)
        if (stem) {
            canvas.vLine(x + (pitch * 0.55).toInt(), (cy - pitch * 3.5).toInt(), cy.toInt(), 2)
        }
        val stemTop = (cy - pitch * 3.5).toInt()
        for (i in 0 until flags) {
            val sx = x + (pitch * 0.55).toInt()
            val y = stemTop + i * ((pitch * 0.45).toInt() + (pitch * 0.30).toInt())
            canvas.box(sx, y, sx + (pitch * 1.1).toInt(), y + (pitch * 0.45).toInt() - 1)
        }
        if (dot) {
            val dy = if (step % 2 == 0) yOf(index, step + 1) else cy
            canvas.head(x + pitch * 1.1, dy, pitch * 0.16, pitch * 0.16, true, tiltDegrees = 0.0)
        }
    }

    fun sharpBefore(index: Int, x: Int, step: Int) =
        canvas.sharp(x - pitch * 1.4, yOf(index, step), pitch)

    fun barLine(index: Int, x: Int) =
        canvas.vLine(x, staffTops[index], bottomOf(index).toInt(), thickness = 2)

    fun read(): ReadScore = ScoreReader.read(canvas.mono())
}

class ScoreReaderTest {

    @Test
    fun `a blank page reads as nothing rather than failing`() {
        val score = ScoreReader.read(SheetCanvas(200, 200).mono())

        assertTrue(score.isEmpty)
        assertEquals(0.0, score.lengthQuarters, 0.0)
    }

    @Test
    fun `a scale in C comes out as the notes it is`() {
        val page = Engraver(width = 900, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        listOf(-2, -1, 0, 1, 2, 3, 4, 5).forEachIndexed { i, step ->
            page.note(staff, 190 + i * 78, step)
        }
        page.canvas.hLine(190 - 12, 190 + 12, page.yOf(staff, -2).toInt(), thickness = 2)

        val score = page.read()

        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5"),
            score.notes.map { it.pitch.toString() },
        )
        assertEquals(Clef.TREBLE, score.staves.single().clef)
        assertEquals(8.0, score.lengthQuarters, 0.0)
    }

    @Test
    fun `notes follow one another in the order they are written`() {
        val page = Engraver(width = 700, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        page.note(staff, 200, 2)
        page.note(staff, 320, 4, filled = false)
        page.note(staff, 460, 3)

        val score = page.read()

        assertEquals(listOf(0.0, 1.0, 3.0), score.notes.map { it.startQuarters })
        assertEquals(listOf(1.0, 2.0, 1.0), score.notes.map { it.quarters })
    }

    @Test
    fun `the key signature reaches every note of that letter`() {
        val page = Engraver(width = 700, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        page.sharps(staff, 1)   // F sharp
        page.note(staff, 260, 1)   // F4
        page.note(staff, 400, 8)   // F5
        page.note(staff, 520, 2)   // G4, untouched

        val score = page.read()

        assertEquals(listOf("F#4", "F#5", "G4"), score.notes.map { it.pitch.toString() })
    }

    @Test
    fun `a flat signature spells the notes with flats`() {
        val page = Engraver(width = 700, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        page.flats(staff, 2)    // B flat, E flat
        page.note(staff, 300, 4)   // B4
        page.note(staff, 430, 0)   // E4
        page.note(staff, 550, 2)   // G4

        val score = page.read()

        assertEquals(listOf("Bb4", "Eb4", "G4"), score.notes.map { it.pitch.toString() })
    }

    @Test
    fun `a sign in front of a note holds until the bar line`() {
        val page = Engraver(width = 820, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        page.sharpBefore(staff, 260, 2)
        page.note(staff, 260, 2)   // G sharp, marked
        page.note(staff, 390, 2)   // still G sharp, same bar
        page.barLine(staff, 470)
        page.note(staff, 560, 2)   // G natural again

        val score = page.read()

        assertEquals(listOf("G#4", "G#4", "G4"), score.notes.map { it.pitch.toString() })
    }

    @Test
    fun `a chord sounds all at once`() {
        val page = Engraver(width = 600, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        page.note(staff, 260, 0)
        page.note(staff, 260, 2, stem = false)
        page.note(staff, 260, 4, stem = false)
        page.note(staff, 400, 5)

        val score = page.read()

        // Counting up from the bottom line, five positions above E4 is C5.
        assertEquals(listOf("E4", "G4", "B4", "C5"), score.notes.map { it.pitch.toString() })
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0), score.notes.map { it.startQuarters })
    }

    @Test
    fun `two systems play one after the other`() {
        val page = Engraver(width = 700, height = 340)
        val first = page.staff(50)
        val second = page.staff(210)
        page.treble(first)
        page.treble(second)
        page.note(first, 260, 2)
        page.note(first, 400, 4)
        page.note(second, 260, 0)

        val score = page.read()

        assertEquals(2, score.staves.size)
        assertEquals(listOf(0.0, 1.0, 2.0), score.notes.map { it.startQuarters })
        assertEquals(listOf("G4", "B4", "E4"), score.notes.map { it.pitch.toString() })
    }

    @Test
    fun `a braced pair of staves plays together`() {
        val page = Engraver(width = 700, height = 300)
        // Two staves close enough together to be one piano system.
        val right = page.staff(60)
        val left = page.staff(130)
        page.treble(right)
        page.bass(left)
        page.note(right, 300, 4)
        page.note(left, 300, 0)

        val score = page.read()

        assertEquals(Clef.TREBLE, score.staves[0].clef)
        assertEquals(Clef.BASS, score.staves[1].clef)
        assertEquals(listOf(0.0, 0.0), score.notes.map { it.startQuarters })
        assertEquals(listOf("G2", "B4"), score.notes.map { it.pitch.toString() })
    }

    @Test
    fun `what is read can be handed straight to the player`() {
        val page = Engraver(width = 700, height = 220)
        val staff = page.staff(70)
        page.treble(staff)
        page.note(staff, 220, 2)
        page.note(staff, 350, 4, filled = false)
        page.note(staff, 500, 5)

        val score = page.read()
        val midi = MidiWriter.score(
            score.notes.map { MidiWriter.TimedNote(it.pitch.midi, it.startQuarters, it.quarters) },
            instrument = Instrument.FLUTE,
            bpm = 90,
        )

        assertEquals("MThd", String(midi.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals("MTrk", String(midi.copyOfRange(14, 18), Charsets.US_ASCII))
        assertEquals(2666L, MidiWriter.scoreMillis(score.lengthQuarters, 90))
    }
}

class MidiScoreTest {

    @Test
    fun `every note that starts also stops`() {
        val midi = MidiWriter.score(
            listOf(
                MidiWriter.TimedNote(60, 0.0, 1.0),
                MidiWriter.TimedNote(64, 0.0, 2.0),
                MidiWriter.TimedNote(67, 1.0, 1.0),
            ),
        )

        var ons = 0
        var offs = 0
        for (i in midi.indices) {
            when (midi[i].toInt() and 0xFF) {
                0x90 -> ons++
                0x80 -> offs++
            }
        }
        assertEquals(3, ons)
        assertEquals(3, offs)
    }

    @Test
    fun `a repeated note is stopped before it starts again`() {
        // Two of the same pitch back to back: written the other way round, a synthesiser
        // hears one long note.
        val midi = MidiWriter.score(
            listOf(
                MidiWriter.TimedNote(60, 0.0, 1.0),
                MidiWriter.TimedNote(60, 1.0, 1.0),
            ),
        )

        val statuses = midi.map { it.toInt() and 0xFF }
        val firstOff = statuses.indexOf(0x80)
        val secondOn = statuses.indexOfLast { it == 0x90 }
        assertTrue("the note off at $firstOff should come before the note on at $secondOn",
            firstOff in 1 until secondOn)
    }

    @Test
    fun `an empty score still writes a playable file`() {
        val midi = MidiWriter.score(emptyList())

        assertEquals("MThd", String(midi.copyOfRange(0, 4), Charsets.US_ASCII))
        assertTrue(midi.size > 20)
    }
}
