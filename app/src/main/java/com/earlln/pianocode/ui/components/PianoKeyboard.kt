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
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
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
    handOpacity: Float = 0.92f,
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
    handOpacity: Float = 0.92f,
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
            onHand = showHand,
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
 * the picture. So the wrist comes in from below, the knuckles lie across the front of the
 * keys, and the fingers reach away towards the back — the way their own hand will look when
 * they put it down.
 *
 * Palm and digits are unioned into one outline before anything is drawn, so the hand has a
 * single edge around it rather than seams where a finger meets the palm.
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

    val knuckleY = size.height * 0.88f
    // The wrist runs off the bottom of the picture, the way a hand does off the near edge
    // of a real keyboard.
    val wristY = size.height * 1.16f
    val palmCentre = longFingers.map { it.center.x }.average().toFloat()
    val baseWidth = (whiteWidth * 0.54f).coerceAtLeast(5f * density)
    val tipWidth = baseWidth * 0.80f

    /** Where a finger leaves the hand: pulled towards the middle, and arched a little. */
    fun knuckleOf(spot: Fingertip): Offset {
        val x = spot.center.x + (palmCentre - spot.center.x) * 0.26f
        val fromCentre = ((x - palmCentre) / (whiteWidth * 3f)).coerceIn(-1f, 1f)
        return Offset(x, knuckleY + fromCentre * fromCentre * baseWidth * 0.55f)
    }

    val thumbAnchor = thumb?.let {
        Offset(
            palmCentre + if (hand == Hand.RIGHT) -baseWidth * 0.75f else baseWidth * 0.75f,
            wristY - baseWidth * 2.1f,
        )
    }

    // --- Palm ---------------------------------------------------------------
    val leftBase = knuckleOf(longFingers.first())
    val rightBase = knuckleOf(longFingers.last())
    val palm = Path().apply {
        moveTo(leftBase.x - baseWidth * 0.55f, leftBase.y + baseWidth * 0.1f)
        lineTo(rightBase.x + baseWidth * 0.55f, rightBase.y + baseWidth * 0.1f)
        // The heel of the hand rounds out and then narrows towards the wrist.
        quadraticBezierTo(
            rightBase.x + baseWidth * 0.85f, wristY - baseWidth * 1.6f,
            palmCentre + baseWidth * 1.05f, wristY,
        )
        lineTo(palmCentre - baseWidth * 1.05f, wristY)
        quadraticBezierTo(
            leftBase.x - baseWidth * 0.85f, wristY - baseWidth * 1.6f,
            leftBase.x - baseWidth * 0.55f, leftBase.y + baseWidth * 0.1f,
        )
        close()
    }

    // --- One silhouette, so no seam shows where a finger joins the palm -----
    var outline = palm
    val digits = buildList {
        thumbAnchor?.let { anchor ->
            add(digitPath(anchor, thumb!!.center, baseWidth * 1.2f, tipWidth * 1.15f))
        }
        longFingers.forEach { add(digitPath(knuckleOf(it), it.center, baseWidth, tipWidth)) }
    }
    digits.forEach { digit ->
        outline = Path().also { it.op(outline, digit, PathOperation.Union) }
    }

    drawPath(outline, SKIN.copy(alpha = opacity))
    drawPath(
        outline,
        SKIN_EDGE.copy(alpha = opacity),
        style = Stroke(width = 1.8f * density),
    )

    // --- Nails, which is most of what makes it read as a hand ---------------
    val nailSpots = buildList {
        thumbAnchor?.let { add(Triple(it, thumb!!.center, tipWidth * 1.15f)) }
        longFingers.forEach { add(Triple(knuckleOf(it), it.center, tipWidth)) }
    }
    for ((base, tip, width) in nailSpots) {
        val dx = tip.x - base.x
        val dy = tip.y - base.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 1f) continue
        val nailWidth = width * 0.62f
        val nailHeight = width * 0.78f
        val centre = Offset(tip.x - dx / length * width * 0.16f, tip.y - dy / length * width * 0.16f)
        val degrees = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
        rotate(degrees = degrees, pivot = centre) {
            val topLeft = Offset(centre.x - nailWidth / 2f, centre.y - nailHeight / 2f)
            val nailSize = Size(nailWidth, nailHeight)
            drawOval(NAIL.copy(alpha = opacity), topLeft, nailSize)
            drawOval(
                SKIN_EDGE.copy(alpha = opacity * 0.75f),
                topLeft,
                nailSize,
                style = Stroke(width = 1.1f * density),
            )
        }
    }
}

