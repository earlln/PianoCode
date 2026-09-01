package com.earlln.pianocode.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.Fingering
import com.earlln.pianocode.music.Hand
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.Pitch
import com.earlln.pianocode.ui.theme.Amber400
import com.earlln.pianocode.ui.theme.Teal400
import com.earlln.pianocode.ui.theme.Violet500

/** What a highlighted key means, which decides its colour in the legend. */
enum class KeyRole(val color: Color, val label: String) {
    ROOT(Violet500, "근음"),
    CHORD_TONE(Teal400, "구성음"),
    TENSION(Amber400, "텐션"),
    BASS(Color(0xFFFF7597), "베이스"),
}

/** One key to paint, identified by the MIDI number it sounds. */
data class KeyHighlight(
    val midi: Int,
    val role: KeyRole,
    val label: String? = null,
    /** The finger that goes here, 1 for the thumb — null when the fingering is hidden. */
    val finger: Int? = null,
    val hand: Hand? = null,
)

/** Semitone offsets of the seven white keys inside an octave. */
private val WHITE_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/** White-key indices that have a black key immediately to their right. */
private val WHITE_WITH_BLACK_AFTER = setOf(0, 1, 3, 4, 5)

/**
 * Draws a piano keyboard with the chord's keys lit up.
 *
 * The range is chosen from the highlights themselves: it starts on the C at or below the
 * lowest key and spans whole octaves, so the picture always shows the shape in context
 * rather than cropping it.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun PianoKeyboard(
    highlights: List<KeyHighlight>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    showLabels: Boolean = true,
    showOctaveMarkers: Boolean = true,
    showHand: Boolean = false,
    handOpacity: Float = 0.55f,
    minOctaves: Int = 2,
) {
    val textMeasurer = rememberTextMeasurer()
    val range = remember(highlights, minOctaves) { keyboardRange(highlights, minOctaves) }
    val byMidi = remember(highlights) { highlights.associateBy { it.midi } }

    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            drawKeyboard(
                startMidi = range.first,
                octaves = range.second,
                highlights = byMidi,
                textMeasurer = textMeasurer,
                showLabels = showLabels,
                showOctaveMarkers = showOctaveMarkers,
                showHand = showHand,
                handOpacity = handOpacity,
            )
        }
    }
}

/** Convenience wrapper that lights up a chord's own voicing. */
@Composable
fun ChordKeyboard(
    chord: Chord,
    modifier: Modifier = Modifier,
    inversion: Int = 0,
    startOctave: Int = 3,
    height: Dp = 132.dp,
    showLabels: Boolean = true,
    showFingers: Boolean = false,
    showHand: Boolean = false,
    handOpacity: Float = 0.55f,
    minOctaves: Int = 2,
) {
    val highlights = remember(chord, inversion, startOctave, showFingers) {
        chordHighlights(chord, inversion, startOctave, showFingers)
    }
    PianoKeyboard(
        highlights = highlights,
        modifier = modifier,
        height = height,
        showLabels = showLabels,
        showHand = showHand && showFingers,
        handOpacity = handOpacity,
        minOctaves = minOctaves,
    )
}

/**
 * Turns a chord into keys to light up: the root gets its own colour, notes above the
 * seventh count as tensions, and a slash bass is marked separately.
 */
fun chordHighlights(
    chord: Chord,
    inversion: Int = 0,
    startOctave: Int = 3,
    withFingers: Boolean = false,
): List<KeyHighlight> {
    val voicing: List<Pitch> = chord.voicing(startOctave, inversion)
    val hasSlash = chord.hasSlashBass
    val tones = chord.quality.tones
    val fingering = if (withFingers) {
        Fingering.forVoicing(voicing, hasSlash).associateBy { it.midi }
    } else {
        emptyMap()
    }
    return voicing.mapIndexed { index, pitch ->
        val toneIndex = if (hasSlash) index - 1 else index
        val role = when {
            hasSlash && index == 0 -> KeyRole.BASS
            pitch.note.pitchClass == chord.root.pitchClass -> KeyRole.ROOT
            tones.getOrNull(toneIndex)?.let { it.degree > 7 } == true -> KeyRole.TENSION
            else -> KeyRole.CHORD_TONE
        }
        val placement = fingering[pitch.midi]
        KeyHighlight(pitch.midi, role, pitch.note.prettyName, placement?.finger, placement?.hand)
    }
}

