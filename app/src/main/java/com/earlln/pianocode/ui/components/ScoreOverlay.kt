package com.earlln.pianocode.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.earlln.pianocode.music.omr.ReadStaff
import com.earlln.pianocode.music.omr.ScoreEvent
import kotlin.math.abs

/** How the reading is marked over the page it was read from. */
data class OverlayTheme(
    val read: Color,
    val playing: Color,
    val selected: Color,
    val changed: Color,
    val erase: Color,
)

/**
 * Maps between the photograph's own coordinates and the screen.
 *
 * The page is the thing being looked at, so it keeps its own coordinates and the screen
 * follows them. Every position the reader worked in — a note's x, a staff line's y — is
 * already in these, so nothing has to be re-measured when the view is zoomed or moved.
 */
data class PageView(
    /** Screen pixels per page pixel at rest, so the page fills the width. */
    val fit: Float,
    val scale: Float,
    val offset: Offset,
) {
    val pixels: Float get() = fit * scale

    fun toScreen(x: Double, y: Double): Offset =
        Offset(x.toFloat() * pixels + offset.x, y.toFloat() * pixels + offset.y)

    fun toPage(point: Offset): Offset =
        Offset((point.x - offset.x) / pixels, (point.y - offset.y) / pixels)
}

/**
 * Draws what the app read on top of the page it read it from.
 *
 * Redrawing the music from scratch was never going to match the original — the clef, the
 * key signature, the words, the engraver's spacing are all things the reader does not
 * model and could only approximate. Keeping the photograph as the surface makes the
 * likeness exact and turns a wrong reading into something plainly visible: a mark that
 * does not sit on the note it is meant to be.
 *
 * Only a note the reader has **changed** paints over the page, and then only across the
 * head it replaces. Everything else leaves the original untouched.
 */
@Suppress("LongParameterList")
fun DrawScope.drawReadingOverlay(
    events: List<ScoreEvent>,
    staves: List<ReadStaff>,
    view: PageView,
    theme: OverlayTheme,
    selectedId: Int?,
    playingIds: Set<Int>,
    changedIds: Set<Int>,
    showNames: Boolean,
) {
    for (event in events) {
        val staff = staves.getOrNull(event.staffIndex) ?: continue
        val space = (staff.staff.space * view.pixels).toFloat()
        if (space < 2f) continue

        val colour = when {
            event.id in playingIds -> theme.playing
            event.id == selectedId -> theme.selected
            event.id in changedIds -> theme.changed
            else -> theme.read
        }

        // A changed note's old head is painted out, so the page shows the correction
        // rather than both readings at once.
        if (event.id in changedIds) {
            val old = view.toScreen(event.x, event.y)
            drawRect(
                color = theme.erase,
                topLeft = Offset(old.x - space * 0.85f, old.y - space * 0.7f),
                size = Size(space * 1.7f, space * 1.4f),
            )
        }

        if (event.isRest) {
            val middle = view.toScreen(event.x, staff.staff.yOfStep(4))
            drawRect(
                color = colour,
                topLeft = Offset(middle.x - space * 0.45f, middle.y - space * 0.25f),
                size = Size(space * 0.9f, space * 0.5f),
                style = Stroke(width = space * 0.16f),
            )
            continue
        }

        for (pitch in event.pitches) {
            val step = staff.clef.stepOf(pitch)
            val centre = view.toScreen(event.x, staff.staff.yOfStep(step))
            val rx = space * 0.62f
            val ry = space * 0.46f
            drawOval(
                color = colour,
                topLeft = Offset(centre.x - rx, centre.y - ry),
                size = Size(rx * 2, ry * 2),
                style = if (event.id == selectedId || event.id in playingIds) {
                    Fill
                } else {
                    Stroke(width = space * 0.18f)
                },
                alpha = if (event.id == selectedId || event.id in playingIds) 0.55f else 0.9f,
            )
            if (showNames && space > 9f) {
                drawLabel(
                    text = pitch.note.prettyName,
                    at = Offset(centre.x, centre.y - space * 1.0f),
                    size = space * 1.0f,
                    colour = colour,
                )
            }
        }

        if (event.id == selectedId) {
            val top = view.toScreen(event.x, staff.staff.top)
            val bottom = view.toScreen(event.x, staff.staff.bottom)
            drawRect(
                color = theme.selected,
                topLeft = Offset(top.x - space * 1.4f, top.y - space * 2.2f),
                size = Size(space * 2.8f, bottom.y - top.y + space * 4.4f),
                style = Stroke(width = space * 0.12f),
            )
        }
    }
}

private fun DrawScope.drawLabel(text: String, at: Offset, size: Float, colour: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = colour.toArgb()
            textSize = size
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawText(text, at.x, at.y, paint)
    }
}

/** What a tap on the page turned out to mean. */
sealed interface PageTap {
    /** A note or rest the app has already marked. */
    data class OnEvent(val id: Int) : PageTap

    /** A place on a staff, given as the staff and the position counted up its lines. */
    data class OnPosition(val staffIndex: Int, val step: Int) : PageTap

    /** Somewhere with no staff under it. */
    data object Elsewhere : PageTap
}

/**
 * Works out what a tap on the page landed on.
 *
 * A note first, if the finger came near one — the reach is measured in staff spaces, so it
 * stays the same size against the music however far the page is zoomed. Failing that, the
 * staff position under the finger, which is what moves a selected note to where it belongs.
 */
object PageTaps {

    fun at(
        point: Offset,
        view: PageView,
        events: List<ScoreEvent>,
        staves: List<ReadStaff>,
    ): PageTap {
        val page = view.toPage(point)
        val space = staves.firstOrNull()?.staff?.space ?: return PageTap.Elsewhere

        val near = events
            .filter { abs(it.x - page.x) < space * 1.4 }
            .minByOrNull { abs(it.x - page.x) + abs(centreY(it, staves) - page.y) / 4 }
        if (near != null && abs(near.x - page.x) < space * 1.2) return PageTap.OnEvent(near.id)

        val staffIndex = staves.indexOfFirst { it.staff.covers(page.y.toDouble(), ledgerSteps = 6) }
        if (staffIndex < 0) return PageTap.Elsewhere
        return PageTap.OnPosition(staffIndex, staves[staffIndex].staff.stepOf(page.y.toDouble()))
    }

    private fun centreY(event: ScoreEvent, staves: List<ReadStaff>): Double {
        val staff = staves.getOrNull(event.staffIndex) ?: return event.y
        if (event.isRest) return staff.staff.yOfStep(4)
        val steps = event.pitches.map { staff.clef.stepOf(it) }
        return staff.staff.yOfStep(steps.average().toInt())
    }
}

/** Where on the page a note sits, for bringing it into view when it is picked out. */
fun eventCentre(event: ScoreEvent, staves: List<ReadStaff>): Offset {
    val staff = staves.getOrNull(event.staffIndex)
        ?: return Offset(event.x.toFloat(), event.y.toFloat())
    val y = if (event.isRest) {
        staff.staff.yOfStep(4)
    } else {
        staff.staff.yOfStep(staff.clef.stepOf(event.pitches.first()))
    }
    return Offset(event.x.toFloat(), y.toFloat())
}
