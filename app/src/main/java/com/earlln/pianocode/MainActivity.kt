package com.earlln.pianocode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.earlln.pianocode.settings.KeyboardSettings
import androidx.activity.enableEdgeToEdge
import com.earlln.pianocode.ui.PianoCodeApp
import com.earlln.pianocode.ui.theme.PianoCodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KeyboardSettings.load(this)
        setContent {
            PianoCodeTheme {
                PianoCodeApp()
            }
        }
    }
}
