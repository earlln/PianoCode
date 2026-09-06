package com.earlln.pianocode.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.music.omr.Clef
import com.earlln.pianocode.music.omr.TimedEvent

/** How the reading is coloured: ordinary notes, the one sounding, the one picked out. */
data class StaffTheme(
    val ink: Color,
    val playing: Color,
    val selected: Color,
    val faint: Color,
)

/**
 * Draws the reading back as music.
 *
 * The point of this is not to be beautiful engraving — it is to be **checkable**. Somebody
 * whose playback sounded wrong needs to see, against the page in their hand, which note
 * the app thinks is there; so every note is drawn where it was read, in the order it will
 * be played, with its name under it. A wrong note is then obvious rather than deduced.
 *
 * Time is the horizontal axis, which is what lets the playhead sweep across it: an event
 * twice as long takes twice the width, so the sweep matches what is heard.
 */
@Suppress("LongParameterList")
fun DrawScope.drawStaffLine(
    events: List<TimedEvent>,
    clef: Clef,
    theme: StaffTheme,
    space: Float,
    left: Float,
    top: Float,
    quartersToX: (Double) -> Float,
    selectedId: Int?,
    playingIds: Set<Int>,
    showNames: Boolean,
) {
    val bottom = top + space * 4
    val lineWidth = (space * 0.07f).coerceAtLeast(1f)

    // The five lines run the whole width, as they do on a page.
    for (line in 0..4) {
        val y = top + line * space
        drawLine(
            color = theme.faint,
            start = Offset(left, y),
            end = Offset(size.width, y),
            strokeWidth = lineWidth,
        )
    }

    for (timed in events) {
        val event = timed.event
        val x = quartersToX(timed.startQuarters)
        val colour = when {
            event.id in playingIds -> theme.playing
            event.id == selectedId -> theme.selected
            else -> theme.ink
        }
        if (event.isRest) {
            drawRest(event.quarters, x, top, space, colour)
        } else {
            for (pitch in event.pitches) {
                drawNote(
                    step = clef.stepOf(pitch),
                    quarters = event.quarters,
                    x = x,
                    bottom = bottom,
                    space = space,
                    colour = colour,
                    lineWidth = lineWidth,
                )
            }
            if (showNames) {
                drawName(
                    text = event.pitches.joinToString("") { it.note.prettyName },
                    x = x,
                    y = bottom + space * 2.4f,
                    space = space,
                    colour = colour,
                )
            }
        }
        if (event.id == selectedId) {
            drawRect(
                color = theme.selected,
                topLeft = Offset(x - space * 1.1f, top - space * 2.2f),
                size = Size(space * 2.2f, space * 8.4f),
                style = Stroke(width = lineWidth * 2),
            )
        }
    }
}

private fun DrawScope.drawNote(
    step: Int,
    quarters: Double,
    x: Float,
    bottom: Float,
    space: Float,
    colour: Color,
    lineWidth: Float,
) {
    val y = bottom - step * (space / 2f)
    val rx = space * 0.62f
    val ry = space * 0.46f

    // Ledger lines, drawn only as far as the note actually reaches.
    if (step < 0) {
        var line = -2
        while (line >= step) {
            val ly = bottom - line * (space / 2f)
            drawLine(colour, Offset(x - rx * 1.5f, ly), Offset(x + rx * 1.5f, ly), lineWidth)
            line -= 2
        }
    } else if (step > 8) {
        var line = 10
        while (line <= step) {
            val ly = bottom - line * (space / 2f)
            drawLine(colour, Offset(x - rx * 1.5f, ly), Offset(x + rx * 1.5f, ly), lineWidth)
            line += 2
        }
    }

    val filled = quarters < 2.0
    translate(x, y) {
        rotate(-20f, Offset.Zero) {
            drawOval(
                color = colour,
                topLeft = Offset(-rx, -ry),
                size = Size(rx * 2, ry * 2),
                style = if (filled) Fill else Stroke(width = space * 0.16f),
            )
        }
    }

    if (quarters >= 4.0) return  // a semibreve has no stem

    // Stems point away from the middle of the staff, as engraving has them.
    val up = step < 4
    val stemX = if (up) x + rx * 0.92f else x - rx * 0.92f
    val stemEnd = if (up) y - space * 3.2f else y + space * 3.2f
    drawLine(colour, Offset(stemX, y), Offset(stemX, stemEnd), lineWidth * 1.6f)

    val flags = when {
        quarters <= 0.25 -> 2
        quarters <= 0.75 -> 1
        else -> 0
    }
    for (i in 0 until flags) {
        val fy = stemEnd + (if (up) 1 else -1) * i * space * 0.6f
        drawLine(
            colour,
            Offset(stemX, fy),
            Offset(stemX + rx * 1.3f, fy + (if (up) 1 else -1) * space * 0.9f),
            lineWidth * 2.4f,
        )
    }

    // A dotted note gets its dot in the space beside the head, as it is printed.
    if (quarters == 3.0 || quarters == 1.5 || quarters == 0.75) {
        val dotY = if (step % 2 == 0) y - space / 2f else y
        drawCircle(colour, radius = space * 0.16f, center = Offset(x + rx * 1.9f, dotY))
    }
}

/**
 * Rests, drawn the way they are printed rather than the way they are shaped.
 *
 * The brick for a semibreve hangs under its line and the one for a minim sits on top of
 * one — the same difference the reader has to measure to tell the two apart, so drawing it
 * faithfully is what lets a reader confirm it got that right.
 */
private fun DrawScope.drawRest(quarters: Double, x: Float, top: Float, space: Float, colour: Color) {
    val brickWidth = space * 0.9f
    val brickHeight = space * 0.42f
    when {
        quarters >= 4.0 -> drawRect(
            colour,
            topLeft = Offset(x - brickWidth / 2, top + space),
            size = Size(brickWidth, brickHeight),
        )

        quarters >= 2.0 -> drawRect(
            colour,
            topLeft = Offset(x - brickWidth / 2, top + space * 2 - brickHeight),
            size = Size(brickWidth, brickHeight),
        )

        else -> {
            // A zig-zag for a crotchet rest, one hook fewer for each halving.
            val height = if (quarters >= 1.0) space * 2.6f else space * 1.8f
            val startY = top + space * 2 - height / 2
            var y = startY
            var atX = x - space * 0.3f
            var direction = 1
            while (y < startY + height) {
                val toX = atX + direction * space * 0.55f
                drawLine(colour, Offset(atX, y), Offset(toX, y + space * 0.55f), space * 0.16f)
                atX = toX
                y += space * 0.55f
                direction = -direction
            }
        }
    }
}

private fun DrawScope.drawName(text: String, x: Float, y: Float, space: Float, colour: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = colour.toArgb()
            textSize = space * 1.1f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(text, x, y, paint)
    }
}

/** Where each event was drawn, so a tap can be matched back to the event it landed on. */
data class EventHit(val id: Int, val bounds: Rect)

/** Turns a tap into the event nearest it, when the tap is close enough to mean one. */
fun Modifier.tapEvents(hits: () -> List<EventHit>, onEvent: (Int?) -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures { position ->
            val hit = hits().firstOrNull { it.bounds.contains(position) }
            onEvent(hit?.id)
        }
    }

/** A comfortable staff size for a phone: big enough to read a ledger note at a glance. */
val StaffSpace: Dp = 11.dp