/**
 * One finger: tapered from root to tip and rounded off at the end.
 *
 * The taper is what makes it read as a finger — a bar of even thickness reads as wire no
 * matter how it is coloured.
 */
private fun digitPath(base: Offset, tip: Offset, baseWidth: Float, tipWidth: Float): Path {
    val dx = tip.x - base.x
    val dy = tip.y - base.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)
    if (length < 0.5f) return Path()
    val ux = dx / length
    val uy = dy / length
    // Perpendicular to the finger, for its two sides.
    val px = -uy
    val py = ux
    val baseHalf = baseWidth / 2f
    val tipHalf = tipWidth / 2f
    // How far past the key the fingertip rounds off. 4/3 of the radius puts the curve very
    // near a true semicircle.
    val overX = ux * tipHalf * 1.33f
    val overY = uy * tipHalf * 1.33f

    return Path().apply {
        moveTo(base.x + px * baseHalf, base.y + py * baseHalf)
        lineTo(tip.x + px * tipHalf, tip.y + py * tipHalf)
        quadraticBezierTo(
            tip.x + px * tipHalf + overX, tip.y + py * tipHalf + overY,
            tip.x + overX, tip.y + overY,
        )
        quadraticBezierTo(
            tip.x - px * tipHalf + overX, tip.y - py * tipHalf + overY,
            tip.x - px * tipHalf, tip.y - py * tipHalf,
        )
        lineTo(base.x - px * baseHalf, base.y - py * baseHalf)
        close()
    }
}

/** Cartoon hand colouring: pale fill, dark edge, a slightly warmer nail. */
private val SKIN = Color(0xFFFBE6CE)
private val SKIN_EDGE = Color(0xFF2B2118)
private val NAIL = Color(0xFFF6D2B6)

/** Fingering numbers are written in red, the way they are printed on a score. */
private val FINGER_RED = Color(0xFFD32F2F)

/**
 * Writes the finger that goes on a key.
 *
 * With a hand drawn, the number goes on the finger itself in red, the way fingering is
 * printed on a score — no disc needed, since the pale finger is already a background.
 * Without one it needs its own: a lit key is a strong colour, and a bare number on it is
 * hard to read. There the left hand's disc is filled dark, so a slash bass reads as the
 * other hand at a glance rather than having to be counted.
 */
@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawFinger(
    textMeasurer: TextMeasurer,
    finger: Int,
    hand: Hand?,
    keyColor: Color,
    centerX: Float,
    centerY: Float,
    radius: Float,
    onHand: Boolean,
) {
    if (onHand) {
        drawKeyLabel(
            textMeasurer,
            finger.toString(),
            FINGER_RED,
            centerX = centerX,
            baseline = centerY + radius * 2.4f,
            fontSizePx = radius * 1.5f,
        )
        return
    }

    val leftHand = hand == Hand.LEFT
    drawCircle(
        color = if (leftHand) Color(0xFF241F30) else Color.White,
        radius = radius,
        center = Offset(centerX, centerY),
    )
    drawCircle(
        color = Color.White,
        radius = radius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1.4f * density),
    )
    drawKeyLabel(
        textMeasurer,
        finger.toString(),
        if (leftHand) Color.White else keyColor,
        centerX = centerX,
        baseline = centerY + radius * 0.58f,
        fontSizePx = radius * 1.25f,
    )
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawKeyLabel(
    textMeasurer: TextMeasurer,
    text: String,
    color: Color,
    centerX: Float,
    baseline: Float,
    fontSizePx: Float,
    bold: Boolean = true,
) {
    val style = TextStyle(
        color = color,
        fontSize = (fontSizePx / density).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
    val measured = textMeasurer.measure(text, style)
    if (measured.size.width > 0) {
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                centerX - measured.size.width / 2f,
                baseline - measured.size.height,
            ),
        )
    }
}

/** Highlights for a bare set of notes, used by the scale viewer. */
fun noteHighlights(notes: List<Note>, startOctave: Int = 4): List<KeyHighlight> {
    var previousMidi = Int.MIN_VALUE
    return notes.map { note ->
        var octave = startOctave
        var pitch = Pitch(note, octave)
        while (pitch.midi <= previousMidi) {
            octave++
            pitch = Pitch(note, octave)
        }
        previousMidi = pitch.midi
        KeyHighlight(pitch.midi, KeyRole.CHORD_TONE, note.prettyName)
    }
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
