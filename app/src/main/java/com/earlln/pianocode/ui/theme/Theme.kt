package com.earlln.pianocode.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// A deep indigo taken from a piano's fallboard, with a violet accent for highlighted keys.
val Indigo900 = Color(0xFF1B1725)
val Violet500 = Color(0xFF7C5CFF)
val Violet300 = Color(0xFFB3A0FF)
val Amber400 = Color(0xFFFFB74D)
val Teal400 = Color(0xFF4DD0C7)
val Rose400 = Color(0xFFFF7597)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B3FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1B0A63),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9F0E8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFB26A00),
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1A21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1A21),
    surfaceVariant = Color(0xFFE6E1EF),
    onSurfaceVariant = Color(0xFF48454F),
    outline = Color(0xFF79757F),
)

private val DarkColors = darkColorScheme(
    primary = Violet300,
    onPrimary = Color(0xFF2A1B6B),
    primaryContainer = Color(0xFF3F2FA0),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Teal400,
    onSecondary = Color(0xFF00352F),
    secondaryContainer = Color(0xFF005048),
    onSecondaryContainer = Color(0xFFB9F0E8),
    tertiary = Amber400,
    tertiaryContainer = Color(0xFF6B4400),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Indigo900,
    onBackground = Color(0xFFE7E1EC),
    surface = Indigo900,
    onSurface = Color(0xFFE7E1EC),
    surfaceVariant = Color(0xFF2E2A38),
    onSurfaceVariant = Color(0xFFC9C4D4),
    outline = Color(0xFF938F9C),
)

private val PianoTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun PianoCodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = PianoTypography, content = content)
}
