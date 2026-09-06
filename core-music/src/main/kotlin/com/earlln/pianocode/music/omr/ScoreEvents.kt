package com.earlln.pianocode.music.omr

import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.Pitch

/**
 * One thing that happens on a staff: notes sounding together, or a silence.
 *
 * The reading is kept in this shape rather than as a flat list of notes with start times
 * because start times are a consequence, not a fact. Lengthen one note and everything
 * after it moves; storing the times instead of deriving them means every edit has to
 * rewrite them and any missed case leaves the piece quietly out of step.
 */
data class ScoreEvent(
    val id: Int,
    val staffIndex: Int,
    /** Empty for a rest. More than one for a chord. */
    val pitches: List<Pitch>,
    val quarters: Double,
    /** Where it was read from on the page, for pointing at it on the photograph. */
    val x: Double,
    val y: Double,
) {
    val isRest: Boolean get() = pitches.isEmpty()

    /** The written durations an event can be given, as an engraver would name them. */
    companion object {
        val LENGTHS = listOf(4.0, 3.0, 2.0, 1.5, 1.0, 0.75, 0.5, 0.25)

        fun lengthName(quarters: Double): String = when (quarters) {
            4.0 -> "온"
            3.0 -> "점2분"
            2.0 -> "2분"
            1.5 -> "점4분"
            1.0 -> "4분"
            0.75 -> "점8분"
            0.5 -> "8분"
            0.25 -> "16분"
            else -> "${quarters}박"
        }
    }
}

/** An event with the moment it starts, which is what a player and a playhead need. */
data class TimedEvent(val event: ScoreEvent, val startQuarters: Double) {
    val endQuarters: Double get() = startQuarters + event.quarters

    fun covers(quarters: Double): Boolean =
        quarters >= startQuarters && quarters < endQuarters
}

/**
 * Works out when each event happens.
 *
 * Systems follow one another and the staves inside a system all start together, so a
 * system lasts as long as its longest staff. Deriving this on demand means an edit only
 * has to change the event it is about; the timing follows on its own.
 */
object Timeline {

    fun of(events: List<ScoreEvent>, systems: List<List<Int>>): List<TimedEvent> {
        val byStaff = events.groupBy { it.staffIndex }
        val timed = mutableListOf<TimedEvent>()
        var systemStart = 0.0
        for (system in systems) {
            var systemEnd = systemStart
            for (staffIndex in system) {
                var time = systemStart
                for (event in byStaff[staffIndex].orEmpty()) {
                    timed += TimedEvent(event, time)
                    time += event.quarters
                }
                if (time > systemEnd) systemEnd = time
            }
            systemStart = systemEnd
        }
        return timed.sortedBy { it.startQuarters }
    }

    fun notesOf(events: List<ScoreEvent>, systems: List<List<Int>>): List<ReadNote> =
        of(events, systems)
            .flatMap { timed ->
                timed.event.pitches.map { pitch ->
                    ReadNote(
                        pitch = pitch,
                        startQuarters = timed.startQuarters,
                        quarters = timed.event.quarters,
                        staffIndex = timed.event.staffIndex,
                        x = timed.event.x,
                        y = timed.event.y,
                        eventId = timed.event.id,
                    )
                }
            }
            .sortedWith(compareBy({ it.startQuarters }, { it.pitch.midi }))

    fun lengthQuarters(events: List<ScoreEvent>, systems: List<List<Int>>): Double =
        of(events, systems).maxOfOrNull { it.endQuarters } ?: 0.0
}

/**
 * The corrections a reader can make to what the app thought it saw.
 *
 * Every one returns a new list rather than changing the old one, so the screen can hand
 * back the previous reading if an edit turns out to be the wrong guess.
 *
 * The two pitch edits are deliberately different questions. Moving by a **position** is
 * what fixes the common mistake — a head read one line off — and lands on whatever the key
 * signature says that letter is. Moving by a **semitone** is what fixes a missed sharp or
 * flat, and leaves the letter alone where it can.
 */
object ScoreEdits {

    /** Moves an event up or down the staff by [steps] lines-and-spaces. */
    fun byStep(events: List<ScoreEvent>, id: Int, steps: Int, key: KeySignature): List<ScoreEvent> =
        mapEvent(events, id) { event ->
            event.copy(pitches = event.pitches.map { stepped(it, steps, key) })
        }

    /** Raises or lowers an event by [semitones], keeping the letter where it can. */
    fun bySemitone(events: List<ScoreEvent>, id: Int, semitones: Int): List<ScoreEvent> =
        mapEvent(events, id) { event ->
            event.copy(pitches = event.pitches.map { altered(it, semitones) })
        }

    fun setLength(events: List<ScoreEvent>, id: Int, quarters: Double): List<ScoreEvent> =
        mapEvent(events, id) { it.copy(quarters = quarters.coerceIn(0.125, 8.0)) }

    /** Turns a note into a silence of the same length, or a silence back into a note. */
    fun toggleRest(events: List<ScoreEvent>, id: Int, whenEmpty: Pitch): List<ScoreEvent> =
        mapEvent(events, id) { event ->
            if (event.isRest) event.copy(pitches = listOf(whenEmpty))
            else event.copy(pitches = emptyList())
        }

    fun remove(events: List<ScoreEvent>, id: Int): List<ScoreEvent> =
        events.filterNot { it.id == id }

    /**
     * Puts a new event straight after [id], which is how a note the reader missed
     * altogether gets back in.
     */
    fun insertAfter(events: List<ScoreEvent>, id: Int): List<ScoreEvent> {
        val at = events.indexOfFirst { it.id == id }
        if (at < 0) return events
        val previous = events[at]
        val next = events.getOrNull(at + 1)
        val added = previous.copy(
            id = (events.maxOfOrNull { it.id } ?: 0) + 1,
            // Halfway to whatever comes next on the page, so the order on screen and the
            // order on the photograph stay the same.
            x = next?.takeIf { it.staffIndex == previous.staffIndex }
                ?.let { (previous.x + it.x) / 2 } ?: (previous.x + 1),
        )
        return events.toMutableList().apply { add(at + 1, added) }
    }

    private inline fun mapEvent(
        events: List<ScoreEvent>,
        id: Int,
        change: (ScoreEvent) -> ScoreEvent,
    ): List<ScoreEvent> = events.map { if (it.id == id) change(it) else it }

    private fun stepped(pitch: Pitch, steps: Int, key: KeySignature): Pitch {
        val diatonic = pitch.octave * 7 + pitch.note.letter + steps
        val letter = Math.floorMod(diatonic, 7)
        return Pitch(Note(letter, key.accidentalFor(letter)), Math.floorDiv(diatonic, 7))
    }

    private fun altered(pitch: Pitch, semitones: Int): Pitch {
        val moved = pitch.note.transposeBy(0, semitones)
        // Three sharps in a row is nobody's idea of a note; respell it as the sound it is.
        return if (moved.isPractical) {
            Pitch(moved, pitch.octave)
        } else {
            Pitch.ofMidi(pitch.midi + semitones, preferFlats = semitones < 0)
        }
    }
}
