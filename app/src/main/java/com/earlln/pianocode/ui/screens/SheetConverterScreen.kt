package com.earlln.pianocode.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earlln.pianocode.music.ConversionMode
import com.earlln.pianocode.music.Key
import com.earlln.pianocode.sheet.ConverterStage
import com.earlln.pianocode.sheet.SheetConverterViewModel
import com.earlln.pianocode.ui.components.SectionHeader
import com.earlln.pianocode.util.ImageIo

/**
 * The sheet converter: pick a photo of a lead sheet, let the app read the chord symbols,
 * choose the scale to move them into, and write the new symbols back onto the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetConverterScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: SheetConverterViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showResult by remember { mutableStateOf(true) }
    // Converting every detected chord is cheap, but do it once per state change, not per row.
    val conversions = remember(state.detected, state.sourceKey, state.targetKey, state.mode) {
        state.conversions
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::loadImage) }

    LaunchedEffect(state.resultBitmap) {
        if (state.resultBitmap != null) showResult = true
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 40.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(
                        "악보 코드 변환",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "악보 사진 속 코드 심볼을 찾아 원하는 스케일로 바꿔 드립니다. " +
                            "사진은 기기 안에서만 처리됩니다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.sourceBitmap == null) "악보 이미지 선택" else "다른 이미지 선택",
                        )
                    }
                }
            }
        }

        state.message?.let { message ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = viewModel::clearMessage) { Text("확인") }
                    }
                }
            }
        }

        if (state.stage == ConverterStage.ANALYZING) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "악보에서 코드를 읽는 중…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val preview = if (showResult) state.resultBitmap ?: state.sourceBitmap else state.sourceBitmap
        if (preview != null) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    if (state.resultBitmap != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "변환 결과 보기",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(checked = showResult, onCheckedChange = { showResult = it })
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = if (showResult) "변환된 악보" else "원본 악보",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
        }

        if (state.detected.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(
                        title = "변환 설정",
                        subtitle = "${state.detected.size}개의 코드를 찾았습니다",
                    )
                }
            }

            item {
                Column {
                    LabelRow("변환 방식")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(ConversionMode.entries.toList()) { mode ->
                            FilterChip(
                                selected = state.mode == mode,
                                onClick = { viewModel.setMode(mode) },
                                label = { Text(mode.displayName) },
                            )
                        }
                    }
                    Text(
                        state.mode.description,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                Column {
                    LabelRow(
                        if (state.keyWasDetected) "원래 조성 (자동 인식)" else "원래 조성",
                    )
                    KeyPicker(
                        selected = state.sourceKey,
                        onSelect = viewModel::setSourceKey,
                    )
                }
            }

            item {
                Column {
                    LabelRow("바꿀 스케일")
                    KeyPicker(
                        selected = state.targetKey,
                        onSelect = viewModel::setTargetKey,
                    )
                    Text(
                        "${state.sourceKey.name} → ${state.targetKey.name} " +
                            "(${if (state.semitoneShift == 0) "같은 높이" else "+${state.semitoneShift}반음"}) · " +
                            "${state.targetKey.keySignatureText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                LeftBehindWarning(
                    missedCount = state.missed.size,
                    disabledCount = state.disabledIds.size,
                    missedSamples = state.missed.take(6).map { it.text },
                    onEnableAll = viewModel::enableAll,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(
                        title = "인식된 코드",
                        subtitle = "잘못 인식된 항목은 꺼서 원본 그대로 둘 수 있습니다",
                    )
                }
            }

            items(conversions, key = { it.first.id }) { (detected, conversion) ->
                val enabled = detected.id !in state.disabledIds
                Card(
                    onClick = { viewModel.toggleChord(detected.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enabled) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    detected.chord.prettySymbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    textDecoration = if (enabled) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "  →  ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    conversion.converted.prettySymbol,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Text(
                                buildString {
                                    append("원문 \"${detected.rawText}\"")
                                    if (!conversion.isDiatonicToSource) {
                                        append(" · 조성 밖의 코드")
                                    }
                                    if (detected.confidence < 0.6f) {
                                        append(" · 인식 신뢰도 낮음")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.toggleChord(detected.id) },
                        )
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Button(
                        onClick = viewModel::renderResult,
                        enabled = state.stage != ConverterStage.RENDERING,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.stage == ConverterStage.RENDERING) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("악보에 그리는 중…")
                        } else {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("악보에 ${state.changedCount}개 코드 바꿔 그리기")
                        }
                    }

                    if (state.resultBitmap != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.saveResult {} },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("저장")
                            }
                            OutlinedButton(
                                onClick = {
                                    state.resultBitmap?.let { bitmap ->
                                        val intent = ImageIo.shareIntent(
                                            context, bitmap, "PianoCode_sheet.png",
                                        )
                                        context.startActivity(
                                            Intent.createChooser(intent, "악보 공유"),
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("공유")
                            }
                        }
                    }
                }
            }
        } else if (state.stage != ConverterStage.ANALYZING) {
            item { HowToCard(Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

@Composable
private fun LabelRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun KeyPicker(
    selected: Key,
    onSelect: (Key) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(Key.COMMON_KEYS, key = { "${it.tonic.letter}:${it.tonic.accidental}:${it.type.id}" }) { key ->
            FilterChip(
                selected = key.tonic == selected.tonic && key.type == selected.type,
                onClick = { onSelect(key) },
                label = { Text(key.shortName) },
            )
        }
    }
}

/**
 * Says how much of the page will stay in the original key.
 *
 * A sheet where only some symbols moved is worse than one that was never converted — the
 * chords and the staff disagree and nothing on the page says so. Anything the recogniser
 * could not read, or the user switched off, is counted here before they hit convert.
 */
@Composable
private fun LeftBehindWarning(
    missedCount: Int,
    disabledCount: Int,
    missedSamples: List<String>,
    onEnableAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (missedCount + disabledCount > 0) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (missedCount + disabledCount > 0) {
                    "원래 조성으로 남는 부분이 있습니다"
                } else {
                    "찾은 코드를 모두 변환합니다"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))

            if (missedCount > 0) {
                Text(
                    "· 코드처럼 보이지만 읽지 못한 글자 ${missedCount}곳" +
                        if (missedSamples.isEmpty()) {
                            ""
                        } else {
                            " (${missedSamples.joinToString(", ")})"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (disabledCount > 0) {
                Text(
                    "· 직접 꺼 둔 코드 ${disabledCount}개",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (missedCount + disabledCount > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "이 부분은 원본 그대로 남아 한 악보에 두 조성이 섞이게 됩니다. " +
                        "저장 전에 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "오선보의 조표와 음표는 바뀌지 않습니다. 코드 심볼만 옮겨 적습니다.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (disabledCount > 0) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onEnableAll) { Text("꺼 둔 코드 모두 켜기") }
            }
        }
    }
}

@Composable
private fun HowToCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("잘 인식되는 사진", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            listOf(
                "코드 심볼(C, Am7, F#m7b5)이 또렷하게 보이도록 찍어 주세요.",
                "악보를 정면에서, 그림자 없이 밝게 촬영하면 정확도가 올라갑니다.",
                "인쇄된 악보가 손글씨보다 훨씬 잘 인식됩니다.",
                "한 번에 한 페이지씩 변환하는 것이 좋습니다.",
            ).forEach {
                Text(
                    "· $it",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}
