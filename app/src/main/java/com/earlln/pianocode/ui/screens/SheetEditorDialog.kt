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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.earlln.pianocode.sheet.MissedCandidate
import com.earlln.pianocode.sheet.SheetEntry
import com.earlln.pianocode.sheet.key

/**
 * Full-screen editor for correcting what the app believes the page says.
 *
 * It has to be its own screen. On a page scaled to a phone's width a chord symbol is a few
 * millimetres across — too small to aim at — and while the preview sat inside the scrolling
 * settings list, every drag over it was taken by the list instead. Here the page fills the
 * screen, pinch and drag move it, and a tap selects.
 *
 * It shows the sheet as printed, never the converted output. Correcting asks what the page
 * actually carries, so the page in front of the reader has to be the one carrying it — and
 * an edit that discards the rendered result would otherwise swap the image mid-edit.
 *
 * The actions sit directly under the header rather than at the foot of the screen: a
 * selection is made by tapping the page, and the answer to "now what" should not be at the
 * far end of a phone from the page it applies to.
 */
@Composable
fun SheetEditorDialog(
    bitmap: Bitmap,
    markingColor: Int,
    entries: List<SheetEntry>,
    missed: List<MissedCandidate>,
    selectedIds: Set<String>,
    selectedMissed: String?,
    onTap: (Rect) -> Unit,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
    onAdoptMissed: () -> Unit,
    onDismissMissed: () -> Unit,
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
        // The activity draws edge to edge; a dialog that fits system windows would be inset
        // by the framework and then again by the padding below, so it takes the insets itself.
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
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
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
                    Text("닫기")
                }
            }

            ActionBar(
                markingColor = markingColor,
                missedCount = missed.size,
                selectedCount = selectedIds.size,
                missedSelected = selectedMissed != null,
                onCorrect = onCorrect,
                onDelete = onDelete,
                onAdoptMissed = onAdoptMissed,
                onDismissMissed = onDismissMissed,
                onClearSelection = onClearSelection,
            )

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
                                val x = page.x.toInt()
                                val y = page.y.toInt()
                                onTap(Rect(x, y, x, y))
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
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { zoom = (zoom / 1.6f).coerceAtLeast(1f) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "축소")
                }
                OutlinedButton(onClick = { zoom = (zoom * 1.6f).coerceAtMost(10f) }) {
                    Icon(Icons.Filled.Add, contentDescription = "확대")
                }
                Text(
                    "${(zoom * 100).toInt()}%  ·  코드 ${entries.size}개",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * What can be done with the current selection, kept at the top of the screen.
 *
 * It holds its place whether or not anything is selected, so the page below never jumps
 * when a tap lands.
 */
@Composable
private fun ActionBar(
    markingColor: Int,
    missedCount: Int,
    selectedCount: Int,
    missedSelected: Boolean,
    onCorrect: () -> Unit,
    onDelete: () -> Unit,
    onAdoptMissed: () -> Unit,
    onDismissMissed: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            when {
                missedSelected -> {
                    Text(
                        "분홍색 표시 1곳 — 아직 코드로 넣지 않은 자리입니다.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAdoptMissed, modifier = Modifier.weight(1f)) {
                            Text("코드로 넣기")
                        }
                        OutlinedButton(onClick = onDismissMissed) { Text("코드 아님") }
                        OutlinedButton(onClick = onClearSelection) { Text("해제") }
                    }
                }

                selectedCount > 0 -> {
                    Text(
                        "${selectedCount}개 선택됨",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onCorrect, modifier = Modifier.weight(1f)) {
                            Text("코드 고치기")
                        }
                        OutlinedButton(onClick = onDelete) { Text("지우기") }
                        OutlinedButton(onClick = onClearSelection) { Text("해제") }
                    }
                }

                else -> {
                    Text(
                        "고칠 자리를 누르세요. 코드는 여러 개를 한 번에 고를 수 있습니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LegendRow(Color(markingColor), "앱이 찾은 코드 — 고치거나 지웁니다")
                    if (missedCount > 0) {
                        Spacer(Modifier.height(3.dp))
                        LegendRow(
                            Color(0xFFFF7597),
                            "넣지 않은 자리 ${missedCount}곳 — 넣거나 없앱니다",
                        )
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
