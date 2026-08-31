package com.earlln.pianocode.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earlln.pianocode.music.ConversionMode
import com.earlln.pianocode.music.Key
import com.earlln.pianocode.sheet.ConverterStage
import com.earlln.pianocode.sheet.MarkingColor
import com.earlln.pianocode.sheet.PickerTarget
import com.earlln.pianocode.sheet.SheetConverterState
import com.earlln.pianocode.sheet.SheetConverterViewModel
import com.earlln.pianocode.ui.components.SectionHeader
import com.earlln.pianocode.util.ImageIo

/**
 * Where a sheet photo can come from.
 *
 * The system photo picker is the quickest route and needs no permission, but it shows one
 * flat grid. People who keep sheet music filed away reach for their gallery app's own
 * browser — on a Galaxy that is 사진 / 앨범 / 스토리 — or for a folder in Files, so those are
 * offered alongside it rather than behind it.
 */
private enum class ImageSource(
    val title: String,
    val description: String,
    val icon: ImageVector,
) {
    PHOTO_PICKER(
        "최근 사진",
        "안드로이드 기본 사진 선택기. 권한 없이 바로 열립니다.",
        Icons.Filled.PhotoLibrary,
    ),
    GALLERY_APP(
        "갤러리 앱에서 찾기",
        "삼성 갤러리·구글 포토 등에서 사진, 앨범, 스토리를 탐색합니다.",
        Icons.Filled.Collections,
    ),
    FILES(
        "파일에서 찾기",
        "내 파일, 드라이브, 다운로드 폴더의 이미지를 고릅니다.",
        Icons.Filled.Folder,
    ),
    CAMERA(
        "카메라로 촬영",
        "지금 악보를 찍어서 바로 변환합니다.",
        Icons.Filled.PhotoCamera,
    ),
}

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
    var showViewer by remember { mutableStateOf(false) }
    // Converting every reading is cheap, but do it once per state change, not per row.
    val conversions = remember(state.entries, state.sourceKey, state.targetKey, state.mode) {
        state.conversions
    }

    var showSourceSheet by remember { mutableStateOf(false) }
    // ACTION_IMAGE_CAPTURE writes into a uri we hand it, so the destination is remembered
    // across the launch and read back once the camera reports success.
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::loadImage) }

    // Samsung Gallery, Google Photos and the like answer ACTION_PICK with their own browser,
    // which is what gets the user into 사진 / 앨범 / 스토리 rather than a flat grid.
    val pickFromApp = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> result.data?.data?.let(viewModel::loadImage) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::loadImage) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved -> if (saved) captureUri?.let(viewModel::loadImage) }

    fun open(source: ImageSource) {
        showSourceSheet = false
        try {
            when (source) {
                ImageSource.PHOTO_PICKER -> pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )

                ImageSource.GALLERY_APP -> {
                    val intent = Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    ).apply { type = "image/*" }
                    pickFromApp.launch(Intent.createChooser(intent, "앱에서 악보 찾기"))
                }

                ImageSource.FILES -> openDocument.launch(arrayOf("image/*"))

                ImageSource.CAMERA -> {
                    val uri = ImageIo.createCaptureUri(context)
                    captureUri = uri
                    takePicture.launch(uri)
                }
            }
        } catch (error: ActivityNotFoundException) {
            viewModel.showMessage("이 기기에서 ${source.title}을(를) 열 수 있는 앱을 찾지 못했습니다.")
        }
    }

    if (showSourceSheet) {
        ImageSourceSheet(
            onDismiss = { showSourceSheet = false },
            onSelect = { open(it) },
        )
    }

    LaunchedEffect(state.resultBitmap) {
        if (state.resultBitmap != null) showResult = true
    }

    state.sourceBitmap?.let { source ->
        if (showViewer) {
            SheetViewerDialog(
                original = source,
                converted = state.resultBitmap,
                startWithConverted = showResult && state.resultBitmap != null,
                onClose = { showViewer = false },
            )
        }
    }

    // The editor works on the sheet as printed. Correcting asks what the page carries, so
    // the page shown has to be the one carrying it — and an edit clears the rendered result,
    // which would otherwise swap the image out from under the finger mid-edit.
    val editorPage = state.sourceBitmap
    if (state.editorOpen && editorPage != null) {
        SheetEditorDialog(
            bitmap = editorPage,
            markingColor = state.markingColor.argb,
            entries = state.entries,
            missed = state.openMissed,
            selectedIds = state.selectedIds,
            selectedMissed = state.selectedMissed,
            changedCount = state.changedCount,
            onTap = viewModel::tapAt,
            onCorrect = viewModel::beginCorrection,
            onDelete = viewModel::deleteSelected,
            onAdoptMissed = viewModel::adoptMissed,
            onDismissMissed = viewModel::dismissMissed,
            onClearSelection = viewModel::clearSelection,
            onApply = {
                viewModel.closeEditor()
                viewModel.renderResult()
            },
            onClose = viewModel::closeEditor,
        )
    }

    // Opened either by correcting a selection or by naming a spot with no reading yet.
    state.pickerTarget?.let { target ->
        val named = (target as? PickerTarget.Entries)
            ?.let { entries -> state.entries.firstOrNull { it.id in entries.ids } }
        ChordPickerSheet(
            originalText = named?.rawText ?: (target as? PickerTarget.Spot)?.readAs,
            suggestion = named?.original,
            transpose = viewModel::transposeForPage,
            onDismiss = viewModel::cancelPicker,
            onPick = viewModel::applyChord,
        )
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
                        onClick = { showSourceSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.sourceBitmap == null) "악보 이미지 선택" else "다른 이미지 선택",
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "갤러리 앱(사진·앨범·스토리), 파일, 카메라 중에서 고를 수 있습니다.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                        "악보에서 코드를 읽는 중… 페이지를 여러 번 나눠 읽기 때문에 " +
                            "몇 초 걸립니다.",
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
                            .clip(RoundedCornerShape(12.dp))
                            // A transform detector consumed every drag across the page, so
                            // the list underneath could not be scrolled by dragging the one
                            // thing filling the screen. A tap detector leaves a drag alone.
                            .pointerInput(preview) {
                                detectTapGestures(onDoubleTap = { showViewer = true })
                            },
                    )

                    if (state.entries.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        SheetActions(
                            state = state,
                            context = context,
                            onConvert = viewModel::renderResult,
                            onEdit = viewModel::openEditor,
                            onView = { showViewer = true },
                            onSave = { viewModel.saveResult {} },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (state.resultBitmap != null) {
                                "그림을 두 번 두드려도 열립니다. 확대한 자리 그대로 " +
                                    "원본과 변환본을 번갈아 볼 수 있습니다."
                            } else {
                                "그림을 두 번 두드리면 크게 볼 수 있습니다. " +
                                    "아래에서 조성과 세부 설정을 바꿀 수 있습니다."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.entries.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    SectionHeader(
                        title = "변환 설정",
                        subtitle = "${state.entries.size}개의 코드를 찾았습니다" +
                            (if (state.correctedCount > 0) " · ${state.correctedCount}개 직접 고침" else ""),
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(state.markingColor.argb)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("바뀐 코드를 색으로 구분", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (state.markConverted) {
                                    "새로 쓴 코드는 ${state.markingColor.koreanName}, " +
                                        "원래 조성으로 남은 코드는 악보 원래 색입니다."
                                } else {
                                    "악보 원래 잉크색으로 씁니다. 인쇄용으로 깔끔하지만 " +
                                        "무엇이 바뀌었는지 구분되지 않습니다."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.markConverted,
                            onCheckedChange = viewModel::setMarkConverted,
                        )
                    }
                }
            }

            if (state.markConverted) {
                item {
                    Column {
                        LabelRow("표시 색")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                        ) {
                            items(MarkingColor.entries.toList(), key = { it.name }) { color ->
                                FilterChip(
                                    selected = state.markingColor == color,
                                    onClick = { viewModel.setMarkingColor(color) },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(14.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(color.argb)),
                                        )
                                    },
                                    label = { Text(color.koreanName) },
                                )
                            }
                        }
                        Text(
                            "악보 잉크와 가장 다른 색이 자동으로 골라집니다. 직접 바꿔도 됩니다.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
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
                            "(${state.shiftText}) · ${state.targetKey.keySignatureText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            item {
                LeftBehindWarning(
                    missedCount = state.openMissed.size,
                    disabledCount = state.rejectedCount,
                    missedSamples = state.openMissed.take(6).map { it.text },
                    sourceWidth = state.sourceBitmap?.width ?: 0,
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

            items(conversions, key = { it.first.id }) { (entry, conversion) ->
                val enabled = entry.enabled
                Card(
                    onClick = { viewModel.toggleEntry(entry.id) },
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
                                    entry.original.prettySymbol,
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
                                    append("원문 \"${entry.rawText}\"")
                                    if (!conversion.isDiatonicToSource) {
                                        append(" · 조성 밖의 코드")
                                    }
                                    if (entry.corrected) {
                                        append(" · 직접 고침")
                                    }
                                    if (entry.confidence < 0.6f) {
                                        append(" · 인식 신뢰도 낮음")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { viewModel.toggleEntry(entry.id) },
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("직접 고치기", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "그림 아래 `직접 고치기`를 누르거나 그림을 두 번 두드리면 크게 " +
                                "열립니다. 거기서 잘못 읽은 코드를 눌러 고치거나 지우고, 빈 곳을 " +
                                "눌러 코드를 새로 넣을 수 있습니다. 고친 뒤 다시 변환하면 전체가 " +
                                "새 내용으로 바뀝니다.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (state.correctedCount > 0 || state.rejectedCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildString {
                                    if (state.correctedCount > 0) {
                                        append("직접 고친 코드 ${state.correctedCount}개")
                                    }
                                    if (state.rejectedCount > 0) {
                                        if (isNotEmpty()) append(" · ")
                                        append("꺼 둔 코드 ${state.rejectedCount}개")
                                    }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(state.markingColor.argb),
                            )
                        }
                    }
                }
            }

        } else if (state.stage != ConverterStage.ANALYZING) {
            item { HowToCard(Modifier.padding(horizontal = 16.dp)) }
        }
    }
}

