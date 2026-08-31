package com.earlln.pianocode.sheet

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.earlln.pianocode.music.Chord
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

/** Identifies a leftover by where it sits, which is stable for as long as the page is. */
internal val MissedCandidate.key: String
    get() = "${bounds.left}:${bounds.top}:${bounds.right}:${bounds.bottom}"

/** Where the converter is in its pick → read → convert → save flow. */
enum class ConverterStage { EMPTY, ANALYZING, READY, RENDERING }

/**
 * Where a chord chosen in the picker is written.
 *
 * Held as state rather than inferred at the moment of choosing. Reading it back from the
 * selection meant a chord could be picked with nothing left to write it to — the picker
 * silently did nothing, and the correction the user had just made was lost.
 */
sealed interface PickerTarget {
    /** A place on the page with no reading yet: a blank spot, or a flagged leftover. */
    data class Spot(val bounds: Rect, val readAs: String?) : PickerTarget

    /** Readings already found, to be told what they actually say. */
    data class Entries(val ids: Set<String>) : PickerTarget
}

/** Whether an entry came from reading the page or from the user placing it. */
enum class EntryOrigin { RECOGNISED, MANUAL }

/**
 * One chord the app believes the page carries.
 *
 * Editing works on these rather than on the drawn output, which is what makes a correction
 * stick: fix what the page is understood to say, press convert again, and the fix flows
 * through the transposition like every other chord. Painting over the result instead would
 * leave the app still believing the wrong thing underneath.
 */
data class SheetEntry(
    val id: String,
    val bounds: Rect,
    /** What the sheet says here, as read or as corrected by hand. */
    val original: Chord,
    /** The text the reader actually saw, kept so a misreading can be recognised as one. */
    val rawText: String,
    val confidence: Float,
    /** False for a reading the user rejected — notation mistaken for a chord. */
    val enabled: Boolean = true,
    val corrected: Boolean = false,
    val origin: EntryOrigin = EntryOrigin.RECOGNISED,
)

