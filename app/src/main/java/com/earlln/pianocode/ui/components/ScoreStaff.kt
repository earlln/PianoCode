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
import com.earlln.pianocode.music.omr.Clef
import com.earlln.pianocode.music.omr.TimedEvent

/** How the reading is coloured: ordinary notes, the one sounding, the one picked out. */
data class StaffTheme(
    val ink: Color,
    val playing: Color,
    val selected: Color,
    val faint: Color,
    val lyric: Color,
)

/** A word of the page's own text, placed where the page printed it. */
data class StaffWord(val text: String, val centreX: Float)

/**
 * Draws one staff of the reading back as music, laid out the way the page lays it out.
 *
 * Time was the horizontal axis here once, and it made the reading unfindable: a page of
 * four systems came out as one endless line with nothing to match against the paper in
 * your hand. Now each staff keeps its own line and every note sits at the fraction across
 * the page it was read from, so the drawing and the photograph break in the same places.
 *
 * The point of it is to be **checkable**, not to be beautiful engraving: a wrong note
 * should be obvious against the original rather than deduced from the sound.
 */
@Suppress("LongParameterList")
fun DrawScope.drawStaffLine(
    events: List<TimedEvent>,
    clef: Clef,
    theme: StaffTheme,
    space: Float,
    top: Float,
    pageToX: (Double) -> Float,
    selectedId: Int?,
    playingIds: Set<Int>,
    showNames: Boolean,
    words: List<StaffWord> = emptyList(),
) {
    val bottom = top + space * 4
    val lineWidth = (space * 0.07f).coerceAtLeast(1f)

    for (line in 0..4) {
        val y = top + line * space
        drawLine(
            color = theme.faint,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = lineWidth,
        )
    }

    for (timed in events) {
        val event = timed.event
        val x = pageToX(event.x)
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
                    y = bottom + space * 1.9f,
                    size = space * 1.0f,
                    colour = colour,
                )
            }
        }
        if (event.id == selectedId) {
            drawRect(
                color = theme.selected,
                topLeft = Offset(x - space * 1.3f, top - space * 2.2f),
                size = Size(space * 2.6f, space * 8.4f),
                style = Stroke(width = lineWidth * 2),
            )
        }
    }

    // The page's own words, under the staff they are sung to.
    for (word in words) {
        drawName(
            text = word.text,
            x = word.centreX,
            y = bottom + space * (if (showNames) 3.4f else 2.4f),
            size = space * 1.05f,
            colour = theme.lyric,
        )
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

private fun DrawScope.drawName(text: String, x: Float, y: Float, size: Float, colour: Color) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = colour.toArgb()
            textSize = size
            textAlign = android.graphics.Paint.Align.CENTER
        }
        drawText(text, x, y, paint)
    }
}

/** Where each event was drawn, so a tap can be matched back to the event it landed on. */
data class EventHit(val id: Int, val bounds: Rect)

/** What a tap on a staff landed on. */
sealed interface StaffTap {
    /** A note or rest that is already there. */
    data class OnEvent(val id: Int) : StaffTap

    /** A line or space, counted from the bottom line, with nothing written on it. */
    data class OnPosition(val step: Int) : StaffTap
}

/**
 * Reads a tap on a staff as either the note under the finger or the place it landed.
 *
 * Both readings are useful and neither needs a keyboard: tap a note to pick it out, then
 * tap the line you meant and it moves there. That is the whole repair for the commonest
 * mistake — a head read one line off — done with two taps and no typing.
 */
fun Modifier.tapStaff(
    hits: () -> List<EventHit>,
    stepAt: (Float) -> Int,
    onTap: (StaffTap) -> Unit,
): Modifier = pointerInput(Unit) {
    detectTapGestures { position ->
        val hit = hits().firstOrNull { it.bounds.contains(position) }
        onTap(
            if (hit != null) StaffTap.OnEvent(hit.id) else StaffTap.OnPosition(stepAt(position.y)),
        )
    }
}