/** Picks the C-to-B window that comfortably contains every highlight. */
private fun keyboardRange(highlights: List<KeyHighlight>, minOctaves: Int): Pair<Int, Int> {
    if (highlights.isEmpty()) return 48 to minOctaves
    val lowest = highlights.minOf { it.midi }
    val highest = highlights.maxOf { it.midi }
    val startMidi = lowest - Math.floorMod(lowest, 12)
    val span = highest - startMidi
    val octaves = maxOf(minOctaves, span / 12 + 1)
    return startMidi to octaves
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawKeyboard(
    startMidi: Int,
    octaves: Int,
    highlights: Map<Int, KeyHighlight>,
    textMeasurer: TextMeasurer,
    showLabels: Boolean,
    showOctaveMarkers: Boolean,
    showHand: Boolean,
    handOpacity: Float,
) {
    val whiteCount = octaves * 7
    if (whiteCount == 0) return
    val whiteWidth = size.width / whiteCount
    val whiteHeight = size.height
    val blackWidth = whiteWidth * 0.62f
    val blackHeight = whiteHeight * 0.62f
    val corner = CornerRadius(whiteWidth * 0.12f, whiteWidth * 0.12f)
    val labelSize = (whiteWidth * 0.36f).coerceIn(7f, 13f * density)
    // Gathered while the keys are drawn and used afterwards, so the hand lies over every
    // key rather than under the black ones, and the numbers sit on top of the hand.
    val fingertips = mutableListOf<Fingertip>()
    val names = mutableListOf<NameDraw>()

    // --- White keys -------------------------------------------------------
    for (index in 0 until whiteCount) {
        val midi = startMidi + (index / 7) * 12 + WHITE_SEMITONES[index % 7]
        val left = index * whiteWidth
        val highlight = highlights[midi]
        drawRoundRect(
            color = highlight?.role?.color ?: Color.White,
            topLeft = Offset(left, 0f),
            size = Size(whiteWidth, whiteHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color(0xFF6E6A78),
            topLeft = Offset(left, 0f),
            size = Size(whiteWidth, whiteHeight),
            cornerRadius = corner,
            style = Stroke(width = 1f * density),
        )
        highlight?.finger?.let { finger ->
            fingertips += Fingertip(
                finger = finger,
                hand = highlight.hand ?: Hand.RIGHT,
                center = Offset(left + whiteWidth / 2f, whiteHeight * 0.50f),
                radius = whiteWidth * 0.32f,
                keyColor = highlight.role.color,
            )
        }
        val whiteLabel = highlight?.label
        if (showLabels && whiteLabel != null) {
            // Held back until the hand is down: the palm covers the front of the keys,
            // which is where these used to sit.
            names += NameDraw(
                text = whiteLabel,
                centerX = left + whiteWidth / 2f,
                baseline = whiteHeight * if (showHand) 0.80f else 0.92f,
                fontSizePx = labelSize,
            )
        } else if (showOctaveMarkers && index % 7 == 0) {
            val octave = midi / 12 - 1
            drawKeyLabel(
                textMeasurer, "C$octave", Color(0xFF9A96A4),
                centerX = left + whiteWidth / 2f,
                baseline = whiteHeight - whiteHeight * 0.06f,
                fontSizePx = labelSize * 0.85f,
                bold = false,
            )
        }
    }

    // --- Black keys, drawn on top so they overlap their neighbours ---------
    for (index in 0 until whiteCount) {
        if (index % 7 !in WHITE_WITH_BLACK_AFTER) continue
        if (index == whiteCount - 1) continue
        val midi = startMidi + (index / 7) * 12 + WHITE_SEMITONES[index % 7] + 1
        val centerX = (index + 1) * whiteWidth
        val left = centerX - blackWidth / 2f
        val highlight = highlights[midi]
        drawRoundRect(
            color = highlight?.role?.color ?: Color(0xFF1F1B29),
            topLeft = Offset(left, 0f),
            size = Size(blackWidth, blackHeight),
            cornerRadius = corner,
        )
        highlight?.finger?.let { finger ->
            fingertips += Fingertip(
                finger = finger,
                hand = highlight.hand ?: Hand.RIGHT,
                center = Offset(centerX, blackHeight * 0.70f),
                radius = blackWidth * 0.38f,
                keyColor = highlight.role.color,
            )
        }
        if (highlight == null) {
            // A faint top edge keeps the black keys from reading as one solid block.
            drawRoundRect(
                color = Color(0xFF3A3548),
                topLeft = Offset(left, 0f),
                size = Size(blackWidth, blackHeight * 0.18f),
                cornerRadius = corner,
            )
        }
        val blackLabel = highlight?.label
        if (showLabels && blackLabel != null) {
            drawKeyLabel(
                textMeasurer, blackLabel, Color.White,
                centerX = centerX,
                baseline = blackHeight - blackHeight * 0.1f,
                fontSizePx = labelSize * 0.92f,
            )
        }
    }

    // --- The hand, then the writing on top of it --------------------------
    if (showHand) {
        fingertips.groupBy { it.hand }.forEach { (hand, spots) ->
            drawHand(spots, hand, whiteWidth, handOpacity)
        }
    }
    names.forEach { name ->
        drawKeyLabel(
            textMeasurer, name.text, Color.White,
            centerX = name.centerX,
            baseline = name.baseline,
            fontSizePx = name.fontSizePx,
        )
    }
    fingertips.forEach { tip ->
        drawFinger(
            textMeasurer, tip.finger, tip.hand, tip.keyColor,
            centerX = tip.center.x,
            centerY = tip.center.y,
            radius = tip.radius,
        )
    }
}

/** A note name waiting to be written, once the hand is out of its way. */
private data class NameDraw(
    val text: String,
    val centerX: Float,
    val baseline: Float,
    val fontSizePx: Float,
)

/** A key that is going to be pressed, and where its finger lands on the picture. */
private data class Fingertip(
    val finger: Int,
    val hand: Hand,
    val center: Offset,
    val radius: Float,
    val keyColor: Color,
)

/**
 * Lays a hand over the keys it presses.
 *
 * Numbers alone say which finger without saying what the hand does — whether it sits square
 * or reaches, where the thumb tucks, how far the span is. Drawing the shape answers that in
 * one look.
 *
 * The player sits at the near edge, which on a keyboard drawn from above is the bottom of
 * the picture. So the wrist comes in from below, the knuckles sit across the front of the
 * keys, and the fingers reach away from the reader towards the back — the way their own
 * hand will look when they put it down.
 */
private fun DrawScope.drawHand(
    spots: List<Fingertip>,
    hand: Hand,
    whiteWidth: Float,
    opacity: Float,
) {
    if (spots.isEmpty()) return
    val ordered = spots.sortedBy { it.center.x }
    val thumb = ordered.firstOrNull { it.finger == 1 }
    val longFingers = ordered.filter { it.finger != 1 }
    if (longFingers.isEmpty()) return

    val skin = Color(0xFFD8A784)
    val edge = Color(0xFF8A5F42)
    val knuckleY = size.height * 0.88f
    // The wrist runs off the bottom of the picture, the way a hand does off the near edge
    // of a real keyboard.
    val wristY = size.height * 1.14f
    val palmCentre = longFingers.map { it.center.x }.average().toFloat()
    val baseWidth = (whiteWidth * 0.46f).coerceAtLeast(4f * density)
    val tipWidth = baseWidth * 0.66f

    /** Where a finger leaves the hand: pulled towards the middle, and arched a little. */
    fun knuckleOf(spot: Fingertip): Offset {
        val x = spot.center.x + (palmCentre - spot.center.x) * 0.30f
        val fromCentre = ((x - palmCentre) / (whiteWidth * 3f)).coerceIn(-1f, 1f)
        return Offset(x, knuckleY + fromCentre * fromCentre * baseWidth * 0.5f)
    }

    // --- Palm, drawn first so the fingers sit on top of it -----------------
    val leftBase = knuckleOf(longFingers.first())
    val rightBase = knuckleOf(longFingers.last())
    val palm = Path().apply {
        moveTo(leftBase.x - baseWidth * 0.62f, leftBase.y)
        lineTo(rightBase.x + baseWidth * 0.62f, rightBase.y)
        // The heel of the hand narrows towards the wrist.
        quadraticBezierTo(
            rightBase.x + baseWidth * 0.30f, wristY - baseWidth,
            palmCentre + baseWidth * 0.95f, wristY,
        )
        lineTo(palmCentre - baseWidth * 0.95f, wristY)
        quadraticBezierTo(
            leftBase.x - baseWidth * 0.30f, wristY - baseWidth,
            leftBase.x - baseWidth * 0.62f, leftBase.y,
        )
        close()
    }
    drawPath(palm, skin.copy(alpha = opacity))
    drawPath(palm, edge.copy(alpha = opacity * 0.7f), style = Stroke(width = 1.2f * density))

    // --- The thumb, which comes in from the side of the hand ---------------
    if (thumb != null) {
        val fromLeft = hand == Hand.RIGHT
        val anchor = Offset(
            palmCentre + if (fromLeft) -baseWidth * 0.9f else baseWidth * 0.9f,
            wristY - baseWidth * 1.9f,
        )
        drawDigit(anchor, thumb.center, baseWidth * 1.15f, tipWidth * 1.1f, skin, edge, opacity)
    }

    // --- The four long fingers --------------------------------------------
    for (spot in longFingers) {
        drawDigit(knuckleOf(spot), spot.center, baseWidth, tipWidth, skin, edge, opacity)
    }
}

/**
 * One finger: a tapered shape with a rounded end, rather than a stick.
 *
 * The taper is what makes it read as a finger — a line of even thickness reads as a wire no
 * matter how it is coloured.
 */
private fun DrawScope.drawDigit(
    base: Offset,
    tip: Offset,
    baseWidth: Float,
    tipWidth: Float,
    skin: Color,
    edge: Color,
    opacity: Float,
) {
    val dx = tip.x - base.x
    val dy = tip.y - base.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 0.5f) return
    val ux = dx / length
    val uy = dy / length
    // Perpendicular to the finger, for its two sides.
    val px = -uy
    val py = ux
    val baseHalf = baseWidth / 2f
    val tipHalf = tipWidth / 2f
    // The fingertip rounds off just past the key it is on.
    val overshootX = ux * tipHalf
    val overshootY = uy * tipHalf

    val path = Path().apply {
        moveTo(base.x + px * baseHalf, base.y + py * baseHalf)
        lineTo(tip.x + px * tipHalf, tip.y + py * tipHalf)
        quadraticBezierTo(
            tip.x + px * tipHalf + overshootX * 1.2f,
            tip.y + py * tipHalf + overshootY * 1.2f,
            tip.x + overshootX * 1.2f,
            tip.y + overshootY * 1.2f,
        )
        quadraticBezierTo(
            tip.x - px * tipHalf + overshootX * 1.2f,
            tip.y - py * tipHalf + overshootY * 1.2f,
            tip.x - px * tipHalf,
            tip.y - py * tipHalf,
        )
        lineTo(base.x - px * baseHalf, base.y - py * baseHalf)
        close()
    }
    drawPath(path, skin.copy(alpha = opacity))
    drawPath(path, edge.copy(alpha = opacity * 0.7f), style = Stroke(width = 1.2f * density))
}

/**
 * Walks the inversions as the keyboard is dragged sideways.
 *
 * One step per quarter of the width, so a long drag walks several positions instead of
 * snapping back to one, and the gesture is claimed only once it is clearly horizontal —
 * the pictures sit in vertically scrolling lists, which must keep working over them.
 */
fun Modifier.swipeInversions(
    positionCount: Int,
    inversion: Int,
    onInversion: (Int) -> Unit,
): Modifier = if (positionCount <= 1) {
    this
} else {
    this.pointerInput(positionCount) {
        var travelled = 0f
        var position = inversion
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = { travelled = 0f },
            onDragCancel = { travelled = 0f },
        ) { change, amount ->
            change.consume()
            travelled += amount
            val step = size.width / 4f
            while (travelled <= -step) {
                travelled += step
                position = (position + 1) % positionCount
                onInversion(position)
            }
            while (travelled >= step) {
                travelled -= step
                position = (position - 1 + positionCount) % positionCount
                onInversion(position)
            }
        }
    }
}
