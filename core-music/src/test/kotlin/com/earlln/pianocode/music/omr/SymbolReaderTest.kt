package com.earlln.pianocode.music.omr

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A staff that can be written on in the terms an engraver uses, so a test can say
 * "a dotted minim here" and check the reader says the same back.
 */
private class Score(val width: Int = 640, val height: Int = 240) {
    val space = 12
    val thickness = 2
    val pitch = (space + thickness).toDouble()
    val topY = 70
    val canvas = SheetCanvas(width, height)
    private val bottomY = topY + 4.0 * pitch

    init {
        canvas.staff(topY, left = 20, right = width - 20, space = space, thickness = thickness)
    }

    fun yOf(step: Int): Double = bottomY + (thickness - 1) / 2.0 - step * (pitch / 2)

    fun head(x: Int, step: Int, filled: Boolean = true) {
        canvas.head(x.toDouble(), yOf(step), pitch * 0.62, pitch * 0.47, filled)
    }

    /** The stem an engraver draws: to the right going up, to the left going down. */
    fun stem(x: Int, step: Int, up: Boolean = true, lengthSpaces: Double = 3.5) {
        val cy = yOf(step)
        val sx = x + if (up) (pitch * 0.55).toInt() else -(pitch * 0.55).toInt()
        if (up) {
            canvas.vLine(sx, (cy - pitch * lengthSpaces).toInt(), cy.toInt(), thickness = 2)
        } else {
            canvas.vLine(sx, cy.toInt(), (cy + pitch * lengthSpaces).toInt(), thickness = 2)
        }
    }

    /** [count] beams across the stems of the notes at [from] and [to]. */
    fun beams(from: Int, to: Int, atStep: Int, count: Int, lengthSpaces: Double = 3.5) {
        val stemTop = (yOf(atStep) - pitch * lengthSpaces).toInt()
        val thick = (pitch * 0.5).toInt()
        val gap = (pitch * 0.28).toInt()
        val x0 = from + (pitch * 0.55).toInt()
        val x1 = to + (pitch * 0.55).toInt() + 1
        for (i in 0 until count) {
            val y = stemTop + i * (thick + gap)
            canvas.box(x0, y, x1, y + thick - 1)
        }
    }

    /** The hook on a single quaver or semiquaver. */
    fun flags(x: Int, step: Int, count: Int, lengthSpaces: Double = 3.5) {
        val stemTop = (yOf(step) - pitch * lengthSpaces).toInt()
        val sx = x + (pitch * 0.55).toInt()
        val thick = (pitch * 0.45).toInt()
        val gap = (pitch * 0.30).toInt()
        for (i in 0 until count) {
            val y = stemTop + i * (thick + gap)
            canvas.box(sx, y, sx + (pitch * 1.1).toInt(), y + thick - 1)
        }
    }

    fun dot(x: Int, step: Int, index: Int = 0) {
        val cy = if (step % 2 == 0) yOf(step + 1) else yOf(step)
        val r = pitch * 0.16
        canvas.head(x + pitch * (1.1 + index * 0.5), cy, r, r, filled = true, tiltDegrees = 0.0)
    }

    fun read(): List<NoteSymbol> {
        val image = canvas.mono()
        val scale = PageScale.estimate(image)!!
        val staff = StaffDetector.detect(image, scale).single()
        val clean = StaffLines.remove(image, listOf(staff), scale)
        return SymbolReader.read(clean, staff, HeadDetector.detect(clean, staff))
    }
}

class SymbolReaderTest {

    @Test
    fun `a semibreve has no stem and lasts four beats`() {
        val score = Score(width = 260)
        score.head(120, step = 4, filled = false)

        val note = score.read().single()

        assertEquals(null, note.stemUp)
        assertEquals(4.0, note.quarters, 0.0)
    }

    @Test
    fun `an open head with a stem is two beats`() {
        val score = Score(width = 260)
        score.head(120, step = 2, filled = false)
        score.stem(120, step = 2)

        val note = score.read().single()

        assertEquals(true, note.stemUp)
        assertEquals(0, note.beams)
        assertEquals(2.0, note.quarters, 0.0)
    }

    @Test
    fun `a plain filled head with a stem is one beat`() {
        val score = Score(width = 260)
        score.head(120, step = 2)
        score.stem(120, step = 2)

        val note = score.read().single()

        assertEquals(0, note.beams)
        assertEquals(1.0, note.quarters, 0.0)
    }

    @Test
    fun `a stem drawn downward is read as a stem`() {
        val score = Score(width = 260)
        score.head(120, step = 7)
        score.stem(120, step = 7, up = false)

        val note = score.read().single()

        assertEquals(false, note.stemUp)
        assertEquals(1.0, note.quarters, 0.0)
    }

    @Test
    fun `one beam makes quavers`() {
        val score = Score(width = 320)
        score.head(100, step = 2); score.stem(100, step = 2)
        score.head(160, step = 2); score.stem(160, step = 2)
        score.beams(from = 100, to = 160, atStep = 2, count = 1)

        val notes = score.read()

        assertEquals(2, notes.size)
        assertEquals(listOf(1, 1), notes.map { it.beams })
        assertEquals(listOf(0.5, 0.5), notes.map { it.quarters })
    }

    @Test
    fun `two beams make semiquavers`() {
        val score = Score(width = 320)
        score.head(100, step = 3); score.stem(100, step = 3)
        score.head(160, step = 3); score.stem(160, step = 3)
        score.beams(from = 100, to = 160, atStep = 3, count = 2)

        val notes = score.read()

        assertEquals(listOf(2, 2), notes.map { it.beams })
        assertEquals(listOf(0.25, 0.25), notes.map { it.quarters })
    }

    @Test
    fun `a flag counts the same as a beam`() {
        val score = Score(width = 260)
        score.head(120, step = 2); score.stem(120, step = 2)
        score.flags(120, step = 2, count = 1)

        val note = score.read().single()

        assertEquals(1, note.beams)
        assertEquals(0.5, note.quarters, 0.0)
    }

    @Test
    fun `a dot adds half the note again`() {
        val score = Score(width = 300)
        score.head(110, step = 2, filled = false)
        score.stem(110, step = 2)
        score.dot(110, step = 2)

        val note = score.read().single()

        assertEquals(1, note.dots)
        assertEquals(3.0, note.quarters, 0.0)
    }

    @Test
    fun `an undotted note is not given a dot by its neighbour`() {
        val score = Score(width = 340)
        score.head(110, step = 2); score.stem(110, step = 2)
        score.head(190, step = 4); score.stem(190, step = 4)

        val notes = score.read()

        assertEquals(listOf(0, 0), notes.map { it.dots })
    }

    @Test
    fun `a phrase of mixed lengths reads through`() {
        val score = Score(width = 640)
        // A dotted crotchet, a quaver, then a minim: the opening of countless hymns.
        score.head(90, step = 2); score.stem(90, step = 2); score.dot(90, step = 2)
        score.head(200, step = 4); score.stem(200, step = 4); score.flags(200, step = 4, count = 1)
        score.head(320, step = 5, filled = false); score.stem(320, step = 5)

        val notes = score.read()

        assertEquals(listOf(2, 4, 5), notes.map { it.step })
        assertEquals(listOf(1.5, 0.5, 2.0), notes.map { it.quarters })
    }
}
