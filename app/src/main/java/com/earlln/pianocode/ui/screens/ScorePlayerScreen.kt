package com.earlln.pianocode.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earlln.pianocode.music.Instrument
import com.earlln.pianocode.music.omr.ScoreEvent
import com.earlln.pianocode.score.ReadingStage
import com.earlln.pianocode.score.ScorePlayerState
import com.earlln.pianocode.score.ScorePlayerViewModel
import com.earlln.pianocode.ui.components.EventHit
import com.earlln.pianocode.ui.components.SectionHeader
import com.earlln.pianocode.ui.components.SheetSourcePicker
import com.earlln.pianocode.ui.components.StaffTap
import com.earlln.pianocode.ui.components.StaffTheme
import com.earlln.pianocode.ui.components.StaffWord
import com.earlln.pianocode.ui.components.drawStaffLine
import com.earlln.pianocode.ui.components.rememberSheetSourceOpener
import com.earlln.pianocode.ui.components.tapStaff
import kotlin.math.roundToInt

/**
 * Plays the music written on a page, shows what it made of it, and lets that be corrected.
 *
 * Its own screen, and its own reading of the photograph. The converter across the drawer
 * reads the chord symbols printed above the staff and never looks at the notes; this reads
 * the notes and never looks at the symbols.
 *
 * The page scrolls as one piece rather than as a list, because following the playback
 * means scrolling to a staff that is part-way down it, and that is a plain measurement on
 * a scrolling column and a fight with a lazy one.
 */
