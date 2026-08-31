package com.earlln.pianocode.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Reading the page close up, and holding it against the original.
 *
 * A sheet fitted to a phone's width is unreadable — that is the whole reason the editor
 * zooms — but wanting to read a bar of music is not wanting to correct it. This is the
 * same pinch, drag and double tap without the outlines, the selection or the risk of
 * changing something by touching the wrong place.
 *
 * Switching between the two pages keeps the zoom and the position, which is what makes it
 * a comparison: the same bar, in the same place on the screen, one chord row against the
 * other. Two views side by side would halve a page that is already too small to read.
 */
@Composable
fun SheetViewerDialog(
    original: Bitmap,
    converted: Bitmap?,
    startWithConverted: Boolean,
    onClose: () -> Unit,
) {
    val originalImage = remember(original) { original.asImageBitmap() }
    val convertedImage = remember(converted) { converted?.asImageBitmap() }
    var showConverted by remember(converted) {
        mutableStateOf(startWithConverted && converted != null)
    }
    val bitmap = if (showConverted && converted != null) converted else original
    val image = if (showConverted && convertedImage != null) convertedImage else originalImage

    // The converted page carries a banner above the sheet, so it is taller. Lifting it by
    // that much puts the two pages' staves in the same place, which is the whole point.
    val bannerOffset =
        if (showConverted && converted != null) {
            (converted.height - original.height).coerceAtLeast(0)
        } else {
            0
        }

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }

    fun baseScale(): Float =
        if (viewport.width <= 0f) 1f else viewport.width / original.width

    fun scale(): Float = baseScale() * zoom

    fun clampPan(candidate: Offset): Offset {
        // Clamped against the original throughout, so the reachable area does not shift
        // when the page is switched.
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

    Dialog(
        onDismissRequest = onClose,
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
                    if (converted == null) "악보 크게 보기" else "원본과 비교",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("닫기")
                }
            }

            if (converted != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = !showConverted,
                        onClick = { showConverted = false },
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
                                val growth = zoom / previous
                                pan = clampPan(centroid - (centroid - pan) * growth + drag)
                            }
                        }
                        .pointerInput(bitmap) {
                            // Double tap steps in on the spot, and again to fit the page.
                            detectTapGestures(
                                onDoubleTap = { tap ->
                                    val previous = zoom
                                    zoom = if (zoom < 2.5f) 3f else 1f
                                    val growth = zoom / previous
                                    pan = clampPan(tap - (tap - pan) * growth)
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
                }

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
                                if (showConverted) "변환본" else "원본",
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
