package com.oprek.tool.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Dark hacker theme colors
val DarkBg = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkCard = Color(0xFF1C2128)
val AccentGreen = Color(0xFF3FB950)
val AccentBlue = Color(0xFF58A6FF)
val AccentPurple = Color(0xFFBC8CFF)
val AccentOrange = Color(0xFFD29922)
val AccentRed = Color(0xFFF85149)
val AccentCyan = Color(0xFF39D2C0)
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val TextMuted = Color(0xFF484F58)

private val DarkColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = DarkBg,
    primaryContainer = Color(0xFF1A4D2E),
    onPrimaryContainer = AccentGreen,
    secondary = AccentBlue,
    onSecondary = DarkBg,
    secondaryContainer = Color(0xFF1A3A5C),
    onSecondaryContainer = AccentBlue,
    tertiary = AccentPurple,
    onTertiary = DarkBg,
    tertiaryContainer = Color(0xFF3D2A5C),
    onTertiaryContainer = AccentPurple,
    error = AccentRed,
    onError = DarkBg,
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = AccentRed,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    outlineVariant = Color(0xFF30363D),
)

@Composable
fun OprekToolTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = DarkBg.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = DarkBg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
