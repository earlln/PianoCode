package com.earlln.pianocode.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** How the keyboard pictures are drawn. */
data class KeyboardPrefs(
    /** The circled finger number on each key. */
    val showFingers: Boolean = true,
    /** The hand silhouette reaching for those keys. */
    val showHand: Boolean = true,
    /** How solid that silhouette is. Low enough and the keys read straight through it. */
    val handOpacity: Float = 0.55f,
    /** Note names written on the keys, where the picture is big enough to hold them. */
    val showNoteNames: Boolean = true,
    /** Dragging a keyboard sideways walks the inversions. */
    val swipeToInvert: Boolean = true,
    /** The hand on the small keyboards in the chord list, not only the big one. */
    val handInList: Boolean = true,
)

/**
 * The keyboard drawing options, remembered between runs.
 *
 * A single observable object rather than a view model per screen: every keyboard in the app
 * draws from the same settings, and threading them through each screen that happens to show
 * one would be a lot of plumbing for a handful of switches.
 */
object KeyboardSettings {

    private const val PREFS = "keyboard_settings"
    private var store: SharedPreferences? = null

    var prefs: KeyboardPrefs by mutableStateOf(KeyboardPrefs())
        private set

    /** Reads what was saved. Call once, before anything draws. */
    fun load(context: Context) {
        val store = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { this.store = it }
        val defaults = KeyboardPrefs()
        prefs = KeyboardPrefs(
            showFingers = store.getBoolean("showFingers", defaults.showFingers),
            showHand = store.getBoolean("showHand", defaults.showHand),
            handOpacity = store.getFloat("handOpacity", defaults.handOpacity),
            showNoteNames = store.getBoolean("showNoteNames", defaults.showNoteNames),
            swipeToInvert = store.getBoolean("swipeToInvert", defaults.swipeToInvert),
            handInList = store.getBoolean("handInList", defaults.handInList),
        )
    }

    fun update(transform: (KeyboardPrefs) -> KeyboardPrefs) {
        val next = transform(prefs)
        prefs = next
        store?.edit {
            putBoolean("showFingers", next.showFingers)
            putBoolean("showHand", next.showHand)
            putFloat("handOpacity", next.handOpacity)
            putBoolean("showNoteNames", next.showNoteNames)
            putBoolean("swipeToInvert", next.swipeToInvert)
            putBoolean("handInList", next.handInList)
        }
    }

    fun reset() = update { KeyboardPrefs() }
}
