package com.earlln.pianocode.music.omr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draws a staff at a fixed size so a test can say where a head goes in the language the
 * reader answers in: `at(step)` puts a head on a line or in a space, counting up from the
 * bottom line, and the reader has to come back with the same number.
 */
private class Page(
    val space: Int = 12,
    val thickness: Int = 2,
    val topY: Int = 60,
    val width: Int = 520,
    val height: Int = 220,
) {
    val canvas = SheetCanvas(width, height)
    private val stepPixels = (space + thickness) / 2.0
    private val bottomY = topY + 4.0 * (space + thickness)

    init {
        canvas.staff(topY, left = 20, right = width - 20, space = space, thickness = thickness)
    }

    fun yOf(step: Int): Double = bottomY + (thickness - 1) / 2.0 - step * stepPixels

    /** A note head at [x] on [step], with the stem an engraver would give it. */
    fun note(x: Int, step: Int, filled: Boolean = true, stem: Boolean = true) {
        val cy = yOf(step)
        val pitch = (space + thickness).toDouble()
        // A head is one staff space tall — it just fits between two lines — and about a
        // third again as wide.
        canvas.head(x.toDouble(), cy, pitch * 0.62, pitch * 0.47, filled)
        if (stem) {
            val stemX = x + (pitch * 0.55).toInt()
            canvas.vLine(stemX, (cy - pitch * 3.5).toInt(), cy.toInt(), thickness = 2)
        }
    }

    /** The beam an engraver would draw across the ends of two stems. */
    fun beam(from: Int, to: Int, atStep: Int) {
        val pitch = (space + thickness).toDouble()
        val stemTop = (yOf(atStep) - pitch * 3.5).toInt()
        val thick = (pitch * 0.5).toInt()
        canvas.box(from + (pitch * 0.55).toInt(), stemTop, to + (pitch * 0.55).toInt() + 1, stemTop + thick)
    }

    fun read(): Pair<Staff, List<NoteHead>> {
        val image = canvas.mono()
        val scale = PageScale.estimate(image)!!
        val staff = StaffDetector.detect(image, scale).single()
        val clean = StaffLines.remove(image, listOf(staff), scale)
        return staff to HeadDetector.detect(clean, staff)
    }
}

class StaffLinesTest {

    @Test
    fun `the lines go and the stems stay`() {
        val page = Page()
        page.note(x = 120, step = 2)
        val image = page.canvas.mono()
        val scale = PageScale.estimate(image)!!
        val staff = StaffDetector.detect(image, scale).single()

        val clean = StaffLines.remove(image, listOf(staff), scale)

        // Well away from the note, the line is gone.
        val lineY = staff.lineY[2].toInt()
        assertTrue("line survived at x=400", (lineY - 1..lineY + 1).none { clean.isInk(400, it) })
        // The stem crosses lines and must still be there above the head.
        val stemX = 120 + (12 * 0.55).toInt()
        val stemY = (staff.yOfStep(2) - 20).toInt()
        assertTrue(
            "stem was rubbed out",
            (stemX - 2..stemX + 3).any { clean.isInk(it, stemY) },
        )
    }
}

class HeadDetectorTest {

    @Test
    fun `a head on each line and space comes back on the right step`() {
        val page = Page(width = 720)
        val steps = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        steps.forEachIndexed { index, step -> page.note(60 + index * 70, step) }

        val (_, heads) = page.read()

        assertEquals(steps.size, heads.size)
        assertEquals(steps, heads.map { it.step })
        assertTrue(heads.all { it.filled })
    }

    @Test
    fun `ledger notes above and below the staff are counted too`() {
        val page = Page(width = 400)
        page.note(100, step = -2)  // first ledger below
        page.note(200, step = 10)  // first ledger above
        // Engravers draw the short line the note sits on; it must not confuse the reader.
        page.canvas.hLine(100 - 10, 100 + 10, page.yOf(-2).toInt(), thickness = 2)
        page.canvas.hLine(200 - 10, 200 + 10, page.yOf(10).toInt(), thickness = 2)

        val (_, heads) = page.read()

        assertEquals(listOf(-2, 10), heads.map { it.step }.sorted())
    }

