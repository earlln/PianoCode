package com.earlln.pianocode.score

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earlln.pianocode.music.Instrument
import com.earlln.pianocode.music.MidiWriter
import com.earlln.pianocode.music.omr.KeySignature
import com.earlln.pianocode.music.omr.MonoImage
import com.earlln.pianocode.music.omr.ReadScore
import com.earlln.pianocode.music.omr.ScoreEdits
import com.earlln.pianocode.music.omr.ScoreEvent
import com.earlln.pianocode.music.omr.ScoreReader
import com.earlln.pianocode.music.omr.TimedEvent
import com.earlln.pianocode.sheet.SheetPlayer
import com.earlln.pianocode.util.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReadingStage { IDLE, LOADING, READING, DONE }

data class ScorePlayerState(
    val stage: ReadingStage = ReadingStage.IDLE,
    val page: Bitmap? = null,
    val score: ReadScore? = null,
    /** The reading as it came out, kept so a correction can be told from a reading. */
    val asRead: ReadScore? = null,
    val instrument: Instrument = Instrument.PIANO,
    val tempo: Int = 90,
    /** Semitones to move everything by, so a piece can be played where it can be sung. */
    val transpose: Int = 0,
    val playing: Boolean = false,
    /** How far into the piece the sound has got, so the screen can point at it. */
    val playheadQuarters: Double = 0.0,
    /** The event the reader has picked out to correct, if any. */
    val selectedId: Int? = null,
    val showNames: Boolean = true,
    val edited: Boolean = false,
    val pdfPageCount: Int = 0,
    val pdfPage: Int = 0,
    val message: String? = null,
) {
    val notes: Int get() = score?.notes?.size ?: 0
    val hasNotes: Boolean get() = notes > 0

    val timeline: List<TimedEvent> get() = score?.timeline.orEmpty()

    val selected: ScoreEvent? get() = score?.events?.firstOrNull { it.id == selectedId }

    /** The key of the staff the selected event is on, which its pitches are spelled in. */
    val selectedKey: KeySignature
        get() = selected?.let { score?.staves?.getOrNull(it.staffIndex)?.key } ?: KeySignature()

    /** Events sounding right now, which is what the score and the photo both highlight. */
    val sounding: Set<Int>
        get() = if (!playing) emptySet()
        else timeline.filter { it.covers(playheadQuarters) }.map { it.event.id }.toSet()

    /**
     * Events that no longer say what the reading said.
     *
     * Only these paint over the page: a note left as it was read has nothing to correct,
     * and covering the printed note would throw away the very thing being checked against.
     */
    val changed: Set<Int>
        get() {
            val before = asRead?.events?.associateBy { it.id } ?: return emptySet()
            return score?.events.orEmpty()
                .filter { now ->
                    val was = before[now.id]
                    was == null || was.pitches != now.pitches || was.quarters != now.quarters
                }
                .map { it.id }
                .toSet()
        }
}

/**
 * Reads the notes off a page and plays them.
 *
 * This is deliberately a different screen from the chord converter, and a different piece
 * of code. The converter reads the chord symbols printed above the staff and never looks
 * at the music; this reads the music and never looks at the symbols. They answer different
 * questions about the same photograph and share nothing but the file picker.
 */
class ScorePlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ScorePlayerState())
    val state: StateFlow<ScorePlayerState> = _state.asStateFlow()

    private val playerLazy = lazy { SheetPlayer(application, fileName = "score.mid") }
    private val player: SheetPlayer get() = playerLazy.value
    private var sourceUri: Uri? = null
    private var playhead: Job? = null

    fun load(uri: Uri, page: Int = 0) {
        sourceUri = uri
        stop()
        viewModelScope.launch {
            _state.update { it.copy(stage = ReadingStage.LOADING, message = null) }
            val context = getApplication<Application>()
            val pages = withContext(Dispatchers.IO) {
                if (ImageIo.isPdf(context, uri)) ImageIo.pdfPageCount(context, uri) else 0
            }
            val bitmap = withContext(Dispatchers.IO) {
                ImageIo.loadBitmap(context, uri, page = page)
            }
            if (bitmap == null) {
                _state.update {
                    it.copy(stage = ReadingStage.IDLE, message = "이 파일을 열지 못했습니다.")
                }
                return@launch
            }

            _state.update {
                it.copy(
                    stage = ReadingStage.READING,
                    page = bitmap,
                    score = null,
                    pdfPageCount = pages,
                    pdfPage = page,
                )
            }
            val score = withContext(Dispatchers.Default) { ScoreReader.read(bitmap.toMono()) }
            _state.update {
                it.copy(
                    stage = ReadingStage.DONE,
                    score = score,
                    asRead = score,
                    selectedId = null,
                    edited = false,
                    message = if (score.isEmpty) NOTHING_READ else null,
                )
            }
        }
    }

    fun openPdfPage(page: Int) {
        val uri = sourceUri ?: return
        if (page == _state.value.pdfPage) return
        load(uri, page)
    }

    // --- correcting what was read ------------------------------------------

    fun select(id: Int?) = _state.update { it.copy(selectedId = if (it.selectedId == id) null else id) }

    fun setShowNames(show: Boolean) = _state.update { it.copy(showNames = show) }

    /**
     * Puts the picked note on the line or space that was tapped.
     *
     * This is the repair without a keyboard: pick the note, tap where it belongs. The
     * position is what the staff is written in, so the pitch follows from the clef and
     * whatever the key signature says that letter is.
     */
    fun setSelectedToStep(step: Int) {
        val current = _state.value
        val score = current.score ?: return
        val event = current.selected ?: return
        if (event.isRest) return
        val clef = score.staves.getOrNull(event.staffIndex)?.clef ?: return
        val from = clef.stepOf(event.pitches.first())
        moveSelectedByStep(step - from)
    }

    /** Steps the picked note along the reading, so nothing depends on hitting it exactly. */
    fun selectNeighbour(forward: Boolean) {
        val events = _state.value.score?.events.orEmpty()
        if (events.isEmpty()) return
        val at = events.indexOfFirst { it.id == _state.value.selectedId }
        val next = when {
            at < 0 -> 0
            forward -> (at + 1).coerceAtMost(events.lastIndex)
            else -> (at - 1).coerceAtLeast(0)
        }
        _state.update { it.copy(selectedId = events[next].id) }
    }

    /** Moves the picked note up or down the staff, which fixes a head read a line off. */
    fun moveSelectedByStep(steps: Int) = edit { score, id ->
        ScoreEdits.byStep(score.events, id, steps, _state.value.selectedKey)
    }

    /** Raises or lowers it a semitone, which fixes a sharp or flat that was missed. */
    fun moveSelectedBySemitone(semitones: Int) = edit { score, id ->
        ScoreEdits.bySemitone(score.events, id, semitones)
    }

    fun setSelectedLength(quarters: Double) = edit { score, id ->
        ScoreEdits.setLength(score.events, id, quarters)
    }

    fun toggleSelectedRest() = edit { score, id ->
        val fallback = score.events.firstOrNull { it.id == id }?.pitches?.firstOrNull()
            ?: score.staves.getOrNull(0)?.clef?.pitchAt(4)
            ?: return@edit score.events
        ScoreEdits.toggleRest(score.events, id, fallback)
    }

    fun deleteSelected() {
        val id = _state.value.selectedId ?: return
        edit { score, _ -> ScoreEdits.remove(score.events, id) }
        _state.update { it.copy(selectedId = null) }
    }

    fun insertAfterSelected() = edit { score, id -> ScoreEdits.insertAfter(score.events, id) }

    /** Throws away every correction and goes back to what was read off the page. */
    fun revertEdits() {
        val original = _state.value.asRead ?: return
        if (_state.value.playing) stop()
        _state.update { it.copy(score = original, edited = false, selectedId = null) }
    }

    /**
     * Applies one correction.
     *
     * Playback stops first. Carrying on through an edit would keep sounding the file
     * written before it, so what is heard would disagree with what is shown — and the
     * whole point of correcting here is that the two agree.
     */
    private inline fun edit(change: (ReadScore, Int) -> List<ScoreEvent>) {
        val current = _state.value
        val score = current.score ?: return
        val id = current.selectedId ?: return
        if (current.playing) stop()
        _state.update {
            it.copy(score = score.withEvents(change(score, id)), edited = true)
        }
    }

    // --- hearing it ---------------------------------------------------------

    fun setInstrument(instrument: Instrument) = restarting {
        _state.update { it.copy(instrument = instrument) }
    }

    fun setTempo(bpm: Int) = restarting {
        _state.update { it.copy(tempo = bpm) }
    }

    fun setTranspose(semitones: Int) = restarting {
        _state.update { it.copy(transpose = semitones.coerceIn(-12, 12)) }
    }

    fun togglePlayback() {
        if (_state.value.playing) stop() else start()
    }

    private inline fun restarting(change: () -> Unit) {
        change()
        if (_state.value.playing) {
            stop()
            start()
        }
    }

    private fun start() {
        val current = _state.value
        val notes = current.score?.notes.orEmpty()
        if (notes.isEmpty()) {
            _state.update { it.copy(message = NOTHING_READ) }
            return
        }
        val midi = MidiWriter.score(
            notes = notes.map {
                MidiWriter.TimedNote(
                    midi = it.pitch.midi + current.transpose,
                    startQuarters = it.startQuarters,
                    quarters = it.quarters,
                )
            },
            instrument = current.instrument,
            bpm = current.tempo,
        )
        val started = player.play(midi) { finished() }
        if (started) followPlayhead(current.tempo)
        _state.update {
            if (started) {
                it.copy(playing = true, playheadQuarters = 0.0)
            } else {
                it.copy(
                    playing = false,
                    message = "이 기기에서 소리를 낼 수 없습니다. " +
                        "안드로이드 내장 신시사이저가 없는 기기일 수 있습니다.",
                )
            }
        }
    }

    fun stop() {
        playhead?.cancel()
        playhead = null
        if (playerLazy.isInitialized()) player.stop()
        _state.update { it.copy(playing = false, playheadQuarters = 0.0) }
    }

    private fun finished() {
        playhead?.cancel()
        playhead = null
        _state.update { it.copy(playing = false, playheadQuarters = 0.0) }
    }

    /**
     * Keeps the screen's idea of "now" in step with what is being heard.
     *
     * The position is asked of the player rather than counted here, because a count of our
     * own drifts against the audio and a playhead that has drifted is worse than none: it
     * points confidently at the wrong note.
     */
    private fun followPlayhead(bpm: Int) {
        playhead?.cancel()
        playhead = viewModelScope.launch {
            val perQuarter = 60_000.0 / bpm.coerceIn(20, 300)
            while (isActive) {
                val quarters = player.positionMillis / perQuarter
                _state.update { it.copy(playheadQuarters = quarters) }
                delay(PLAYHEAD_STEP_MILLIS)
            }
        }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
        playhead?.cancel()
        if (playerLazy.isInitialized()) player.stop()
        super.onCleared()
    }
}

/**
 * Flattens a page to the grey the reader works in.
 *
 * Engraved music is black on white, so which channel the grey comes from hardly matters,
 * but the weighted average is what keeps a page photographed under warm light from turning
 * one colour of ink darker than another.
 */
private fun Bitmap.toMono(): MonoImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    val gray = IntArray(pixels.size)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        gray[i] = (red * 30 + green * 59 + blue * 11) / 100
    }
    return MonoImage.fromGray(width, height, gray)
}

/** Often enough to look continuous, rarely enough to cost nothing worth measuring. */
private const val PLAYHEAD_STEP_MILLIS = 60L

private const val NOTHING_READ =
    "이 페이지에서 음표를 찾지 못했습니다. 오선과 음표가 또렷하게 나온 " +
        "사진이나 PDF일수록 잘 읽습니다."
