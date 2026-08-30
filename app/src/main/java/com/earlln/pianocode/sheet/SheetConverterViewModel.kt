package com.earlln.pianocode.sheet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earlln.pianocode.music.Chord
import com.earlln.pianocode.music.ChordConversion
import com.earlln.pianocode.music.ChordParser
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

/**
 * A chord the reader missed or misread, written in by hand.
 *
 * Recognition will never catch every symbol on every page, and a page converted all but
 * three ways is still a page in two keys. Rather than chase the last few by tightening
 * filters — which costs correct readings elsewhere — the remaining ones are placed by hand,
 * and they are painted exactly like the automatic ones.
 */
data class ManualChord(
    val id: String,
    val bounds: Rect,
    /** What the page says there, as the user read it off the sheet. */
    val original: Chord,
    /** Where that lands in the target key — this is what gets drawn. */
    val converted: Chord,
)

/** A region the user has marked out, waiting for them to say what belongs there. */
data class PendingEdit(
    val bounds: Rect,
    /** What the page appears to say there, when the reader saw something it could not use. */
    val originalText: String? = null,
    /** What the reader thinks the page says there, offered as a one-tap answer. */
    val suggestion: Chord? = null,
    /** Set when the user is correcting an entry they already placed. */
    val replacingId: String? = null,
)

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
    val manualEdits: List<ManualChord> = emptyList(),
    val pendingEdit: PendingEdit? = null,
    val editMode: Boolean = false,
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

    /** True when two boxes cover mostly the same spot on the page. */
    private fun overlaps(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

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
    val leftBehindCount: Int
        get() = (missed.count { candidate -> manualEdits.none { overlaps(it.bounds, candidate.bounds) } } +
            disabledIds.size).coerceAtLeast(0)

    /** Everything that will be painted: the automatic conversions plus the hand-placed ones. */
    val replacements: List<Pair<Rect, String>>
        get() {
            val manual = manualEdits.map { it.bounds to it.converted.symbol }
            val automatic = conversions
                .filter { (detected, _) -> detected.id !in disabledIds }
                // A hand-placed chord wins over whatever the reader put in the same spot.
                .filter { (detected, _) -> manualEdits.none { overlaps(it.bounds, detected.bounds) } }
                .map { (detected, conversion) -> detected.bounds to conversion.converted.symbol }
            return automatic + manual
        }

    /** The typical size of a chord on this page, used to size a tap into a box. */
    val typicalChordBounds: Rect?
        get() = detected.map { it.bounds }.takeIf { it.isNotEmpty() }?.let { boxes ->
            val heights = boxes.map { it.height() }.sorted()
            val widths = boxes.map { it.width() }.sorted()
            Rect(0, 0, widths[widths.size / 2], heights[heights.size / 2])
        }

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
                    manualEdits = emptyList(),
                    pendingEdit = null,
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

    /** Turns hand-editing on or off. */
    fun setEditMode(on: Boolean) =
        _state.update { it.copy(editMode = on, pendingEdit = null) }

    /**
     * Opens the picker for a region the user marked on the page.
     *
     * When the reader saw text there but could not use it, that text is offered already
     * transposed, so the common case — a symbol it simply missed — is one more tap.
     */
    fun beginEdit(spot: Rect) {
        val current = _state.value
        // A tap arrives as a point. Grow it to the size a chord occupies on this page, which
        // is what pointing at one symbol means.
        val typical = current.typicalChordBounds
        val bounds = if (spot.width() > 0 && spot.height() > 0) {
            spot
        } else {
            val width = (typical?.width() ?: 90).coerceAtLeast(20)
            val height = (typical?.height() ?: 40).coerceAtLeast(12)
            Rect(
                spot.left - width / 2,
                spot.top - height / 2,
                spot.left + width / 2,
                spot.top + height / 2,
            )
        }
        val nearby = current.missed.firstOrNull { overlapsRect(it.bounds, bounds) }
        val parsed = nearby?.let { ChordParser.parse(it.text, requireUppercaseRoot = true) }
        _state.update {
            it.copy(
                pendingEdit = PendingEdit(
                    bounds = nearby?.bounds ?: bounds,
                    originalText = nearby?.text,
                    suggestion = parsed,
                ),
            )
        }
    }

    /** Where a chord printed on this page lands under the current settings. */
    fun transposeForPage(chord: Chord): Chord = _state.value.let { current ->
        Transposer.convert(chord, current.sourceKey, current.targetKey, current.mode).converted
    }

    /** Reopens the picker for a chord already placed by hand. */
    fun editExisting(id: String) {
        val existing = _state.value.manualEdits.firstOrNull { it.id == id } ?: return
        _state.update {
            it.copy(
                pendingEdit = PendingEdit(
                    bounds = existing.bounds,
                    suggestion = existing.original,
                    replacingId = id,
                ),
            )
        }
    }

    fun cancelEdit() = _state.update { it.copy(pendingEdit = null) }

    /**
     * Places the chord the page already carries at the marked spot.
     *
     * [original] is what the user read off the sheet; the conversion is worked out here so
     * they never have to do it themselves — which is the point of the app.
     */
    fun applyEdit(original: Chord) {
        val pending = _state.value.pendingEdit ?: return
        _state.update { current ->
            val id = pending.replacingId ?: "manual-${System.currentTimeMillis()}"
            val converted = Transposer.convert(
                original, current.sourceKey, current.targetKey, current.mode,
            ).converted
            current.copy(
                manualEdits = current.manualEdits.filterNot { it.id == id } +
                    ManualChord(id, pending.bounds, original, converted),
                pendingEdit = null,
                resultBitmap = null,
            )
        }
    }

    fun removeEdit(id: String) = _state.update {
        it.copy(manualEdits = it.manualEdits.filterNot { edit -> edit.id == id }, resultBitmap = null)
    }

    private fun overlapsRect(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

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
            val replacements = current.replacements.map { (bounds, symbol) ->
                ChordReplacement(bounds, symbol)
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
