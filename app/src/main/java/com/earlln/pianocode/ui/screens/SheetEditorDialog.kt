package com.earlln.pianocode.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.earlln.pianocode.sheet.MissedCandidate
import com.earlln.pianocode.sheet.SheetEntry
import com.earlln.pianocode.sheet.key
import kotlin.math.roundToInt

/**
 * Full-screen editor for correcting what the app believes the page says.
 *
 * It has to be its own screen. On a page scaled to a phone's width a chord symbol is a few
 * millimetres across — too small to aim at — and while the preview sat inside the scrolling
 * settings list, every drag over it was taken by the list instead.
 *
 * It shows the sheet as printed, never the converted output: correcting asks what the page
 * already carries, so the page in front of the reader has to be the one carrying it.
 *
 * What can be done with a selection follows the selection around the page. Zoomed in to
 * reach a symbol a few millimetres wide, a control bar pinned to the edge of the screen is
 * a round trip — zoom out, reach, zoom back — for every single correction.
 */
@Composable
fun SheetEditorDialog(
    original: Bitmap,
    converted: Bitmap?,
    startWithConverted: Boolean,
    markingColor: Int,
    entries: List<SheetEntry>,
    missed: List<MissedCandidate>,
    selectedIds: Set<String>,
    selectedMissed: String?,
    changedCount: Int,
    onTap: (Rect) -> Unit,
    onHold: (Rect) -> Unit,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
    onAdoptMissed: () -> Unit,
    onDismissMissed: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val originalImage = remember(original) { original.asImageBitmap() }
    val convertedImage = remember(converted) { converted?.asImageBitmap() }
    var showConverted by remember(converted) {
        mutableStateOf(startWithConverted && converted != null)
    }
    val bitmap = if (showConverted && converted != null) converted else original
    val image = if (showConverted && convertedImage != null) convertedImage else originalImage

    // The converted page carries a banner above the sheet and is taller by exactly that
    // much. Lifting it by the difference puts the two pages' staves on the same pixels, so
    // the outlines fit either page and switching is a comparison rather than a jump.
    val bannerOffset =
        if (showConverted && converted != null) {
            (converted.height - original.height).coerceAtLeast(0)
        } else {
            0
        }

    // Once a converted page exists, `원본` means the photograph as it was taken: no
    // outlines, no labels, nothing the app added. Marks belong on the page being worked on.
    val marksVisible = converted == null || showConverted

    val density = LocalDensity.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var actionsSize by remember { mutableStateOf(IntSize.Zero) }
    // Reused across frames: allocating paints inside a draw pass is per-frame garbage.
    val labelInk = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val labelPaper = remember { android.graphics.Paint().apply { isAntiAlias = true } }

    // The page is laid out to the screen's width at zoom 1, so one number scales both axes
    // and the mapping back to page coordinates stays a division.
    fun baseScale(): Float =
        if (viewport.width <= 0f) 1f else viewport.width / original.width

    fun scale(): Float = baseScale() * zoom

    fun toPage(point: Offset): Offset =
        Offset((point.x - pan.x) / scale(), (point.y - pan.y) / scale())

    fun clampPan(candidate: Offset): Offset {
        val drawnWidth = original.width * scale()
        val drawnHeight = original.height * scale()
        val minX = minOf(0f, viewport.width - drawnWidth)
        val minY = minOf(0f, viewport.height - drawnHeight)
        val maxX = maxOf(0f, viewport.width - drawnWidth)
        val maxY = maxOf(0f, viewport.height - drawnHeight)
        return Offset(
            candidate.x.coerceIn(minOf(minX, maxX), maxOf(minX, maxX)),
            candidate.y.coerceIn(minOf(minY, maxY), maxOf(minY, maxY)),
        )
    }

    /** The area the selection covers on the page, or null when nothing is selected. */
    val focus: Rect? = when {
        selectedMissed != null -> missed.firstOrNull { it.key == selectedMissed }?.bounds
        selectedIds.isNotEmpty() -> entries
            .filter { it.id in selectedIds }
            .map { it.bounds }
            .reduceOrNull { a, b ->
                Rect(
                    minOf(a.left, b.left),
                    minOf(a.top, b.top),
                    maxOf(a.right, b.right),
                    maxOf(a.bottom, b.bottom),
                )
            }

        else -> null
    }

    Dialog(
        onDismissRequest = onClose,
        // The activity draws edge to edge; the padding below takes the insets itself.
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "악보 보기 · 고치기",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("그냥 닫기")
                }
            }

            // Closing is not applying, so say what applying is and put it in reach. Every
            // edit invalidates the drawn page, and without this the way back to a converted
            // sheet is to close, find the screen again and press convert there.
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("고친 대로 악보에 ${changedCount}개 바꿔 그리기")
            }

            if (converted != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = !showConverted,
                        onClick = {
                            showConverted = false
                            onClearSelection()
                        },
                        label = { Text("원본") },
                    )
                    FilterChip(
                        selected = showConverted,
                        onClick = { showConverted = true },
                        label = { Text("변환본") },
                    )
                    Text(
                        "확대한 자리 그대로 번갈아 봅니다",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!marksVisible) {
                        Text(
                            "찍은 그대로의 원본입니다. 표시도, 손댈 곳도 없습니다.",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "고치려면 `변환본`으로 돌아가세요. 확대한 자리는 그대로입니다.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (focus != null) {
                        Text(
                            if (selectedMissed != null) {
                                "분홍색 표시 1곳 선택됨 — 그림 위 버튼으로 처리하세요."
                            } else {
                                "${selectedIds.size}개 선택됨 — 그림 위 버튼으로 처리하세요."
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Text(
                            "코드를 꾹 누르면 바로 고칩니다. 짧게 누르면 골라 두었다가 " +
                                "여러 개를 한 번에 처리합니다.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(5.dp))
                        LegendRow(Color(markingColor), "앱이 찾은 코드 — 고치거나 지웁니다")
                        if (missed.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            LegendRow(
                                Color(0xFFFF7597),
                                "넣지 않은 자리 ${missed.size}곳 — 넣거나 없앱니다",
                            )
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            viewport = Size(it.width.toFloat(), it.height.toFloat())
                        }
                        .pointerInput(original) {
                            detectTransformGestures { centroid, drag, gestureZoom, _ ->
                                val previous = zoom
                                zoom = (zoom * gestureZoom).coerceIn(1f, 10f)
                                // Hold the page still under the fingers: the point beneath
                                // the centroid before the pinch stays beneath it after.
                                val growth = zoom / previous
                                pan = clampPan(centroid - (centroid - pan) * growth + drag)
                            }
                        }
                        .pointerInput(original) {
                            fun at(point: Offset): Rect {
                                val page = toPage(point)
                                val x = page.x.toInt()
                                val y = page.y.toInt()
                                return Rect(x, y, x, y)
                            }
                            detectTapGestures(
                                onTap = { if (marksVisible) onTap(at(it)) },
                                onLongPress = { if (marksVisible) onHold(at(it)) },
                                onDoubleTap = { tap ->
                                    val previous = zoom
                                    zoom = if (zoom < 2.5f) 3f else 1f
                                    pan = clampPan(tap - (tap - pan) * (zoom / previous))
                                },
                            )
                        },
                ) {
                    val drawScale = scale()
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(
                            pan.x.toInt(),
                            (pan.y - bannerOffset * drawScale).toInt(),
                        ),
                        dstSize = IntSize(
                            (bitmap.width * drawScale).toInt(),
                            (bitmap.height * drawScale).toInt(),
                        ),
                    )

                    fun box(rect: Rect, color: Color, width: Float, fill: Boolean = false) {
                        val topLeft = Offset(
                            pan.x + rect.left * drawScale,
                            pan.y + rect.top * drawScale,
                        )
                        val boxSize = Size(rect.width() * drawScale, rect.height() * drawScale)
                        if (fill) {
                            drawRect(
                                color = color.copy(alpha = 0.28f),
                                topLeft = topLeft,
                                size = boxSize,
                            )
                        }
                        drawRect(
                            color = color,
                            topLeft = topLeft,
                            size = boxSize,
                            style = Stroke(width = width),
                        )
                    }

                    if (!marksVisible) return@Canvas

                    // Text that reads like a chord but no entry covers — worth a look.
                    missed.forEach { candidate ->
                        val picked = candidate.key == selectedMissed
                        box(
                            candidate.bounds,
                            Color(0xFFFF7597),
                            if (picked) 4.dp.toPx() else 2.dp.toPx(),
                            fill = picked,
                        )
                    }

                    entries.forEach { entry ->
                        val selected = entry.id in selectedIds
                        val colour = when {
                            selected -> Color(0xFF2196F3)
                            !entry.enabled -> Color(0xFF9E9E9E)
                            else -> Color(markingColor)
                        }
                        box(
                            entry.bounds,
                            colour,
                            if (selected) 4.dp.toPx() else 2.dp.toPx(),
                            fill = selected,
                        )
                    }

                    // What the app believes is written at each spot, so a box that looks
                    // right but reads wrong can be told apart from one that is simply
                    // correct. Only once the page is enlarged enough for it to be legible.
                    //
                    // Light on a dark chip, never in the marking colour: on the converted
                    // page the marking colour is what the app has just written onto the
                    // sheet, and a label in the same colour reads as more of the same ink
                    // rather than as a note about it.
                    val labelSize = 13.dp.toPx()
                    val minimumBox = 15.dp.toPx()
                    labelInk.textSize = labelSize
                    labelInk.color = 0xFFFFFFFF.toInt()
                    fun label(rect: Rect, text: String, chip: Int) {
                        if (rect.height() * drawScale < minimumBox) return
                        val x = pan.x + rect.left * drawScale
                        val y = pan.y + rect.top * drawScale - 4.dp.toPx()
                        val canvas = drawContext.canvas.nativeCanvas
                        labelPaper.color = chip
                        canvas.drawRect(
                            x - 4f,
                            y - labelSize,
                            x + labelInk.measureText(text) + 4f,
                            y + 5f,
                            labelPaper,
                        )
                        canvas.drawText(text, x, y, labelInk)
                    }

                    missed.forEach { label(it.bounds, it.text, 0xFFC2185B.toInt()) }
                    entries.forEach { entry ->
                        label(
                            entry.bounds,
                            entry.original.symbol,
                            when {
                                entry.id in selectedIds -> 0xFF1565C0.toInt()
                                !entry.enabled -> 0xFF757575.toInt()
                                else -> 0xE6263238.toInt()
                            },
                        )
                    }
                }

                if (focus != null && marksVisible) {
                    val gap = with(density) { 10.dp.toPx() }
                    val drawScale = scale()
                    val centreX = pan.x + (focus.left + focus.right) / 2f * drawScale
                    val under = pan.y + focus.bottom * drawScale + gap
                    val over = pan.y + focus.top * drawScale - actionsSize.height - gap
                    // Under the selection unless that falls off the screen, then over it,
                    // and failing both simply somewhere visible.
                    val y = when {
                        under + actionsSize.height <= viewport.height -> under
                        over >= 0f -> over
                        else -> (viewport.height - actionsSize.height).coerceAtLeast(0f)
                    }
                    val x = (centreX - actionsSize.width / 2f)
                        .coerceIn(0f, (viewport.width - actionsSize.width).coerceAtLeast(0f))

                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                            .onSizeChanged { actionsSize = it },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val labels = if (selectedMissed != null) {
                                listOf("코드로 넣기" to onAdoptMissed, "코드 아님" to onDismissMissed)
                            } else {
                                listOf("고치기" to onCorrect, "지우기" to onDelete)
                            }
                            labels.forEach { (label, action) ->
                                TextButton(onClick = action) {
                                    Text(
                                        label,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                            TextButton(onClick = onClearSelection) {
                                Text(
                                    "해제",
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                    }
                }

                // Floating rather than in a row of its own: a bar at the foot of a dialog
                // ends up under the system gesture bar, where it cannot be read or pressed.
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            "${(zoom * 100).toInt()}% · " +
                                if (showConverted) "변환본" else "원본 코드 ${entries.size}개",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = { zoom = (zoom / 1.6f).coerceAtLeast(1f) },
                        modifier = Modifier.size(46.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "축소")
                    }
                    OutlinedButton(
                        onClick = { zoom = (zoom * 1.6f).coerceAtMost(10f) },
                        modifier = Modifier.size(46.dp),
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "확대")
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(11.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
