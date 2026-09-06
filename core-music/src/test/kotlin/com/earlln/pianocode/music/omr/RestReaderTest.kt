package com.earlln.pianocode.music.omr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RestPage(val width: Int = 620, val height: Int = 240) {
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

    fun note(x: Int, step: Int) {
        canvas.head(x.toDouble(), yOf(step), pitch * 0.62, pitch * 0.47, filled = true)
        canvas.vLine(x + (pitch * 0.55).toInt(), (yOf(step) - pitch * 3.5).toInt(), yOf(step).toInt(), 2)
    }

    /** Hangs under the second line from the top, as a semibreve rest does. */
    fun wholeRest(x: Int) = canvas.brickRest(x.toDouble(), yOf(6), pitch, hanging = true)

    /** Sits on the middle line, as a minim rest does. */
    fun halfRest(x: Int) = canvas.brickRest(x.toDouble(), yOf(4), pitch, hanging = false)

    fun quarterRest(x: Int) = canvas.crotchetRest(x.toDouble(), yOf(4), pitch)

    fun eighthRest(x: Int) = canvas.quaverRest(x.toDouble(), yOf(4), pitch)

    fun barLine(x: Int) = canvas.vLine(x, topY, bottomY.toInt(), thickness = 2)

    fun rests(): List<Rest> {
        val image = canvas.mono()
        val scale = PageScale.estimate(image)!!
        val staff = StaffDetector.detect(image, scale).single()
        val clean = StaffLines.remove(image, listOf(staff), scale)
        val heads = HeadDetector.detect(clean, staff, fromX = 60)
        return RestReader.detect(clean, staff, fromX = 60, heads = heads)
    }
}

class RestReaderTest {

    @Test
    fun `a semibreve rest is four beats of silence`() {
        val page = RestPage(width = 320)
        page.wholeRest(160)

        val rests = page.rests()

        assertEquals(1, rests.size)
        assertEquals(4.0, rests.single().quarters, 0.0)
    }

    @Test
    fun `a minim rest is two, told apart only by where it sits`() {
        val page = RestPage(width = 320)
        page.halfRest(160)

        val rests = page.rests()

        assertEquals(1, rests.size)
        assertEquals(2.0, rests.single().quarters, 0.0)
    }

    @Test
    fun `a crotchet rest is one beat`() {
        val page = RestPage(width = 320)
        page.quarterRest(160)

        val rests = page.rests()

        assertEquals(listOf(1.0), rests.map { it.quarters })
    }

    @Test
    fun `a quaver rest is half a beat`() {
        val page = RestPage(width = 320)
        page.eighthRest(160)

        val rests = page.rests()

        assertEquals(listOf(0.5), rests.map { it.quarters })
    }

    @Test
    fun `a bar line is not a silence`() {
        val page = RestPage(width = 400)
        page.barLine(200)

        assertTrue(page.rests().isEmpty())
    }

    @Test
    fun `a note and its stem are not a silence`() {
        val page = RestPage(width = 400)
        page.note(150, 2)
        page.note(260, 5)

        assertTrue(page.rests().isEmpty())
    }

    @Test
    fun `rests and notes are found side by side`() {
        val page = RestPage(width = 560)
        page.note(140, 2)
        page.quarterRest(240)
        page.note(340, 4)
        page.halfRest(440)

        val rests = page.rests()

        assertEquals(listOf(1.0, 2.0), rests.map { it.quarters })
    }
}

class RestsInTimeTest {

    @Test
    fun `a silence takes its time and everything after it waits`() {
        val page = RestPage(width = 700)
        page.canvas.trebleClef(40.0, page.topY.toDouble(), page.yOf(0), page.pitch)
        page.note(200, 2)
        page.quarterRest(300)
        page.note(400, 4)

        val score = ScoreReader.read(page.canvas.mono())

        assertEquals(listOf("G4", "B4"), score.notes.map { it.pitch.toString() })
        // The second note waits out the beat of silence rather than following straight on.
        assertEquals(listOf(0.0, 2.0), score.notes.map { it.startQuarters })
        assertEquals(3.0, score.lengthQuarters, 0.0)
    }
}