/**
 * Convert, edit, save and share — kept directly under the page they act on.
 *
 * They used to sit at the foot of a long settings list, which meant scrolling past every
 * key picker and every recognised chord to press the one button the screen exists for.
 */
@Composable
private fun SheetActions(
    state: SheetConverterState,
    context: Context,
    onConvert: () -> Unit,
    onEdit: () -> Unit,
    onView: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Button(
            onClick = onConvert,
            enabled = state.stage != ConverterStage.RENDERING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.stage == ConverterStage.RENDERING) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("악보에 그리는 중…")
            } else {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("악보에 ${state.changedCount}개 코드 바꿔 그리기")
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onView, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.ZoomIn, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (state.resultBitmap != null) "원본과 비교" else "크게 보기")
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("직접 고치기")
            }
        }

        if (state.resultBitmap != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("저장")
                }
                OutlinedButton(
                    onClick = {
                        state.resultBitmap?.let { bitmap ->
                            val intent =
                                ImageIo.shareIntent(context, bitmap, "PianoCode_sheet.png")
                            context.startActivity(Intent.createChooser(intent, "악보 공유"))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSourceSheet(
    onDismiss: () -> Unit,
    onSelect: (ImageSource) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(
                "악보 이미지 가져오기",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
            )
            Text(
                "어디에서 찾을지 골라 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()

            ImageSource.entries.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(source) }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        source.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(source.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            source.description,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
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
    sourceWidth: Int,
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

            if (sourceWidth in 1..LOW_RESOLUTION_WIDTH) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "이미지 가로가 ${sourceWidth}px입니다. 코드 심볼이 작게 찍혀 인식률이 " +
                        "떨어질 수 있습니다. 화면 캡처보다 원본 악보 파일이나 " +
                        "가까이서 찍은 사진이 훨씬 잘 읽힙니다.",
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

/** Below this width a page's chord symbols are too small to read reliably. */
private const val LOW_RESOLUTION_WIDTH = 1600

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
                "가로 1600px 이상이면 좋습니다. 화면 캡처보다 원본 파일이나 가까이서 찍은 사진이 유리합니다.",
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
