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
import androidx.compose.ui.graphics.StrokeCap
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
                center = Offset(left + whiteWidth / 2f, whiteHeight * 0.60f),
                radius = whiteWidth * 0.32f,
                keyColor = highlight.role.color,
            )
        }
        val whiteLabel = highlight?.label
        if (showLabels && whiteLabel != null) {
            drawKeyLabel(
                textMeasurer, whiteLabel, Color.White,
                centerX = left + whiteWidth / 2f,
                baseline = whiteHeight - whiteHeight * 0.08f,
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
                center = Offset(centerX, blackHeight * 0.72f),
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

    // --- The hand, then the numbers on top of it --------------------------
    if (showHand) {
        fingertips.groupBy { it.hand }.forEach { (hand, spots) ->
            drawHand(spots, hand, whiteWidth, handOpacity)
        }
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
 * one look. It is deliberately a silhouette rather than a picture of a hand: the keys have
 * to stay readable underneath, and a photograph of someone else's hand tells you less about
 * yours than a shape you can lay your own over.
 *
 * The knuckles sit at the back of the keyboard and the fingers reach towards the player,
 * which is how the hand meets the keys from where the reader is sitting.
 */
private fun DrawScope.drawHand(
    spots: List<Fingertip>,
    hand: Hand,
    whiteWidth: Float,
    opacity: Float,
) {
    if (spots.size < 2) return
    val ordered = spots.sortedBy { it.center.x }
    val skin = if (hand == Hand.LEFT) Color(0xFF3B3350) else Color(0xFF2E2740)
    val knuckleY = size.height * 0.13f
    val palmCentre = ordered.map { it.center.x }.average().toFloat()
    val fingerWidth = (whiteWidth * 0.40f).coerceAtLeast(3f * density)

    // The thumb reaches in from the side of the hand, not down from the knuckles, so it is
    // drawn from lower down and from whichever edge that hand's thumb actually sits on.
    val thumb = spots.firstOrNull { it.finger == 1 }
    val thumbAnchor = Offset(
        if (hand == Hand.RIGHT) ordered.first().center.x else ordered.last().center.x,
        size.height * 0.34f,
    )

    for (spot in ordered) {
        val fromThumb = spot.finger == 1
        val start = if (fromThumb) {
            thumbAnchor
        } else {
            // Fingers converge a little towards the middle of the hand, the way they do
            // when the knuckles are closer together than the fingertips.
            Offset(spot.center.x + (palmCentre - spot.center.x) * 0.35f, knuckleY)
        }
        drawLine(
            color = skin.copy(alpha = opacity),
            start = start,
            end = spot.center,
            strokeWidth = if (fromThumb) fingerWidth * 1.35f else fingerWidth,
            cap = StrokeCap.Round,
        )
    }

    // The palm: a soft band across the knuckles tying the fingers into one hand.
    val knuckles = ordered.filter { it.finger != 1 }.map { it.center.x }
    if (knuckles.size >= 2) {
        val left = knuckles.min() + (palmCentre - knuckles.min()) * 0.35f
        val right = knuckles.max() + (palmCentre - knuckles.max()) * 0.35f
        drawRoundRect(
            color = skin.copy(alpha = opacity * 0.82f),
            topLeft = Offset(left - fingerWidth * 0.7f, size.height * 0.02f),
            size = Size(
                (right - left) + fingerWidth * 1.4f,
                knuckleY + fingerWidth * 0.5f - size.height * 0.02f,
            ),
            cornerRadius = CornerRadius(fingerWidth, fingerWidth),
        )
    }
    if (thumb != null && knuckles.isNotEmpty()) {
        // A short web from the palm down to where the thumb comes in.
        drawLine(
            color = skin.copy(alpha = opacity * 0.82f),
            start = Offset(palmCentre, knuckleY),
            end = thumbAnchor,
            strokeWidth = fingerWidth * 1.6f,
            cap = StrokeCap.Round,
        )
    }
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
 * Draws the finger that goes on a key.
 *
 * A pale disc with the number on it, because a lit key says which note to play and says
 * nothing about how — which is the part someone learning the chord is stuck on. The left
 * hand's discs are outlined rather than filled, so a slash bass reads as the other hand at
 * a glance instead of having to be counted.
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
) {
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
