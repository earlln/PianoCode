package com.earlln.pianocode.music.omr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Opening(val width: Int = 620, val height: Int = 260) {
    val space = 12
    val thickness = 2
    val pitch = (space + thickness).toDouble()
    val topY = 80
    val canvas = SheetCanvas(width, height)
    private val bottomY = topY + 4.0 * pitch

    init {
        canvas.staff(topY, left = 20, right = width - 20, space = space, thickness = thickness)
    }

    fun yOf(step: Int): Double = bottomY + (thickness - 1) / 2.0 - step * (pitch / 2)

    fun treble() = canvas.trebleClef(40.0, topY.toDouble(), bottomY, pitch)
    fun bass() = canvas.bassClef(40.0, topY.toDouble(), pitch)

    /** Sharps at the heights a treble key signature puts them. */
    fun sharpsAt(count: Int, from: Double = 70.0) {
        val steps = listOf(8, 5, 9, 6, 3, 7, 4)
        for (i in 0 until count) canvas.sharp(from + i * pitch * 1.15, yOf(steps[i]), pitch)
    }

    fun flatsAt(count: Int, from: Double = 70.0) {
        val steps = listOf(4, 7, 3, 6, 2, 5, 1)
        for (i in 0 until count) canvas.flat(from + i * pitch * 1.15, yOf(steps[i]), pitch)
    }

    fun note(x: Int, step: Int) {
        canvas.head(x.toDouble(), yOf(step), pitch * 0.62, pitch * 0.47, filled = true)
        canvas.vLine(x + (pitch * 0.55).toInt(), (yOf(step) - pitch * 3.5).toInt(), yOf(step).toInt(), 2)
    }

    fun barLine(x: Int) = canvas.vLine(x, topY, bottomY.toInt(), thickness = 2)

    fun read(): Reading {
        val image = canvas.mono()
        val scale = PageScale.estimate(image)!!
        val staff = StaffDetector.detect(image, scale).single()
        val clean = StaffLines.remove(image, listOf(staff), scale)
        return Reading(staff, clean)
    }

    class Reading(val staff: Staff, val ink: MonoImage) {
        /** Everything before this is the clef and the key signature, not music. */
        val musicFrom: Int
            get() = ClefReader.endOf(ink, staff) + (staff.space * 6).toInt()

        fun heads(): List<NoteHead> = HeadDetector.detect(ink, staff, fromX = musicFrom)
    }
}

class ClefReaderTest {

    @Test
    fun `a treble clef is recognised by reaching past the staff`() {
        val page = Opening(width = 300)
        page.treble()
        val read = page.read()

        assertEquals(Clef.TREBLE, ClefReader.detect(read.ink, read.staff))
    }

    @Test
    fun `a bass clef stays inside the staff and is read as one`() {
        val page = Opening(width = 300)
        page.bass()
        val read = page.read()

        assertEquals(Clef.BASS, ClefReader.detect(read.ink, read.staff))
    }

    @Test
    fun `a staff with nothing at its head is taken as treble`() {
        val page = Opening(width = 300)
        val read = page.read()

        assertEquals(Clef.TREBLE, ClefReader.detect(read.ink, read.staff))
    }
}

class KeyReaderTest {

    @Test
    fun `an empty signature reads as no accidentals`() {
        val page = Opening(width = 400)
        page.treble()
        page.note(200, 4)
        val read = page.read()

        val key = KeyReader.detect(read.ink, read.staff, ClefReader.endOf(read.ink, read.staff))

        assertTrue(key.isEmpty)
    }

    @Test
    fun `two sharps come back as two sharps`() {
        val page = Opening(width = 400)
        page.treble()
        page.sharpsAt(2)
        val read = page.read()

        val key = KeyReader.detect(read.ink, read.staff, ClefReader.endOf(read.ink, read.staff))

        assertEquals(KeySignature(sharps = 2), key)
    }

    @Test
    fun `three flats come back as three flats`() {
        val page = Opening(width = 400)
        page.treble()
        page.flatsAt(3)
        val read = page.read()

        val key = KeyReader.detect(read.ink, read.staff, ClefReader.endOf(read.ink, read.staff))

        assertEquals(KeySignature(flats = 3), key)
    }

    @Test
    fun `the music after the signature is not counted into it`() {
        val page = Opening(width = 500)
        page.treble()
        page.sharpsAt(1)
        // A note set a comfortable distance after the signature, as engraving does.
        page.note(220, 4)
        val read = page.read()

        val key = KeyReader.detect(read.ink, read.staff, ClefReader.endOf(read.ink, read.staff))

        assertEquals(KeySignature(sharps = 1), key)
    }

    @Test
    fun `a signature says which letters are altered`() {
        assertEquals(1, KeySignature(sharps = 1).accidentalFor(3))   // F sharp
        assertEquals(0, KeySignature(sharps = 1).accidentalFor(0))   // C natural
        assertEquals(1, KeySignature(sharps = 2).accidentalFor(0))   // C sharp
        assertEquals(-1, KeySignature(flats = 1).accidentalFor(6))   // B flat
        assertEquals(-1, KeySignature(flats = 2).accidentalFor(2))   // E flat
        assertEquals(0, KeySignature(flats = 2).accidentalFor(5))    // A natural
    }
}

class AccidentalReaderTest {

    @Test
    fun `a sharp in front of a note is found`() {
        val page = Opening(width = 400)
        page.treble()
        page.note(220, 4)
        page.canvas.sharp(220 - page.pitch * 1.4, page.yOf(4), page.pitch)
        val read = page.read()

        val head = read.heads().single()

        assertEquals(Accidental.SHARP, AccidentalReader.before(read.ink, read.staff, head))
    }

    @Test
    fun `a flat in front of a note is found`() {
        val page = Opening(width = 400)
        page.treble()
        page.note(220, 3)
        page.canvas.flat(220 - page.pitch * 1.4, page.yOf(3), page.pitch)
        val read = page.read()

        val head = read.heads().single()

        assertEquals(Accidental.FLAT, AccidentalReader.before(read.ink, read.staff, head))
    }

    @Test
    fun `a natural in front of a note is found`() {
        val page = Opening(width = 400)
        page.treble()
        page.note(220, 5)
        page.canvas.natural(220 - page.pitch * 1.4, page.yOf(5), page.pitch)
        val read = page.read()

        val head = read.heads().single()

        assertEquals(Accidental.NATURAL, AccidentalReader.before(read.ink, read.staff, head))
    }

    @Test
    fun `a note with nothing in front of it has no sign`() {
        val page = Opening(width = 400)
        page.treble()
        page.note(220, 4)
        val read = page.read()

        val head = read.heads().single()

        assertNull(AccidentalReader.before(read.ink, read.staff, head))
    }

    @Test
    fun `a sign belonging to a note two lines away is not borrowed`() {
        val page = Opening(width = 420)
        page.treble()
        page.note(240, 1)
        // The sharp stands at the height of a different note entirely.
        page.canvas.sharp(240 - page.pitch * 1.4, page.yOf(7), page.pitch)
        val read = page.read()

        val head = read.heads().first { it.step == 1 }

        assertNull(AccidentalReader.before(read.ink, read.staff, head))
    }
}

class BarLinesTest {

    @Test
    fun `bar lines are found and note stems are not`() {
        val page = Opening(width = 520)
        page.treble()
        page.note(150, 2)
        page.barLine(200)
        page.note(260, 4)
        page.barLine(320)
        val read = page.read()

        val bars = BarLines.detect(read.ink, read.staff, fromX = read.musicFrom)

        assertEquals(listOf(200, 320), bars.map { (it / 10) * 10 })
    }
}
