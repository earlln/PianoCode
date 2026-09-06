package com.earlln.pianocode.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.earlln.pianocode.util.ImageIo

/**
 * Where a sheet can come from.
 *
 * The system photo picker is the quickest route and needs no permission, but it shows one
 * flat grid. People who keep sheet music filed away reach for their gallery app's own
 * browser — on a Galaxy that is 사진 / 앨범 / 스토리 — or for a folder in Files, so those are
 * offered alongside it rather than behind it.
 */
enum class SheetSource(
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
        "내 파일, 드라이브, 다운로드 폴더의 이미지나 PDF 악보를 고릅니다.",
        Icons.Filled.Folder,
    ),
    CAMERA(
        "카메라로 촬영",
        "지금 악보를 찍어서 바로 불러옵니다.",
        Icons.Filled.PhotoCamera,
    ),
}

/**
 * Wires up every way of getting a sheet into the app and hands back one function to open
 * whichever the reader chose.
 *
 * Four different launchers with a camera destination to remember between them is a lot of
 * machinery for a screen to carry, and any screen that loads a sheet needs all of it. Kept
 * here, both screens offer the same choices and a fix to one is a fix to both.
 */
@Composable
fun rememberSheetSourceOpener(
    onPicked: (Uri) -> Unit,
    onMessage: (String) -> Unit,
): (SheetSource) -> Unit {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val message by rememberUpdatedState(onMessage)

    // ACTION_IMAGE_CAPTURE writes into a uri handed to it, so the destination is kept
    // across the launch and read back once the camera reports success.
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(picked) }

    // Samsung Gallery, Google Photos and the like answer ACTION_PICK with their own
    // browser, which is what gets the reader into 사진 / 앨범 / 스토리 rather than a flat grid.
    val pickFromApp = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> result.data?.data?.let(picked) }

    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(picked) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved -> if (saved) captureUri?.let(picked) }

    return remember(context) {
        { source ->
            try {
                when (source) {
                    SheetSource.PHOTO_PICKER -> pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )

                    SheetSource.GALLERY_APP -> {
                        val intent = Intent(
                            Intent.ACTION_PICK,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        ).apply { type = "image/*" }
                        pickFromApp.launch(Intent.createChooser(intent, "앱에서 악보 찾기"))
                    }

                    SheetSource.FILES -> openDocument.launch(arrayOf("image/*", "application/pdf"))

                    SheetSource.CAMERA -> {
                        val uri = ImageIo.createCaptureUri(context)
                        captureUri = uri
                        takePicture.launch(uri)
                    }
                }
            } catch (error: ActivityNotFoundException) {
                message("이 기기에서 ${source.title}을(를) 열 수 있는 앱을 찾지 못했습니다.")
            }
        }
    }
}

/** The list of ways in, as a sheet from the bottom of the screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetSourcePicker(
    onDismiss: () -> Unit,
    onSelect: (SheetSource) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Text(
                "악보 가져오기",
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

            SheetSource.entries.forEach { source ->
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
