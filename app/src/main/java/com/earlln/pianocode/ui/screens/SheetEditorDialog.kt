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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.earlln.pianocode.sheet.MissedCandidate
import com.earlln.pianocode.sheet.SheetEntry

/**
 * Full-screen editor for placing chords the conversion did not reach.
 *
 * It has to be its own screen. On a page scaled to a phone's width a chord symbol is a few
 * millimetres across — too small to aim at — and while the preview sat inside the scrolling
 * settings list, every drag over it was taken by the list instead. Here the page fills the
 * screen, pinch and drag move it, and a tap means one thing only: put a chord there.
 *
 * Every chord the app believes it found is outlined, so the page shows what it understood.
 * Tapping one selects it — several at once, since the same misreading usually repeats down
 * a page — and the bar underneath either corrects them all or throws them away. Tapping bare
 * paper adds a chord the reader never saw.
 */
@Composable
fun SheetEditorDialog(
    bitmap: Bitmap,
    bannerHeight: Int,
    markingColor: Int,
    entries: List<SheetEntry>,
    missed: List<MissedCandidate>,
    selectedIds: Set<String>,
    onTap: (Rect) -> Unit,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
    onClearSelection: () -> Unit,
    onClose: () -> Unit,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }

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

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("직접 고치기", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "코드를 눌러 고르고, 빈 곳을 누르면 코드를 새로 넣습니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("닫기")
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
                        .pointerInput(bitmap, bannerHeight) {
                            detectTapGestures { tap ->
                                val page = toPage(tap)
                                val x = page.x.toInt()
                                val y = page.y.toInt() - bannerHeight
                                val marked = missed.firstOrNull { it.bounds.contains(x, y) }
                                onTap(marked?.bounds ?: Rect(x, y, x, y))
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
                            pan.y + (rect.top + bannerHeight) * drawScale,
                        )
                        val boxSize = Size(rect.width() * drawScale, rect.height() * drawScale)
                        if (fill) {
                            drawRect(color = color.copy(alpha = 0.28f), topLeft = topLeft, size = boxSize)
                        }
                        drawRect(
                            color = color,
                            topLeft = topLeft,
                            size = boxSize,
                            style = Stroke(width = width),
                        )
                    }

                    // Text that reads like a chord but no entry covers — worth a look.
                    missed.filter { candidate ->
                        entries.none { entry ->
                            entry.bounds.left < candidate.bounds.right &&
                                candidate.bounds.left < entry.bounds.right &&
                                entry.bounds.top < candidate.bounds.bottom &&
                                candidate.bounds.top < entry.bounds.bottom
                        }
                    }.forEach { box(it.bounds, Color(0xFFFF7597), 2.dp.toPx()) }

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
            }

            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { zoom = (zoom / 1.6f).coerceAtLeast(1f) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "축소")
                    }
                    OutlinedButton(onClick = { zoom = (zoom * 1.6f).coerceAtMost(10f) }) {
                        Icon(Icons.Filled.Add, contentDescription = "확대")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${(zoom * 100).toInt()}%  ·  코드 ${entries.size}개",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (selectedIds.isEmpty()) {
                    Text(
                        "잘못 인식한 코드를 눌러 고르세요. 여러 개를 한 번에 고를 수 있습니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${selectedIds.size}개 선택됨",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onCorrect, modifier = Modifier.weight(1f)) {
                            Text("코드 고치기")
                        }
                        OutlinedButton(onClick = onDelete) { Text("지우기") }
                        OutlinedButton(onClick = onClearSelection) { Text("해제") }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}