data class SheetConverterState(
    val stage: ConverterStage = ConverterStage.EMPTY,
    val sourceBitmap: Bitmap? = null,
    val resultBitmap: Bitmap? = null,
    val entries: List<SheetEntry> = emptyList(),
    val missed: List<MissedCandidate> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    /** Leftovers the user has declared not to be chords, so they stop being flagged. */
    val hiddenMissed: Set<String> = emptySet(),
    /** The leftover currently picked out in the editor, if any. */
    val selectedMissed: String? = null,
    val sourceKey: Key = Key(Note(0, 0), ScaleType.MAJOR),
    val targetKey: Key = Key(Note(4, 0), ScaleType.MAJOR),
    val mode: ConversionMode = ConversionMode.TRANSPOSE,
    val keyWasDetected: Boolean = false,
    val markConverted: Boolean = true,
    val markingColor: MarkingColor = MarkingColor.VIOLET,
    val editorOpen: Boolean = false,
    /** What the chord picker will write to, and null when it is not open. */
    val pickerTarget: PickerTarget? = null,
    val message: String? = null,
) {
    val enabledEntries: List<SheetEntry> get() = entries.filter { it.enabled }

    /** Every chord and where it lands, recomputed from the entries on every change. */
    val conversions: List<Pair<SheetEntry, ChordConversion>>
        get() = entries.map {
            it to Transposer.convert(it.original, sourceKey, targetKey, mode)
        }

    /** What the renderer paints: box and the symbol to write in it. */
    val replacements: List<Pair<Rect, String>>
        get() = conversions
            .filter { (entry, _) -> entry.enabled }
            .map { (entry, conversion) -> entry.bounds to conversion.converted.symbol }

    val changedCount: Int
        get() = conversions.count { (entry, conversion) -> entry.enabled && conversion.changed }

    val correctedCount: Int get() = entries.count { it.corrected }

    val manualCount: Int get() = entries.count { it.origin == EntryOrigin.MANUAL }

    val rejectedCount: Int get() = entries.count { !it.enabled }

    /**
     * Text that reads like a chord, no entry covers, and the user has not waved away.
     *
     * These are hints rather than output — nothing is painted at these spots — so the only
     * two useful answers are "yes, that is a chord" and "no, stop showing me that".
     */
    val openMissed: List<MissedCandidate>
        get() = missed.filter { candidate ->
            candidate.key !in hiddenMissed &&
                entries.none { overlaps(it.bounds, candidate.bounds) }
        }

    /**
     * How much of the page will stay in the original key: text that reads like a chord but
     * no entry covers, plus readings the user switched off.
     */
    val leftBehindCount: Int
        get() = missed.count { candidate ->
            candidate.key !in hiddenMissed &&
                entries.none { it.enabled && overlaps(it.bounds, candidate.bounds) }
        } + rejectedCount

    /** The size a chord occupies on this page, used to turn a tap into a box. */
    val typicalChordBounds: Rect?
        get() = entries.map { it.bounds }.takeIf { it.isNotEmpty() }?.let { boxes ->
            val heights = boxes.map { it.height() }.sorted()
            val widths = boxes.map { it.width() }.sorted()
            Rect(0, 0, widths[widths.size / 2], heights[heights.size / 2])
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

    /** The line stamped across the top of the converted page. */
    val banner: String
        get() = "PianoCode · ${sourceKey.shortName} → ${targetKey.shortName} ($shiftText)" +
            (if (markConverted) " · ${markingColor.koreanName}이 바뀐 코드" else "") +
            " · 코드 심볼만 변경 (오선보 조표·음표는 원본 그대로)"

    internal fun overlaps(a: Rect, b: Rect): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
}

/**
 * Drives the sheet converter: reads chords off a photo, lets the reading be corrected, and
 * paints the transposed chords back onto the page.
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
                    entries = emptyList(),
                    missed = emptyList(),
                    selectedIds = emptySet(),
                    hiddenMissed = emptySet(),
                    selectedMissed = null,
                    editorOpen = false,
                    pickerTarget = null,
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

            val entries = scan.chords.map {
                SheetEntry(
                    id = it.id,
                    bounds = it.bounds,
                    original = it.chord,
                    rawText = it.rawText,
                    confidence = it.confidence,
                )
            }
            val detectedKey = Transposer.detectKey(entries.map { it.original })
            val marking = withContext(Dispatchers.Default) {
                SheetRenderer.pickMarkingColor(bitmap, entries.map { it.bounds })
            }

            _state.update { current ->
                current.copy(
                    stage = ConverterStage.READY,
                    sourceBitmap = bitmap,
                    entries = entries,
                    missed = scan.missed,
                    sourceKey = detectedKey ?: current.sourceKey,
                    keyWasDetected = detectedKey != null,
                    markingColor = marking,
                    message = if (entries.isEmpty()) {
                        "코드를 찾지 못했습니다. 코드 심볼이 또렷하게 보이는 사진으로 다시 시도해 보세요."
                    } else {
                        null
                    },
                )
            }
        }
    }

    // --- settings -----------------------------------------------------------

    fun setSourceKey(key: Key) =
        _state.update { it.copy(sourceKey = key, keyWasDetected = false, resultBitmap = null) }

    fun setTargetKey(key: Key) = _state.update { it.copy(targetKey = key, resultBitmap = null) }

    fun setMode(mode: ConversionMode) = _state.update { it.copy(mode = mode, resultBitmap = null) }

    fun setMarkConverted(mark: Boolean) =
        _state.update { it.copy(markConverted = mark, resultBitmap = null) }

    fun setMarkingColor(color: MarkingColor) =
        _state.update { it.copy(markingColor = color, resultBitmap = null) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    // --- editing the reading ------------------------------------------------

    fun openEditor() = _state.update { it.copy(editorOpen = true) }

    fun closeEditor() = _state.update {
        it.copy(
            editorOpen = false,
            selectedIds = emptySet(),
            selectedMissed = null,
            pickerTarget = null,
        )
    }

    /**
     * Handles a tap on the page: choosing what to act on, never acting.
     *
     * A tap has to be safe. The page is read at least as often as it is corrected, and a
     * screen where brushing the paper opens a chord picker cannot be read on.
     */
    fun tapAt(spot: Rect) {
        val current = _state.value
        val hit = current.entries.firstOrNull { current.overlaps(it.bounds, spot) }
        if (hit != null) {
            _state.update {
                it.copy(
                    selectedMissed = null,
                    selectedIds = if (hit.id in it.selectedIds) {
                        it.selectedIds - hit.id
                    } else {
                        it.selectedIds + hit.id
                    },
                )
            }
            return
        }

        val leftover = current.openMissed.firstOrNull { current.overlaps(it.bounds, spot) }
        if (leftover != null) {
            _state.update {
                it.copy(
                    selectedIds = emptySet(),
                    selectedMissed = if (it.selectedMissed == leftover.key) null else leftover.key,
                )
            }
            return
        }

        _state.update { it.copy(selectedIds = emptySet(), selectedMissed = null) }
    }

    /**
     * Handles a press held on the page: says what belongs there, in one gesture.
     *
     * Holding a finger on a symbol is an unambiguous "this one", so it goes straight to the
     * picker rather than selecting and waiting to be told what to do with the selection.
     * Held on bare paper it offers a chord the reader never found.
     */
    fun holdAt(spot: Rect) {
        val current = _state.value
        val hit = current.entries.firstOrNull { current.overlaps(it.bounds, spot) }
        if (hit != null) {
            _state.update {
                it.copy(
                    selectedIds = setOf(hit.id),
                    selectedMissed = null,
                    pickerTarget = PickerTarget.Entries(setOf(hit.id)),
                )
            }
            return
        }

        val leftover = current.openMissed.firstOrNull { current.overlaps(it.bounds, spot) }
        if (leftover != null) {
            _state.update {
                it.copy(
                    selectedIds = emptySet(),
                    selectedMissed = null,
                    pickerTarget = PickerTarget.Spot(leftover.bounds, leftover.text),
                )
            }
            return
        }

        _state.update {
            it.copy(
                selectedIds = emptySet(),
                selectedMissed = null,
                pickerTarget = PickerTarget.Spot(
                    grow(spot, current.typicalChordBounds),
                    null,
                ),
            )
        }
    }

    fun clearSelection() =
        _state.update { it.copy(selectedIds = emptySet(), selectedMissed = null) }

    /**
     * Takes the picked leftover to be a chord after all, and asks what it says.
     *
     * The flag disappears on its own once an entry covers the spot.
     */
    fun adoptMissed() = _state.update { current ->
        val candidate = current.openMissed.firstOrNull { it.key == current.selectedMissed }
            ?: return@update current
        current.copy(
            pickerTarget = PickerTarget.Spot(candidate.bounds, candidate.text),
            selectedMissed = null,
        )
    }

    /** Waves the picked leftover away: notation or lyrics, never a chord. */
    fun dismissMissed() = _state.update { current ->
        val flagged = current.selectedMissed ?: return@update current
        current.copy(hiddenMissed = current.hiddenMissed + flagged, selectedMissed = null)
    }

    /** Opens the picker on the current selection, so it can be told what it really says. */
    fun beginCorrection() = _state.update { current ->
        if (current.selectedIds.isEmpty()) {
            current
        } else {
            current.copy(pickerTarget = PickerTarget.Entries(current.selectedIds))
        }
    }

    fun cancelPicker() = _state.update { it.copy(pickerTarget = null) }

    /**
     * Drops the selected readings — notation the reader mistook for chords.
     *
     * The same spot is usually also sitting in the leftovers list, so it is silenced too:
     * deleting a misreading only to have it come back as a flag would be no deletion at all.
     */
    fun deleteSelected() = _state.update { current ->
        val dropped = current.entries.filter { it.id in current.selectedIds }
        val silenced = current.missed
            .filter { candidate -> dropped.any { current.overlaps(it.bounds, candidate.bounds) } }
            .map { it.key }
        current.copy(
            entries = current.entries.filterNot { it.id in current.selectedIds },
            hiddenMissed = current.hiddenMissed + silenced,
            selectedIds = emptySet(),
            resultBitmap = null,
        )
    }

    /** Turns one reading on or off from the list on the settings screen. */
    fun toggleEntry(id: String) = _state.update { current ->
        current.copy(
            entries = current.entries.map {
                if (it.id == id) it.copy(enabled = !it.enabled) else it
            },
            resultBitmap = null,
        )
    }

    fun enableAll() = _state.update { current ->
        current.copy(entries = current.entries.map { it.copy(enabled = true) }, resultBitmap = null)
    }

    /**
     * Writes the chosen chord to whatever the picker was opened on.
     *
     * The conversion is not stored: only the corrected reading is. Pressing convert again
     * runs the whole page through the transposition afresh, so a fix here reaches the output
     * the same way a correct reading would have. A spot with no reading becomes one straight
     * away — enabled, counted, and converted like every chord the reader found itself.
     */
    fun applyChord(original: Chord) {
        when (val target = _state.value.pickerTarget) {
            null -> return

            is PickerTarget.Spot -> {
                val entry = SheetEntry(
                    id = "manual-${System.currentTimeMillis()}",
                    bounds = target.bounds,
                    original = original,
                    rawText = target.readAs ?: original.symbol,
                    confidence = 1f,
                    corrected = true,
                    origin = EntryOrigin.MANUAL,
                )
                // The pink flag under it goes on its own: openMissed drops any candidate
                // an entry covers, so the spot turns into a chord box the moment this lands.
                _state.update {
                    it.copy(
                        entries = it.entries + entry,
                        pickerTarget = null,
                        selectedIds = emptySet(),
                        selectedMissed = null,
                        resultBitmap = null,
                    )
                }
            }

            is PickerTarget.Entries -> _state.update {
                it.copy(
                    entries = it.entries.map { entry ->
                        if (entry.id in target.ids) {
                            entry.copy(original = original, corrected = true, enabled = true)
                        } else {
                            entry
                        }
                    },
                    pickerTarget = null,
                    selectedIds = emptySet(),
                    resultBitmap = null,
                )
            }
        }
    }

    /** Where a chord printed on this page lands under the current settings. */
    fun transposeForPage(chord: Chord): Chord = _state.value.let { current ->
        Transposer.convert(chord, current.sourceKey, current.targetKey, current.mode).converted
    }

    /** Grows a tap into the box a chord occupies on this page. */
    private fun grow(spot: Rect, typical: Rect?): Rect {
        if (spot.width() > 0 && spot.height() > 0) return spot
        val width = (typical?.width() ?: 90).coerceAtLeast(20)
        val height = (typical?.height() ?: 40).coerceAtLeast(12)
        return Rect(
            spot.left - width / 2,
            spot.top - height / 2,
            spot.left + width / 2,
            spot.top + height / 2,
        )
    }

    // --- output -------------------------------------------------------------

    fun renderResult() {
        val current = _state.value
        val source = current.sourceBitmap ?: return
        if (current.enabledEntries.isEmpty()) {
            _state.update { it.copy(message = "변환할 코드를 하나 이상 남겨 주세요.") }
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
            _state.update { it.copy(stage = ConverterStage.READY, resultBitmap = rendered) }
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
