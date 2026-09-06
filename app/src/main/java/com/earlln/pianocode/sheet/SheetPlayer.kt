package com.earlln.pianocode.sheet

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Plays a chord progression through the device's own synthesiser.
 *
 * The progression is written to a MIDI file and handed to [MediaPlayer], which is how the
 * app gets a flute or a piano without shipping a single sample: Android carries a General
 * MIDI synthesiser and knows all of these voices already.
 *
 * That synthesiser is not guaranteed — a handful of devices ship without it — so every step
 * reports whether it worked rather than failing quietly into silence.
 */
class SheetPlayer(private val context: Context, private val fileName: String = "progression.mid") {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player?.isPlaying == true

    /**
     * How far into the piece the synthesiser has got, in milliseconds.
     *
     * This is what lets the screen point at the note being sounded. It is asked of the
     * player rather than counted by a timer of our own, because a timer drifts away from
     * the audio and the whole value of a playhead is that it agrees with the ear.
     */
    val positionMillis: Long
        get() = player?.runCatching { currentPosition.toLong() }?.getOrNull() ?: 0L

    /** Starts [midi]. Returns false when this device cannot play it. */
    fun play(midi: ByteArray, onFinished: () -> Unit): Boolean {
        stop()
        return try {
            val file = File(context.cacheDir, fileName).apply { writeBytes(midi) }
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    onFinished()
                    stop()
                }
                setOnErrorListener { _, _, _ ->
                    onFinished()
                    stop()
                    true
                }
                prepare()
                start()
                player = this
            }
            true
        } catch (error: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
    }
}
