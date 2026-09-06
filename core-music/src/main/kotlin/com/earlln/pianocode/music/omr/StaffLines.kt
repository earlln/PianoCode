package com.earlln.pianocode.music.omr

import kotlin.math.roundToInt

/**
 * Takes the staff lines off the page and leaves everything that was sitting on them.
 *
 * Note heads, stems and beams all touch the lines, so the lines cannot simply be painted
 * over — that would cut every stem in two and put a white stripe through half the heads.
 * The test instead is local and simple: follow the line across the page a column at a
 * time and rub out only the columns where nothing but the line is present, which is what
 * a short vertical run of ink means. Where something crosses, the run is long and the ink
 * stays.
 */
object StaffLines {

    fun remove(image: MonoImage, staves: List<Staff>, scale: PageScale): MonoImage {
        val canvas = image.mutableCopy()
        // A run this long or shorter is the line and nothing else; one extra pixel of
        // slack absorbs a line that photographed a little fat or sits between two rows.
        val lineOnly = scale.lineThickness + 1
        // A little longer than that and something thin is resting on the line — the top of
        // an open head's ring, a slur, a tie. Rubbing out the whole run there takes the
        // note with the line and opens the head's hole, so only the line's own rows go.
        val lineWithSomethingOnIt = scale.lineThickness + 4
        val halfBand = scale.lineThickness / 2

        for (staff in staves) {
            for (lineCentre in staff.lineY) {
                val centre = lineCentre.roundToInt()
                for (x in staff.left..staff.right) {
                    // Find where the ink through this column starts and stops.
                    var top = centre
                    if (!canvas.isInk(x, top)) {
                        // The line can wander a pixel between columns on a photographed page.
                        top = (centre - 1..centre + 1).firstOrNull { canvas.isInk(x, it) } ?: continue
                    }
                    while (canvas.isInk(x, top - 1)) top--
                    var bottom = top
                    while (canvas.isInk(x, bottom + 1)) bottom++
                    val run = bottom - top + 1
                    if (run <= lineOnly) {
                        for (y in top..bottom) canvas.erase(x, y)
                    } else if (run <= lineWithSomethingOnIt) {
                        for (y in (centre - halfBand)..(centre + halfBand)) {
                            if (y in top..bottom) canvas.erase(x, y)
                        }
                    }
                }
            }
        }
        return canvas.frozen()
    }
}
