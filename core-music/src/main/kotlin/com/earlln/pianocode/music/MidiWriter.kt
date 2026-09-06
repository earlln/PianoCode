package com.earlln.pianocode.music

import java.io.ByteArrayOutputStream

/**
 * Writes a chord progression as a standard MIDI file.
 *
 * A file rather than a live synthesiser because Android already has one: handing the system
 * a `.mid` gets real instrument voices with no samples to ship and no audio engine to write.
 * The format is small enough to emit by hand — a header, one track, and delta-timed events —
 * and being plain bytes it can be checked in a unit test rather than only by ear.
 *
 * What it plays is the chords, held in turn. The app reads chord symbols off a page and
 * nothing else — no note heads, no rhythm — so this is the accompaniment a player would
 * strum behind the tune, not the tune.
 */
object MidiWriter {

    /** Clock resolution. 480 divides cleanly by every note value we use. */
    const val TICKS_PER_BEAT = 480

    /** Fraction of its slot a chord actually sounds, leaving a little air before the next. */
    private const val SOUNDED = 0.92f

    fun progression(
        chords: List<Chord>,
        instrument: Instrument = Instrument.PIANO,
        bpm: Int = 90,
        beatsPerChord: Int = 2,
        startOctave: Int = 3,
        velocity: Int = 96,
    ): ByteArray {
        val track = Track()

        // Tempo, as microseconds per beat.
        track.meta(0x51, intBytes3(60_000_000 / bpm.coerceIn(20, 300)))
        track.event(0xC0, instrument.program and 0x7F)

        val slot = TICKS_PER_BEAT * beatsPerChord.coerceAtLeast(1)
        val sounded = (slot * SOUNDED).toInt().coerceAtLeast(1)
        for (chord in chords) {
            val notes = chord.voicing(startOctave)
                .map { it.midi }
                .filter { it in 0..127 }
                .distinct()
            if (notes.isEmpty()) {
                track.rest(slot)
                continue
            }
            notes.forEach { track.event(0x90, it, velocity.coerceIn(1, 127)) }
            notes.forEachIndexed { index, note ->
                if (index == 0) track.rest(sounded)
                track.event(0x80, note, 0)
            }
            track.rest(slot - sounded)
        }
        track.meta(0x2F, ByteArray(0))

        val body = track.bytes()
        return ByteArrayOutputStream().apply {
            write("MThd".toByteArray(Charsets.US_ASCII))
            write(intBytes4(6))
            write(shortBytes(0))                 // format 0: everything on one track
            write(shortBytes(1))
            write(shortBytes(TICKS_PER_BEAT))
            write("MTrk".toByteArray(Charsets.US_ASCII))
            write(intBytes4(body.size))
            write(body)
        }.toByteArray()
    }

    /** How long [chords] will take at these settings, in milliseconds. */
    fun durationMillis(chordCount: Int, bpm: Int, beatsPerChord: Int): Long =
        (chordCount.toLong() * beatsPerChord.coerceAtLeast(1) * 60_000L) /
            bpm.coerceIn(20, 300)

    /** One note of a piece: what to sound, when, and for how long. */
    data class TimedNote(
        val midi: Int,
        val startQuarters: Double,
        val quarters: Double,
    )

    /**
     * Writes notes that start and stop at times of their own.
     *
     * [progression] gives every chord the same slot, which is all a chord chart needs.
     * Music read off a staff does not work that way — the notes have their own lengths and
     * two staves sound at once — so the events are gathered, sorted by when they happen,
     * and written out as one stream. Where a note ends exactly as the same note starts
     * again, the ending is written first, or the synthesiser hears one long note instead of
     * two.
     */
    fun score(
        notes: List<TimedNote>,
        instrument: Instrument = Instrument.PIANO,
        bpm: Int = 90,
        velocity: Int = 96,
    ): ByteArray {
        val track = Track()
        track.meta(0x51, intBytes3(60_000_000 / bpm.coerceIn(20, 300)))
        track.event(0xC0, instrument.program and 0x7F)

        val events = mutableListOf<Event>()
        for (note in notes) {
            if (note.midi !in 0..127 || note.quarters <= 0.0) continue
            val start = (note.startQuarters * TICKS_PER_BEAT).toInt().coerceAtLeast(0)
            val length = (note.quarters * TICKS_PER_BEAT * SOUNDED).toInt().coerceAtLeast(1)
            events += Event(start, on = true, midi = note.midi)
            events += Event(start + length, on = false, midi = note.midi)
        }
        if (events.isEmpty()) {
            track.meta(0x2F, ByteArray(0))
            return wrap(track.bytes())
        }

        events.sortWith(compareBy({ it.tick }, { it.on }))
        var last = 0
        for (event in events) {
            track.rest(event.tick - last)
            last = event.tick
            if (event.on) {
                track.event(0x90, event.midi, velocity.coerceIn(1, 127))
            } else {
                track.event(0x80, event.midi, 0)
            }
        }
        track.meta(0x2F, ByteArray(0))
        return wrap(track.bytes())
    }

    /** How long a piece of [quarters] crotchets lasts at [bpm], in milliseconds. */
    fun scoreMillis(quarters: Double, bpm: Int): Long =
        (quarters * 60_000.0 / bpm.coerceIn(20, 300)).toLong()

    private data class Event(val tick: Int, val on: Boolean, val midi: Int)

    private fun wrap(body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write("MThd".toByteArray(Charsets.US_ASCII))
        write(intBytes4(6))
        write(shortBytes(0))
        write(shortBytes(1))
        write(shortBytes(TICKS_PER_BEAT))
        write("MTrk".toByteArray(Charsets.US_ASCII))
        write(intBytes4(body.size))
        write(body)
    }.toByteArray()

    // --- the track being built ----------------------------------------------

    private class Track {
        private val out = ByteArrayOutputStream()
        /** Ticks waiting to be spent on the next event's delta time. */
        private var pending = 0

        fun rest(ticks: Int) {
            pending += ticks.coerceAtLeast(0)
        }

        fun event(status: Int, vararg data: Int) {
            writeVarLen(pending)
            pending = 0
            out.write(status)
            data.forEach { out.write(it and 0xFF) }
        }

        fun meta(type: Int, data: ByteArray) {
            writeVarLen(pending)
            pending = 0
            out.write(0xFF)
            out.write(type)
            writeVarLen(data.size)
            out.write(data)
        }

        fun bytes(): ByteArray = out.toByteArray()

        /**
         * Delta times are written seven bits at a time, every byte but the last carrying a
         * high bit that says "there is more". It is how a MIDI file stays compact when most
         * gaps are short.
         */
        private fun writeVarLen(value: Int) {
            var buffer = value.toLong() and 0x7F
            var rest = value ushr 7
            while (rest > 0) {
                buffer = (buffer shl 8) or 0x80L or (rest and 0x7F).toLong()
                rest = rest ushr 7
            }
            while (true) {
                out.write((buffer and 0xFF).toInt())
                if (buffer and 0x80L != 0L) buffer = buffer shr 8 else break
            }
        }
    }

    private fun shortBytes(value: Int) =
        byteArrayOf((value shr 8).toByte(), value.toByte())

    private fun intBytes3(value: Int) =
        byteArrayOf((value shr 16).toByte(), (value shr 8).toByte(), value.toByte())

    private fun intBytes4(value: Int) = byteArrayOf(
        (value shr 24).toByte(), (value shr 16).toByte(),
        (value shr 8).toByte(), value.toByte(),
    )
}
