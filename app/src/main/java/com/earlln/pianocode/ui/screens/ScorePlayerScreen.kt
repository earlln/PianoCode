package com.earlln.pianocode.ui.screens

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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.earlln.pianocode.music.Instrument
import com.earlln.pianocode.music.omr.ScoreEvent
import com.earlln.pianocode.score.ReadingStage
import com.earlln.pianocode.score.ScorePlayerState
import com.earlln.pianocode.score.ScorePlayerViewModel
import com.earlln.pianocode.ui.components.OverlayTheme
import com.earlln.pianocode.ui.components.PageTap
import com.earlln.pianocode.ui.components.PageTaps
import com.earlln.pianocode.ui.components.PageView
import com.earlln.pianocode.ui.components.SectionHeader
import com.earlln.pianocode.ui.components.SheetSourcePicker
import com.earlln.pianocode.ui.components.drawReadingOverlay
import com.earlln.pianocode.ui.components.eventCentre
import com.earlln.pianocode.ui.components.rememberSheetSourceOpener
import kotlin.math.max
import kotlin.math.min

/**
 * Plays the music written on a page, shows what it made of it, and lets that be corrected.
 *
 * The page itself is the working surface. An earlier version drew the music again from
 * scratch and it could never match — the clef, the key signature, the words and the
 * engraver's spacing are all things the reader does not model and could only approximate.
 * Marking the photograph instead makes the likeness exact, brings the lyrics along for
 * nothing, and turns a misreading into something plainly visible: a mark that is not on
 * the note it is meant to be.
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

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(contentPadding),
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

            if (state.page != null) {
                PageEditor(
                    state = state,
                    onSelect = viewModel::select,
                    onPosition = { staffIndex, step ->
                        if (state.selected?.staffIndex == staffIndex) {
                            viewModel.setSelectedToStep(step)
                        }
                    },
                    onShowNames = viewModel::setShowNames,
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
                ReadingCard(
                    state = state,
                    onRevert = viewModel::revertEdits,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.hasNotes) {
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
            // Room for the correction bar, which floats over the bottom of the screen.
            Spacer(Modifier.height(if (state.selectedId != null) 320.dp else 40.dp))
        }

        // Pinned rather than scrolled to. Reaching a control by scrolling took the music
        // being corrected off the screen, which is the one thing that has to stay in view.
        if (state.hasNotes && state.selectedId != null) {
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
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            )
        }
    }
}

/**
 * The page, with the reading marked on it, zoomable and tappable.
 *
 * It opens at the size the page really is — whole, so you can see where you are — and a
 * note that gets picked out brings the view in close, because correcting one is fiddly at
 * page size and pointless at any other time.
 */
