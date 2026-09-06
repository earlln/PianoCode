package com.earlln.pianocode.score

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earlln.pianocode.music.Instrument
import com.earlln.pianocode.music.MidiWriter
import com.earlln.pianocode.music.omr.MonoImage
import com.earlln.pianocode.music.omr.ReadScore
import com.earlln.pianocode.music.omr.ScoreReader
import com.earlln.pianocode.sheet.SheetPlayer
import com.earlln.pianocode.util.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReadingStage { IDLE, LOADING, READING, DONE }

data class ScorePlayerState(
    val stage: ReadingStage = ReadingStage.IDLE,
    val page: Bitmap? = null,
    val score: ReadScore? = null,
    val instrument: Instrument = Instrument.PIANO,
    val tempo: Int = 90,
    /** Semitones to move everything by, so a piece can be played where it can be sung. */
    val transpose: Int = 0,
    val playing: Boolean = false,
    val pdfPageCount: Int = 0,
    val pdfPage: Int = 0,
    val message: String? = null,
) {
    val notes: Int get() = score?.notes?.size ?: 0
    val hasNotes: Boolean get() = notes > 0
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
        val started = player.play(midi) { _state.update { state -> state.copy(playing = false) } }
        _state.update {
            if (started) {
                it.copy(playing = true)
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
        if (playerLazy.isInitialized()) player.stop()
        _state.update { it.copy(playing = false) }
    }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    override fun onCleared() {
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

private const val NOTHING_READ =
    "이 페이지에서 음표를 찾지 못했습니다. 오선과 음표가 또렷하게 나온 " +
        "사진이나 PDF일수록 잘 읽습니다."
