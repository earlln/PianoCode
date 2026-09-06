package com.earlln.pianocode.music.omr

import com.earlln.pianocode.music.Note
import com.earlln.pianocode.music.Pitch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pitch(name: String, octave: Int, accidental: Int = 0): Pitch =
    Pitch(Note(Note.LETTER_NAMES.indexOf(name), accidental), octave)

private fun event(
    id: Int,
    pitches: List<Pitch>,
    quarters: Double = 1.0,
    staff: Int = 0,
    x: Double = id * 100.0,
) = ScoreEvent(id, staff, pitches, quarters, x, 0.0)

class TimelineTest {

    @Test
    fun `events on one staff follow one another`() {
        val events = listOf(
            event(1, listOf(pitch("C", 4)), quarters = 1.0),
            event(2, listOf(pitch("D", 4)), quarters = 2.0),
            event(3, listOf(pitch("E", 4)), quarters = 0.5),
        )

        val timed = Timeline.of(events, listOf(listOf(0)))

        assertEquals(listOf(0.0, 1.0, 3.0), timed.map { it.startQuarters })
        assertEquals(3.5, Timeline.lengthQuarters(events, listOf(listOf(0))), 0.0)
    }

    @Test
    fun `a rest takes its time without sounding`() {
        val events = listOf(
            event(1, listOf(pitch("C", 4))),
            event(2, emptyList(), quarters = 2.0),
            event(3, listOf(pitch("E", 4))),
        )

        val notes = Timeline.notesOf(events, listOf(listOf(0)))

        assertEquals(listOf("C4", "E4"), notes.map { it.pitch.toString() })
        assertEquals(listOf(0.0, 3.0), notes.map { it.startQuarters })
    }

    @Test
    fun `braced staves start together and the longer one sets the length`() {
        val events = listOf(
            event(1, listOf(pitch("C", 5)), quarters = 1.0, staff = 0),
            event(2, listOf(pitch("D", 5)), quarters = 1.0, staff = 0),
            event(3, listOf(pitch("C", 3)), quarters = 4.0, staff = 1),
        )

        val timed = Timeline.of(events, listOf(listOf(0, 1)))

        assertEquals(0.0, timed.first { it.event.id == 1 }.startQuarters, 0.0)
        assertEquals(0.0, timed.first { it.event.id == 3 }.startQuarters, 0.0)
        assertEquals(4.0, Timeline.lengthQuarters(events, listOf(listOf(0, 1))), 0.0)
    }

    @Test
    fun `a system waits for the one before it`() {
        val events = listOf(
            event(1, listOf(pitch("C", 4)), quarters = 2.0, staff = 0),
            event(2, listOf(pitch("G", 4)), quarters = 1.0, staff = 1),
        )

        val timed = Timeline.of(events, listOf(listOf(0), listOf(1)))

        assertEquals(2.0, timed.first { it.event.id == 2 }.startQuarters, 0.0)
    }

    @Test
    fun `an event says whether it is sounding at a moment`() {
        val timed = TimedEvent(event(1, listOf(pitch("C", 4)), quarters = 2.0), startQuarters = 1.0)

        assertFalse(timed.covers(0.9))
        assertTrue(timed.covers(1.0))
        assertTrue(timed.covers(2.9))
        assertFalse(timed.covers(3.0))
    }
}

class ScoreEditsTest {

    private val events = listOf(
        event(1, listOf(pitch("C", 4))),
        event(2, listOf(pitch("E", 4))),
        event(3, emptyList()),
    )

    @Test
    fun `a note read one line off is moved a position`() {
        val fixed = ScoreEdits.byStep(events, id = 2, steps = 1, key = KeySignature())

        assertEquals("F4", fixed[1].pitches.single().toString())
    }

    @Test
    fun `moving a position lands on what the key signature says`() {
        // One sharp, so the F of that line is an F sharp, not an F.
        val fixed = ScoreEdits.byStep(events, id = 2, steps = 1, key = KeySignature(sharps = 1))

        assertEquals("F#4", fixed[1].pitches.single().toString())
    }

