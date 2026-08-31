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
    bitmap: Bitmap,
    markingColor: Int,
    entries: List<SheetEntry>,
    missed: List<MissedCandidate>,
    selectedIds: Set<String>,
    selectedMissed: String?,
    changedCount: Int,
    onTap: (Rect) -> Unit,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
    onAdoptMissed: () -> Unit,
    onDismissMissed: () -> Unit,
    onClearSelection: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var actionsSize by remember { mutableStateOf(IntSize.Zero) }

    // The page is laid out to the screen's width at zoom 1, so one number scales both axes
    // and the mapping back to page coordinates stays a division.
    fun baseScale(): Float =
        if (viewport.width <= 0f) 1f else viewport.width / bitmap.width

    fun scale(): Float = baseScale() * zoom

    fun toPage(point: Offset): Offset =
        Offset((point.x - pan.x) / scale(), (point.y - pan.y) / scale())

    fun clampPan(candidate: Offset): Offset {
        val drawnWidth = bitmap.width * scale()
        val drawnHeight = bitmap.height * scale()
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
                    "직접 고치기",
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

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (focus != null) {
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
                            "고칠 자리를 누르세요. 코드는 여러 개를 한 번에 고를 수 있습니다.",
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
                        .pointerInput(bitmap) {
                            detectTransformGestures { centroid, drag, gestureZoom, _ ->
                                val previous = zoom
                                zoom = (zoom * gestureZoom).coerceIn(1f, 10f)
                                // Hold the page still under the fingers: the point beneath
                                // the centroid before the pinch stays beneath it after.
                                val growth = zoom / previous
                                pan = clampPan(centroid - (centroid - pan) * growth + drag)
                            }
                        }
                        .pointerInput(bitmap) {
                            detectTapGestures { tap ->
                                val page = toPage(tap)
                                onTap(Rect(page.x.toInt(), page.y.toInt(), page.x.toInt(), page.y.toInt()))
                            }
                        },
                ) {
                    val drawScale = scale()
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(pan.x.toInt(), pan.y.toInt()),
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
                }

                if (focus != null) {
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
                            "${(zoom * 100).toInt()}% · 코드 ${entries.size}개",
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