@Composable
private fun PageEditor(
    state: ScorePlayerState,
    onSelect: (Int?) -> Unit,
    onPosition: (Int, Int) -> Unit,
    onShowNames: (Boolean) -> Unit,
) {
    val page = state.page ?: return
    val score = state.score

    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    val fit = if (viewWidth == 0) 0f else viewWidth.toFloat() / page.width

    // Read afresh wherever it is needed. The gesture handler outlives the composition it
    // was made in, so a view captured there would still be describing the old zoom.
    fun currentView() = PageView(
        fit = if (viewWidth == 0) 0f else viewWidth.toFloat() / page.width,
        scale = scale,
        offset = Offset(panX, panY),
    )

    fun clamp() {
        val width = page.width * fit * scale
        val height = page.height * fit * scale
        panX = if (width <= viewWidth) (viewWidth - width) / 2f
        else panX.coerceIn(viewWidth - width, 0f)
        panY = if (height <= viewHeight) 0f else panY.coerceIn(viewHeight - height, 0f)
    }

    fun centreOn(event: ScoreEvent, closerThan: Float?) {
        if (fit == 0f || score == null) return
        val centre = eventCentre(event, score.staves)
        closerThan?.let { scale = max(scale, it) }
        panX = viewWidth / 2f - centre.x * fit * scale
        panY = viewHeight / 2f - centre.y * fit * scale
        clamp()
    }

    // Bring a picked note in close, and centre the view on it.
    LaunchedEffect(state.selectedId, viewWidth) {
        state.selected?.let { centreOn(it, closerThan = ZOOMED_IN) }
    }

    // Follow the sound across the page. The zoom is left alone: whatever the reader chose
    // to look at it at is what they want to keep looking at it at.
    val sounding = state.sounding.firstOrNull()
    LaunchedEffect(sounding) {
        if (!state.playing || sounding == null) return@LaunchedEffect
        score?.events?.firstOrNull { it.id == sounding }?.let { centreOn(it, closerThan = null) }
    }

    val theme = OverlayTheme(
        read = MaterialTheme.colorScheme.primary,
        playing = MaterialTheme.colorScheme.error,
        selected = MaterialTheme.colorScheme.tertiary,
        changed = MaterialTheme.colorScheme.secondary,
        erase = MaterialTheme.colorScheme.surface,
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "악보",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text("음 이름", style = MaterialTheme.typography.labelMedium)
            Switch(checked = state.showNames, onCheckedChange = onShowNames)
        }
        Text(
            "원본 위에 앱이 읽은 음표를 표시했습니다. 음표를 누르면 골라지고, " +
                "그 상태에서 오선의 다른 줄이나 칸을 누르면 그 높이로 옮겨집니다. " +
                "두 손가락으로 벌리면 확대됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .aspectRatio(page.width.toFloat() / page.height)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .clipToBounds()
                .onSizeChanged {
                    viewWidth = it.width
                    viewHeight = it.height
                    clamp()
                }
                .pointerInput(score) {
                    detectTapGestures { point ->
                        val staves = score?.staves.orEmpty()
                        val tap = PageTaps.at(point, currentView(), score?.events.orEmpty(), staves)
                        when (tap) {
                            is PageTap.OnEvent -> onSelect(tap.id)
                            is PageTap.OnPosition -> onPosition(tap.staffIndex, tap.step)
                            PageTap.Elsewhere -> onSelect(null)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        // Zoom about the fingers, so the music under them stays put.
                        panX = centroid.x - (centroid.x - panX) * (next / scale) + pan.x
                        panY = centroid.y - (centroid.y - panY) * (next / scale) + pan.y
                        scale = next
                        clamp()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                translate(panX, panY) {
                    scale(scale = fit * scale, pivot = Offset.Zero) {
                        drawImage(page.asImageBitmap(), topLeft = Offset.Zero)
                    }
                }
                if (score != null) {
                    drawReadingOverlay(
                        events = score.events,
                        staves = score.staves,
                        view = currentView(),
                        theme = theme,
                        selectedId = state.selectedId,
                        playingIds = state.sounding,
                        changedIds = state.changed,
                        showNames = state.showNames,
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    scale = min(scale * 1.6f, MAX_ZOOM)
                    clamp()
                },
                modifier = Modifier.weight(1f),
            ) { Text("확대 ＋") }
            OutlinedButton(
                onClick = {
                    scale = max(scale / 1.6f, 1f)
                    clamp()
                },
                modifier = Modifier.weight(1f),
            ) { Text("축소 －") }
            OutlinedButton(
                onClick = {
                    scale = 1f
                    clamp()
                },
                modifier = Modifier.weight(1f),
            ) { Text("전체 보기") }
        }
    }
}

/**
 * What can be done to the note the reader has picked out.
 *
 * Everything is a button, because this is used on a phone with the page propped up beside
 * it, and nothing needs a precise tap: the arrows walk the reading note by note, so a note
 * too small to hit can still be reached.
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
    val event = state.selected ?: return
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (event.isRest) {
                        "쉼표 · ${ScoreEvent.lengthName(event.quarters)}쉼표"
                    } else {
                        event.pitches.joinToString(" ") { "${it.note.prettyName}${it.octave}" } +
                            " · ${ScoreEvent.lengthName(event.quarters)}음표"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("닫기") }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onNeighbour(false) }, modifier = Modifier.weight(1f)) {
                    Text("◀ 이전")
                }
                OutlinedButton(onClick = { onNeighbour(true) }, modifier = Modifier.weight(1f)) {
                    Text("다음 ▶")
                }
            }

            if (!event.isRest) {
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onStep(1) }, modifier = Modifier.weight(1f)) {
                        Text("한 칸 ▲")
                    }
                    OutlinedButton(onClick = { onStep(-1) }, modifier = Modifier.weight(1f)) {
                        Text("한 칸 ▼")
                    }
                    OutlinedButton(onClick = { onSemitone(1) }, modifier = Modifier.weight(1f)) {
                        Text("♯")
                    }
                    OutlinedButton(onClick = { onSemitone(-1) }, modifier = Modifier.weight(1f)) {
                        Text("♭")
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                items(ScoreEvent.LENGTHS, key = { it }) { quarters ->
                    FilterChip(
                        selected = quarters == event.quarters,
                        onClick = { onLength(quarters) },
                        label = { Text(ScoreEvent.lengthName(quarters)) },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.padding(horizontal = 14.dp),
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
private fun ReadingCard(
    state: ScorePlayerState,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    (if (state.edited) " · ${state.changed.size}개 직접 고침" else ""),
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
            if (state.edited) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRevert) { Text("고친 것 되돌리기") }
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
                "표시가 인쇄된 음표와 어긋나 있으면 그게 잘못 읽은 자리입니다.",
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

/** Close enough to work on a single note without hunting for it. */
private const val ZOOMED_IN = 2.6f
private const val MAX_ZOOM = 6f

private val TEMPO_CHOICES = listOf(60, 80, 100, 120, 144)
private val TRANSPOSE_CHOICES = listOf(-5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5)
