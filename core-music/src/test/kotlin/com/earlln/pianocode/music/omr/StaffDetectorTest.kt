package com.earlln.pianocode.music.omr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PageScaleTest {

    @Test
    fun `the scale comes back off a plain staff`() {
        val canvas = SheetCanvas(400, 160)
        canvas.staff(topY = 40, left = 20, right = 380, space = 10, thickness = 2)

        val scale = PageScale.estimate(canvas.mono())

        assertNotNull(scale)
        assertEquals(2, scale!!.lineThickness)
        assertEquals(10, scale.space)
    }

    @Test
    fun `a blank page has no scale to give`() {
        assertEquals(null, PageScale.estimate(SheetCanvas(80, 80).mono()))
    }

    @Test
    fun `note heads and stems do not shift the estimate`() {
        val canvas = SheetCanvas(400, 160)
        canvas.staff(topY = 40, left = 20, right = 380, space = 10, thickness = 2)
        for (i in 0 until 6) {
            val cx = 60.0 + i * 50
            canvas.head(cx, 64.0, 7.0, 5.0, filled = true)
            canvas.vLine((cx + 6).toInt(), 24, 64, thickness = 2)
        }

        val scale = PageScale.estimate(canvas.mono())

        assertEquals(10, scale!!.space)
    }
}

class StaffDetectorTest {

    @Test
    fun `one staff is found with its five lines in place`() {
        val canvas = SheetCanvas(400, 160)
        canvas.staff(topY = 40, left = 20, right = 380, space = 10, thickness = 2)
        val image = canvas.mono()

        val staves = StaffDetector.detect(image, PageScale.estimate(image)!!)

        assertEquals(1, staves.size)
        val staff = staves.single()
        assertEquals(5, staff.lineY.size)
        // Lines were drawn two pixels thick, so a centre lands half a pixel below the top.
        assertTrue("top line at ${staff.top}", abs(staff.top - 40.5) <= 1.0)
        assertTrue("space of ${staff.space}", abs(staff.space - 12.0) <= 1.0)
        assertTrue(staff.left <= 22 && staff.right >= 378)
    }

    @Test
    fun `two systems on a page come back as two staves`() {
        val canvas = SheetCanvas(400, 300)
        canvas.staff(topY = 40, left = 20, right = 380, space = 10, thickness = 2)
        canvas.staff(topY = 190, left = 20, right = 380, space = 10, thickness = 2)
        val image = canvas.mono()

        val staves = StaffDetector.detect(image, PageScale.estimate(image)!!)

        assertEquals(2, staves.size)
        assertTrue(staves[0].top < staves[1].top)
        assertTrue(abs(staves[1].top - 190.5) <= 1.0)
    }

    @Test
    fun `a line of text above the staff is not mistaken for a staff line`() {
        val canvas = SheetCanvas(400, 200)
        // Chord symbols: short marks in a row, dense but nowhere near staff-line long.
        for (i in 0 until 12) canvas.box(20 + i * 30, 14, 20 + i * 30 + 8, 26)
        canvas.staff(topY = 60, left = 20, right = 380, space = 10, thickness = 2)
        val image = canvas.mono()

        val staves = StaffDetector.detect(image, PageScale.estimate(image)!!)

        assertEquals(1, staves.size)
        assertTrue(staves.single().top > 50)
    }

    @Test
    fun `an unevenly lit photograph still gives up its staff`() {
        val canvas = SheetCanvas(400, 200)
        canvas.staff(topY = 60, left = 20, right = 380, space = 10, thickness = 2)
        // The phone's own shadow over the right third of the page.
        canvas.shade(260, 0, 399, 199, by = 150)
        val image = canvas.mono()

        val staves = StaffDetector.detect(image, PageScale.estimate(image)!!)

        assertEquals(1, staves.size)
        assertTrue("right edge ${staves.single().right}", staves.single().right >= 370)
    }

    @Test
    fun `steps are counted from the bottom line upward`() {
        val staff = Staff(lineY = listOf(40.0, 52.0, 64.0, 76.0, 88.0), left = 0, right = 100)

        assertEquals(0, staff.stepOf(88.0))   // bottom line
        assertEquals(1, staff.stepOf(82.0))   // the space above it
        assertEquals(2, staff.stepOf(76.0))   // second line up
        assertEquals(8, staff.stepOf(40.0))   // top line
        assertEquals(-2, staff.stepOf(100.0)) // first ledger line below
        assertEquals(76.0, staff.yOfStep(2), 0.001)
    }
}
