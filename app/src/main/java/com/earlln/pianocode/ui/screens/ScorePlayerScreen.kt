package com.earlln.pianocode.ui.screens

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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earlln.pianocode.music.Instrument
import com.earlln.pianocode.score.ReadingStage
import com.earlln.pianocode.score.ScorePlayerState
import com.earlln.pianocode.score.ScorePlayerViewModel
import com.earlln.pianocode.ui.components.SectionHeader
import com.earlln.pianocode.ui.components.SheetSourcePicker
import com.earlln.pianocode.ui.components.rememberSheetSourceOpener

/**
 * Plays the music written on a page.
 *
 * Its own screen, and its own reading of the photograph. The converter across the drawer
 * reads the chord symbols printed above the staff and never looks at the notes; this reads
 * the notes and never looks at the symbols.
 */
@Composable
fun ScorePlayerScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ScorePlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // The same ways in as the converter screen: recent photos, the phone's own gallery
    // app with its albums, a folder, or the camera.
    var showSources by remember { mutableStateOf(false) }
    val open = rememberSheetSourceOpener(
        onPicked = { viewModel.load(it) },
        onMessage = viewModel::showMessage,
    )

    if (showSources) {
        SheetSourcePicker(
            onDismiss = { showSources = false },
            onSelect = {
                showSources = false
                open(it)
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.padding(16.dp)) {
                SectionHeader(
                    title = "악보 연주",
                    subtitle = "악보에 적힌 음표를 읽어서 그대로 들려줍니다",
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showSources = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.page == null) "악보 가져오기" else "다른 악보 가져오기")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "갤러리 앱(사진·앨범·스토리), 파일, 카메라 중에서 고를 수 있고 PDF 악보도 됩니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.stage == ReadingStage.LOADING || state.stage == ReadingStage.READING) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.stage == ReadingStage.LOADING) "악보를 여는 중…"
                        else "오선과 음표를 읽는 중…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        state.page?.let { page ->
            item {
                Image(
                    bitmap = page.asImageBitmap(),
                    contentDescription = "불러온 악보",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }

        if (state.pdfPageCount > 1) {
            item {
                Spacer(Modifier.height(16.dp))
                PdfPages(
                    pageCount = state.pdfPageCount,
                    page = state.pdfPage,
                    onSelect = viewModel::openPdfPage,
                )
            }
        }

        if (state.score != null) {
            item {
                Spacer(Modifier.height(16.dp))
                ReadingCard(state = state, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        if (state.hasNotes) {
            item {
                Spacer(Modifier.height(16.dp))
                PlayCard(
                    state = state,
                    onInstrument = viewModel::setInstrument,
                    onTempo = viewModel::setTempo,
                    onTranspose = viewModel::setTranspose,
                    onToggle = viewModel::togglePlayback,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        state.message?.let { message ->
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::dismissMessage) { Text("닫기") }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            LimitsCard(Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** What the reader made of the page, in the terms a musician would ask about. */
@Composable
private fun ReadingCard(state: ScorePlayerState, modifier: Modifier = Modifier) {
    val score = state.score ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("읽은 내용", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "오선 ${score.staves.size}줄 · 음표 ${score.notes.size}개",
                style = MaterialTheme.typography.bodyMedium,
            )
            score.staves.forEachIndexed { index, staff ->
                val key = when {
                    staff.key.sharps > 0 -> "샤프 ${staff.key.sharps}개"
                    staff.key.flats > 0 -> "플랫 ${staff.key.flats}개"
                    else -> "조표 없음"
                }
                Text(
                    "${index + 1}번째 줄 · ${staff.clef.koreanName} · $key · ${staff.noteCount}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (score.notes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "첫 음: " + score.notes.take(12).joinToString(" ") { it.pitch.note.prettyName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlayCard(
    state: ScorePlayerState,
    onInstrument: (Instrument) -> Unit,
    onTempo: (Int) -> Unit,
    onTranspose: (Int) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                "악기와 빠르기",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(Instrument.entries.toList(), key = { it.name }) { instrument ->
                    FilterChip(
                        selected = instrument == state.instrument,
                        onClick = { onInstrument(instrument) },
                        label = { Text(instrument.koreanName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(TEMPO_CHOICES, key = { it }) { bpm ->
                    FilterChip(
                        selected = bpm == state.tempo,
                        onClick = { onTempo(bpm) },
                        label = { Text("${bpm}bpm") },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "조 옮기기",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                "부르기 편한 높이로 올리거나 내려서 들어 봅니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(TRANSPOSE_CHOICES, key = { it }) { semitones ->
                    FilterChip(
                        selected = semitones == state.transpose,
                        onClick = { onTranspose(semitones) },
                        label = {
                            Text(
                                when {
                                    semitones == 0 -> "원래대로"
                                    semitones > 0 -> "+$semitones"
                                    else -> "$semitones"
                                },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(
                    if (state.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(if (state.playing) "멈추기" else "${state.notes}개 음표 연주")
            }
        }
    }
}

@Composable
private fun PdfPages(pageCount: Int, page: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "PDF ${pageCount}쪽 중 ${page + 1}쪽",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(pageCount) { index ->
                FilterChip(
                    selected = index == page,
                    onClick = { onSelect(index) },
                    label = { Text("${index + 1}") },
                )
            }
        }
    }
}

/**
 * Says plainly what this cannot do.
 *
 * Reading music off a photograph is guesswork dressed up as arithmetic, and a reader who
 * knows where it guesses can work with it. One who has been told it simply works will
 * conclude the app is broken the first time a rest goes missing.
 */
@Composable
private fun LimitsCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "잘 읽히는 악보와 한계",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            listOf(
                "인쇄된 악보를 정면에서 찍거나 PDF로 넣을 때 가장 잘 읽습니다.",
                "온·2분·4분·8분 쉼표를 읽습니다. 16분 쉼표는 4분 쉼표로 셉니다.",
                "이음줄·꾸밈음·셋잇단음표는 반영되지 않습니다.",
                "손으로 쓴 악보나 기울어진 사진은 인식률이 크게 떨어집니다.",
                "읽은 결과가 이상하면 위의 '읽은 내용'과 악보를 견주어 보세요.",
            ).forEach {
                Text(
                    "· $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private val TEMPO_CHOICES = listOf(60, 80, 100, 120, 144)
private val TRANSPOSE_CHOICES = listOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