@Composable
fun ScorePlayerScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ScorePlayerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val scroll = rememberScrollState()

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

    // Where each staff sits in the window right now, so playback can bring the one being
    // heard into view. Measured against the window rather than against the whole page:
    // that way the answer needs no arithmetic with the scroll position, which is the part
    // that goes wrong.
    val staffInWindow = remember { mutableStateMapOf<Int, Float>() }
    var windowTop by remember { mutableStateOf(0f) }
    val soundingStaff = state.timeline
        .firstOrNull { it.event.id in state.sounding }
        ?.event
        ?.staffIndex

    LaunchedEffect(soundingStaff) {
        val y = soundingStaff?.let { staffInWindow[it] } ?: return@LaunchedEffect
        scroll.animateScrollBy(y - STAFF_MARGIN)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding)
            .onGloballyPositioned { windowTop = it.positionInRoot().y },
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(
                title = "악보 연주",
                subtitle = "악보에 적힌 음표를 읽어서 그대로 들려줍니다",
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showSources = true }, modifier = Modifier.fillMaxWidth()) {
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

        if (state.stage == ReadingStage.LOADING || state.stage == ReadingStage.READING) {
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

        state.page?.let { page ->
            val marks = state.timeline.filter { it.event.id in state.sounding }.map { it.event }
            val accent = MaterialTheme.colorScheme.primary
            Image(
                bitmap = page.asImageBitmap(),
                contentDescription = "불러온 악보",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    // The same playhead, on the page it came from. Following along on the
                    // photograph is what most people mean by following along.
                    .drawWithContent {
                        drawContent()
                        if (marks.isEmpty()) return@drawWithContent
                        val scale = size.width / page.width
                        val radius = (size.width * 0.018f).coerceAtLeast(6f)
                        for (mark in marks) {
                            drawCircle(
                                color = accent.copy(alpha = 0.35f),
                                radius = radius * 1.9f,
                                center = Offset(
                                    (mark.x * scale).toFloat(),
                                    (mark.y * scale).toFloat(),
                                ),
                            )
                        }
                    },
            )
        }

        if (state.pdfPageCount > 1) {
            Spacer(Modifier.height(16.dp))
            PdfPages(
                pageCount = state.pdfPageCount,
                page = state.pdfPage,
                onSelect = viewModel::openPdfPage,
            )
        }

        if (state.score != null) {
            Spacer(Modifier.height(16.dp))
            ReadingCard(state = state, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (state.hasNotes) {
            Spacer(Modifier.height(16.dp))
            ReadScoreView(
                state = state,
                onTap = { staffIndex, tap ->
                    when (tap) {
                        is StaffTap.OnEvent -> viewModel.select(tap.id)
                        is StaffTap.OnPosition ->
                            if (state.selected?.staffIndex == staffIndex) {
                                viewModel.setSelectedToStep(tap.step)
                            }
                    }
                },
                onStaffPlaced = { index, y -> staffInWindow[index] = y - windowTop },
                onShowNames = viewModel::setShowNames,
                onShowLyrics = viewModel::setShowLyrics,
            )

            Spacer(Modifier.height(8.dp))
            CorrectionBar(
                state = state,
                onNeighbour = viewModel::selectNeighbour,
                onStep = viewModel::moveSelectedByStep,
                onSemitone = viewModel::moveSelectedBySemitone,
                onLength = viewModel::setSelectedLength,
                onToggleRest = viewModel::toggleSelectedRest,
                onInsert = viewModel::insertAfterSelected,
                onDelete = viewModel::deleteSelected,
                onClose = { viewModel.select(null) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

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

        state.message?.let { message ->
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

        Spacer(Modifier.height(16.dp))
        LimitsCard(Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(40.dp))
    }
}

/**
 * The reading, drawn back as music laid out the way the page lays it out.
 *
 * One row a staff, each note at the fraction across the page it was read from, so the
 * drawing breaks where the original breaks and a reader can hold the two side by side.
 * Its predecessor put every note of the piece on one endless line ordered by time, which
 * was unreadable against the paper it came from.
 */
@Composable
private fun ReadScoreView(
    state: ScorePlayerState,
    onTap: (Int, StaffTap) -> Unit,
    onStaffPlaced: (Int, Float) -> Unit,
    onShowNames: (Boolean) -> Unit,
    onShowLyrics: (Boolean) -> Unit,
) {
    val score = state.score ?: return
    val timeline = state.timeline
    val density = LocalDensity.current
    val space = with(density) { 13.dp.toPx() }
    val padding = with(density) { 14.dp.toPx() }
    val rowHeight = space * 12
    val rowHeightDp = with(density) { rowHeight.toDp() }

    val theme = StaffTheme(
        ink = MaterialTheme.colorScheme.onSurface,
        playing = MaterialTheme.colorScheme.primary,
        selected = MaterialTheme.colorScheme.tertiary,
        faint = MaterialTheme.colorScheme.outlineVariant,
        lyric = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(Modifier.fillMaxWidth()) {
        Text(
            "읽은 악보",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "원본과 같은 줄 나눔으로 그렸습니다. 음표를 누르면 골라지고, " +
                "그 상태에서 오선의 다른 줄이나 칸을 누르면 그 높이로 옮겨집니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("음 이름", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.showNames, onCheckedChange = onShowNames)
            Spacer(Modifier.width(16.dp))
            Text("가사", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = state.showLyrics, onCheckedChange = onShowLyrics)
        }

        score.staves.forEachIndexed { index, readStaff ->
            val staff = readStaff.staff
            val words = if (state.showLyrics) state.lyrics[index].orEmpty() else emptyList()
            var widthPx by remember(index) { mutableStateOf(0) }

            fun pageToX(pageX: Double): Float {
                if (widthPx == 0) return padding
                val span = (staff.right - staff.left).coerceAtLeast(1).toDouble()
                val usable = widthPx - padding * 2
                return padding + (((pageX - staff.left) / span) * usable).toFloat()
            }

            val staffTop = space * 3.5f
            val staffBottom = staffTop + space * 4
            fun stepAt(y: Float): Int = ((staffBottom - y) / (space / 2f)).roundToInt()

            val rowEvents = timeline.filter { it.event.staffIndex == index }
            val hits = rowEvents.map { timed ->
                val x = pageToX(timed.event.x)
                EventHit(
                    id = timed.event.id,
                    // Generous either side: a finger is wider than a note head, and this
                    // is the difference between correcting a reading and fighting it.
                    bounds = Rect(x - space * 1.6f, 0f, x + space * 1.6f, rowHeight),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "${index + 1}번째 줄 · ${readStaff.clef.koreanName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp)
                    .onSizeChanged { widthPx = it.width }
                    .onGloballyPositioned { onStaffPlaced(index, it.positionInRoot().y) }
                    .tapStaff(
                        hits = { hits },
                        stepAt = ::stepAt,
                        onTap = { onTap(index, it) },
                    ),
            ) {
                drawStaffLine(
                    events = rowEvents,
                    clef = readStaff.clef,
                    theme = theme,
                    space = space,
                    top = staffTop,
                    pageToX = { pageToX(it) },
                    selectedId = state.selectedId,
                    playingIds = state.sounding,
                    showNames = state.showNames,
                    words = words.map {
                        StaffWord(it.text, pageToX((it.left + it.right) / 2.0))
                    },
                )
            }
        }
    }
}

/**
 * What can be done to the note the reader has picked out.
 *
 * Everything here is a button, because this is used on a phone with a photograph of a
 * hymn book propped up beside it. Nothing is typed and nothing needs a precise tap: the
 * arrows walk the reading note by note, so even a note too small to hit can be reached.
 */
@Composable
private fun CorrectionBar(
    state: ScorePlayerState,
    onNeighbour: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
    onSemitone: (Int) -> Unit,
    onLength: (Double) -> Unit,
    onToggleRest: () -> Unit,
    onInsert: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val event = state.selected
                Text(
                    when {
                        event == null -> "고칠 음표를 고르세요"
                        event.isRest -> "쉼표 · ${ScoreEvent.lengthName(event.quarters)}쉼표"
                        else -> event.pitches.joinToString(" ") {
                            "${it.note.prettyName}${it.octave}"
                        } + " · ${ScoreEvent.lengthName(event.quarters)}음표"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (event != null) TextButton(onClick = onClose) { Text("선택 해제") }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onNeighbour(false) }, modifier = Modifier.weight(1f)) {
                    Text("◀ 이전 음표")
                }
                OutlinedButton(onClick = { onNeighbour(true) }, modifier = Modifier.weight(1f)) {
                    Text("다음 음표 ▶")
                }
            }

            val event = state.selected ?: return@Column

            if (!event.isRest) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "음 높이",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onStep(1) }, modifier = Modifier.weight(1f)) {
                        Text("한 칸 ▲")
                    }
                    OutlinedButton(onClick = { onStep(-1) }, modifier = Modifier.weight(1f)) {
                        Text("한 칸 ▼")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onSemitone(1) }, modifier = Modifier.weight(1f)) {
                        Text("반음 ♯")
                    }
                    OutlinedButton(onClick = { onSemitone(-1) }, modifier = Modifier.weight(1f)) {
                        Text("반음 ♭")
                    }
                    OutlinedButton(onClick = { onStep(7) }, modifier = Modifier.weight(1f)) {
                        Text("한 옥타브 ▲")
                    }
                    OutlinedButton(onClick = { onStep(-7) }, modifier = Modifier.weight(1f)) {
                        Text("▼")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "길이",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(ScoreEvent.LENGTHS, key = { it }) { quarters ->
                    FilterChip(
                        selected = quarters == event.quarters,
                        onClick = { onLength(quarters) },
                        label = { Text(ScoreEvent.lengthName(quarters)) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onToggleRest, modifier = Modifier.weight(1f)) {
                    Text(if (event.isRest) "음표로" else "쉼표로")
                }
                OutlinedButton(onClick = onInsert, modifier = Modifier.weight(1f)) {
                    Text("뒤에 추가")
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Text("지우기")
                }
            }
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
                "오선 ${score.staves.size}줄 · 음표 ${score.notes.size}개" +
                    (if (state.edited) " · 직접 고침" else ""),
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
 * conclude the app is broken the first time a tie goes missing.
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
                "가사는 있는 그대로 옮겨 적을 뿐, 음표에 맞춰 나누지는 않습니다.",
                "손으로 쓴 악보나 기울어진 사진은 인식률이 크게 떨어집니다.",
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

/** How far below the top of the window the staff being played is brought to rest. */
private const val STAFF_MARGIN = 160f

private val TEMPO_CHOICES = listOf(60, 80, 100, 120, 144)
private val TRANSPOSE_CHOICES = listOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