    @Test
    fun `moving down past the bottom of an octave keeps counting`() {
        val fixed = ScoreEdits.byStep(events, id = 1, steps = -1, key = KeySignature())

        assertEquals("B3", fixed[0].pitches.single().toString())
    }

    @Test
    fun `a missed sharp is added without changing the letter`() {
        val fixed = ScoreEdits.bySemitone(events, id = 1, semitones = 1)

        assertEquals("C#4", fixed[0].pitches.single().toString())
    }

    @Test
    fun `a note pushed past a double sharp is respelled as the sound it is`() {
        var moved = events
        repeat(3) { moved = ScoreEdits.bySemitone(moved, id = 1, semitones = 1) }

        val pitch = moved[0].pitches.single()
        assertEquals(63, pitch.midi)
        assertTrue("unreadable spelling ${pitch.note.name}", pitch.note.isPractical)
    }

    @Test
    fun `a chord moves as a whole`() {
        val chord = listOf(event(1, listOf(pitch("C", 4), pitch("E", 4), pitch("G", 4))))

        val fixed = ScoreEdits.byStep(chord, id = 1, steps = 1, key = KeySignature())

        assertEquals(listOf("D4", "F4", "A4"), fixed.single().pitches.map { it.toString() })
    }

    @Test
    fun `changing a length moves everything after it`() {
        val fixed = ScoreEdits.setLength(events, id = 1, quarters = 2.0)

        assertEquals(
            listOf(0.0, 2.0, 3.0),
            Timeline.of(fixed, listOf(listOf(0))).map { it.startQuarters },
        )
    }

    @Test
    fun `a note wrongly read can be made a silence and back again`() {
        val silenced = ScoreEdits.toggleRest(events, id = 1, whenEmpty = pitch("C", 4))
        assertTrue(silenced[0].isRest)

        val sounded = ScoreEdits.toggleRest(silenced, id = 1, whenEmpty = pitch("G", 4))
        assertEquals("G4", sounded[0].pitches.single().toString())
    }

    @Test
    fun `deleting an event closes the gap`() {
        val fixed = ScoreEdits.remove(events, id = 2)

        assertEquals(listOf(1, 3), fixed.map { it.id })
        assertEquals(listOf(0.0, 1.0), Timeline.of(fixed, listOf(listOf(0))).map { it.startQuarters })
    }

    @Test
    fun `a missed note can be put back in after its neighbour`() {
        val fixed = ScoreEdits.insertAfter(events, id = 1)

        assertEquals(4, fixed.size)
        assertEquals(1, fixed[0].id)
        // The new event sits between its neighbours on the page as well as in the list.
        assertTrue(fixed[1].x > fixed[0].x && fixed[1].x < fixed[2].x)
        assertEquals(0, fixed[1].staffIndex)
    }

    @Test
    fun `editing one event leaves the others alone`() {
        val fixed = ScoreEdits.bySemitone(events, id = 2, semitones = -1)

        assertEquals("C4", fixed[0].pitches.single().toString())
        assertEquals("Eb4", fixed[1].pitches.single().toString())
        assertTrue(fixed[2].isRest)
    }

    @Test
    fun `an edit to an id that is not there changes nothing`() {
        assertEquals(events, ScoreEdits.byStep(events, id = 99, steps = 1, key = KeySignature()))
        assertEquals(events, ScoreEdits.remove(events, id = 99))
        assertEquals(events, ScoreEdits.insertAfter(events, id = 99))
    }
}

class ClefStepTest {

    @Test
    fun `a pitch and its position on the staff agree both ways`() {
        for (step in -6..14) {
            assertEquals(step, Clef.TREBLE.stepOf(Clef.TREBLE.pitchAt(step)))
            assertEquals(step, Clef.BASS.stepOf(Clef.BASS.pitchAt(step)))
        }
    }

    @Test
    fun `middle C sits where each clef puts it`() {
        assertEquals(-2, Clef.TREBLE.stepOf(pitch("C", 4)))
        assertEquals(10, Clef.BASS.stepOf(pitch("C", 4)))
    }
}
