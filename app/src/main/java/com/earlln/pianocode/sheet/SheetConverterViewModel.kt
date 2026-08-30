package com.earlln.pianocode.sheet

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earlln.pianocode.music.ChordConversion
import com.earlln.pianocode.music.ConversionMode
import com.earlln.pianocode.music.Key
import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.ScaleType
import com.earlln.pianocode.music.Transposer
import com.earlln.pianocode.util.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the converter is in its pick → read → convert → save flow. */
enum class ConverterStage { EMPTY, ANALYZING, READY, RENDERING }

data class SheetConverterState(
    val stage: ConverterStage = ConverterStage.EMPTY,
    val sourceBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val detected: List<DetectedChord> = emptyList(),
    val missed: List<MissedCandidate> = emptyList(),
    val disabledIds: Set<String> = emptySet(),
    val sourceKey: Key = Key(Note(0, 0), ScaleType.MAJOR),
    val targetKey: Key = Key(Note(4, 0), ScaleType.MAJOR),
    val mode: ConversionMode = ConversionMode.TRANSPOSE,
    val keyWasDetected: Boolean = false,
    val markConverted: Boolean = true,
    val markingColor: MarkingColor = MarkingColor.VIOLET,
    val message: String? = null,
) {
    val enabled: List<DetectedChord> get() = detected.filterNot { it.id in disabledIds }

    /** The before/after pairs shown in the list, in reading order. */
    val conversions: List<Pair<DetectedChord, ChordConversion>>
        get() = detected.map { it to Transposer.convert(it.chord, sourceKey, targetKey, mode) }

    val changedCount: Int
        get() = conversions.count { (detected, conversion) ->
            detected.id !in disabledIds && conversion.changed
        }

    /** Semitones the transpose mode will move everything by, written the short way round. */
    val semitoneShift: Int get() = sourceKey.signedSemitonesTo(targetKey)

    /** `+3반음`, `-2반음`, `같은 높이` — how the move reads to a player. */
    val shiftText: String
        get() = when {
            semitoneShift > 0 -> "+${semitoneShift}반음"
            semitoneShift < 0 -> "${semitoneShift}반음"
            else -> "같은 높이"
        }

    /**
     * How many symbols will stay in the original key: ones the recogniser could not read,
     * plus ones the user switched off. Any of these leaves the page in two keys at once,
     * so the screen shows this count rather than letting it pass unnoticed.
     */
    val leftBehindCount: Int get() = missed.size + disabledIds.size

    /** The line stamped across the top of the converted page. */
    val banner: String
        get() = "PianoCode · ${sourceKey.shortName} → ${targetKey.shortName} ($shiftText)" +
            (if (markConverted) " · ${markingColor.koreanName}이 바뀐 코드" else "") +
            " · 코드 심볼만 변경 (오선보 조표·음표는 원본 그대로)"
}

/**
 * Drives the sheet converter: reads chords off a photo, rewrites them into the chosen
 * scale, and paints the result back onto the page.
 */
class SheetConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val recognizer = SheetChordRecognizer()
    private val _state = MutableStateFlow(SheetConverterState())
    val state: StateFlow<SheetConverterState> = _state.asStateFlow()

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = ConverterStage.ANALYZING,
                    resultBitmap = null,
                    detected = emptyList(),
                    missed = emptyList(),
                    disabledIds = emptySet(),
                    message = null,
                )
            }
            val context = getApplication<Application>()
            val bitmap = withContext(Dispatchers.IO) { ImageIo.loadBitmap(context, uri) }
            if (bitmap == null) {
                _state.update {
                    it.copy(stage = ConverterStage.EMPTY, message = "이미지를 열 수 없습니다.")
                }
                return@launch
            }

            val scan = try {
                recognizer.recognize(bitmap)
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        stage = ConverterStage.READY,
                        sourceBitmap = bitmap,
                        message = "글자 인식에 실패했습니다: ${error.message ?: "알 수 없는 오류"}",
                    )
                }
                return@launch
            }

            val detected = scan.chords
            val detectedKey = Transposer.detectKey(detected.map { it.chord })
            // A page already written in violet would swallow the default marking, so the
            // colour is chosen against this page's own ink rather than assumed.
            val marking = withContext(Dispatchers.Default) {
                SheetRenderer.pickMarkingColor(bitmap, detected.map { it.bounds })
            }
            _state.update { current ->
                current.copy(
                    stage = ConverterStage.READY,
                    sourceBitmap = bitmap,
                    detected = detected,
                    missed = scan.missed,
                    sourceKey = detectedKey ?: current.sourceKey,
                    keyWasDetected = detectedKey != null,
                    markingColor = marking,
                    message = if (detected.isEmpty()) {
                        "코드를 찾지 못했습니다. 코드 심볼이 또렷하게 보이는 사진으로 다시 시도해 보세요."
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun toggleChord(id: String) {
        _state.update { current ->
            val disabled = if (id in current.disabledIds) {
                current.disabledIds - id
            } else {
                current.disabledIds + id
            }
            current.copy(disabledIds = disabled, resultBitmap = null)
        }
    }

    fun setSourceKey(key: Key) =
        _state.update { it.copy(sourceKey = key, keyWasDetected = false, resultBitmap = null) }

    fun setTargetKey(key: Key) = _state.update { it.copy(targetKey = key, resultBitmap = null) }

    fun setMode(mode: ConversionMode) = _state.update { it.copy(mode = mode, resultBitmap = null) }

    /** Whether converted symbols are written in the highlight colour or the page's own ink. */
    fun setMarkConverted(mark: Boolean) =
        _state.update { it.copy(markConverted = mark, resultBitmap = null) }

    /** Overrides the colour chosen for this page. */
    fun setMarkingColor(color: MarkingColor) =
        _state.update { it.copy(markingColor = color, resultBitmap = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Surfaces a problem the UI hit before the view model was involved. */
    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    /** Turns every recognised chord back on, after the user has switched some off. */
    fun enableAll() = _state.update { it.copy(disabledIds = emptySet(), resultBitmap = null) }

    /** Paints the converted symbols onto a copy of the page. */
    fun renderResult() {
        val current = _state.value
        val source = current.sourceBitmap ?: return
        if (current.enabled.isEmpty()) {
            _state.update { it.copy(message = "변환할 코드를 하나 이상 선택해 주세요.") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(stage = ConverterStage.RENDERING) }
            val replacements = current.conversions
                .filter { (detected, _) -> detected.id !in current.disabledIds }
                .map { (detected, conversion) ->
                    ChordReplacement(detected.bounds, conversion.converted.symbol)
                }
            val rendered = withContext(Dispatchers.Default) {
                SheetRenderer.render(
                    source = source,
                    replacements = replacements,
                    banner = current.banner,
                    highlightInk = if (current.markConverted) current.markingColor.argb else null,
                )
            }
            _state.update {
                it.copy(stage = ConverterStage.READY, resultBitmap = rendered)
            }
        }
    }

    fun saveResult(onDone: (Boolean) -> Unit) {
        val bitmap = _state.value.resultBitmap ?: return onDone(false)
        viewModelScope.launch {
            val context = getApplication<Application>()
            val name = "PianoCode_${System.currentTimeMillis()}.png"
            val uri = withContext(Dispatchers.IO) {
                ImageIo.saveToGallery(context, bitmap, name)
            }
            _state.update {
                it.copy(
                    message = if (uri != null) {
                        "갤러리의 PianoCode 앨범에 저장했습니다."
                    } else {
                        "저장에 실패했습니다."
                    },
                )
            }
            onDone(uri != null)
        }
    }

    override fun onCleared() {
        recognizer.close()
        super.onCleared()
    }
}