    @Test
    fun `an open head is told apart from a filled one`() {
        val page = Page(width = 300)
        page.note(90, step = 4, filled = false)
        page.note(190, step = 4, filled = true)

        val (_, heads) = page.read()

        assertEquals(2, heads.size)
        assertEquals(listOf(false, true), heads.sortedBy { it.x }.map { it.filled })
    }

    @Test
    fun `a beam is not read as a row of note heads`() {
        val page = Page(width = 320)
        page.note(100, step = 2)
        page.note(160, step = 3)
        page.beam(from = 100, to = 160, atStep = 2)

        val (_, heads) = page.read()

        assertEquals(listOf(2, 3), heads.map { it.step })
    }

    @Test
    fun `a beam lying across a staff line is still not a note`() {
        val page = Page(width = 320)
        page.note(100, step = -2)
        page.note(160, step = -1)
        // Short stems put this beam right along the middle line, where the ink of the beam
        // and the ink of the line become one bar.
        val beamY = page.yOf(4).toInt()
        page.canvas.box(107, beamY - 3, 167, beamY + 3)

        val (_, heads) = page.read()

        assertEquals(listOf(-2, -1), heads.map { it.step })
    }

    @Test
    fun `a chord of stacked heads keeps every note`() {
        val page = Page(width = 300)
        page.note(120, step = 0)
        page.note(120, step = 2, stem = false)
        page.note(120, step = 4, stem = false)

        val (_, heads) = page.read()

        assertEquals(listOf(4, 2, 0), heads.map { it.step })
    }

    @Test
    fun `a bar line is not a note`() {
        val page = Page(width = 300)
        page.note(100, step = 2)
        val staffTop = 60
        page.canvas.vLine(220, staffTop, staffTop + 4 * 14, thickness = 3)

        val (_, heads) = page.read()

        assertEquals(listOf(2), heads.map { it.step })
    }

    @Test
    fun `a phrase photographed under a shadow still reads`() {
        val page = Page(width = 620)
        val steps = listOf(0, 2, 4, 3, 1, 5, 7, 8)
        steps.forEachIndexed { index, step -> page.note(60 + index * 65, step) }
        page.canvas.shade(320, 0, 619, 219, by = 140)

        val (_, heads) = page.read()

        assertEquals(steps, heads.map { it.step })
    }

    @Test
    fun `steps become the pitches the clef says they are`() {
        val page = Page(width = 480)
        // C major on the treble staff, from middle C on its ledger line upward.
        val steps = listOf(-2, -1, 0, 1, 2, 3)
        steps.forEachIndexed { index, step -> page.note(70 + index * 65, step) }
        page.canvas.hLine(70 - 12, 70 + 12, page.yOf(-2).toInt(), thickness = 2)

        val (_, heads) = page.read()

        assertEquals(
            listOf("C4", "D4", "E4", "F4", "G4", "A4"),
            heads.map { Clef.TREBLE.pitchAt(it.step).toString() },
        )
        assertEquals(60, Clef.TREBLE.pitchAt(heads.first().step).midi)
    }
}

class ClefTest {

    @Test
    fun `the treble staff reads the way it is taught`() {
        assertEquals("E4", Clef.TREBLE.pitchAt(0).toString())
        assertEquals("F4", Clef.TREBLE.pitchAt(1).toString())
        assertEquals("B4", Clef.TREBLE.pitchAt(4).toString())
        assertEquals("F5", Clef.TREBLE.pitchAt(8).toString())
        assertEquals("C4", Clef.TREBLE.pitchAt(-2).toString())
        assertEquals(60, Clef.TREBLE.pitchAt(-2).midi)
    }

    @Test
    fun `the bass staff starts on its own line`() {
        assertEquals("G2", Clef.BASS.pitchAt(0).toString())
        assertEquals("A3", Clef.BASS.pitchAt(8).toString())
        assertEquals("C4", Clef.BASS.pitchAt(10).toString())
        assertEquals(60, Clef.BASS.pitchAt(10).midi)
    }

    @Test
    fun `an accidental rides along with the letter`() {
        assertEquals("F#5", Clef.TREBLE.pitchAt(8, accidental = 1).toString())
        assertEquals("Bb4", Clef.TREBLE.pitchAt(4, accidental = -1).toString())
    }
}
