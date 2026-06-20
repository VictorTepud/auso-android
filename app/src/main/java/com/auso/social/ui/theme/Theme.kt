package com.auso.social.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// AUSO Brand Colors - Blue accent
val AusoPrimary = Color(0xFF2196F3)

// ═══════════ DARK THEME: Deep black, no gradients ═══════════
private val DeepBlackColorScheme = darkColorScheme(
    primary = AusoPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    background = Color(0xFF000000),        // Pure black
    surface = Color(0xFF000000),            // Pure black
    surfaceVariant = Color(0xFF111111),     // Minimal elevation
    onBackground = Color(0xFFE0E0E0),       // Light gray text
    onSurface = Color(0xFFE0E0E0),          // Light gray text
    onSurfaceVariant = Color(0xFF9E9E9E),   // Muted gray
    error = Color(0xFFFF5252),
    onError = Color.White,
    outline = Color(0xFF222222),            // Subtle borders
    outlineVariant = Color(0xFF1A1A1A),
    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF000000),
)

// ═══════════ LIGHT THEME: Deep white, no gradients ═══════════
private val DeepWhiteColorScheme = lightColorScheme(
    primary = AusoPrimary,
    onPrimary = Color.White,
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),         // Pure white
    surface = Color(0xFFFFFFFF),             // Pure white
    surfaceVariant = Color(0xFFF5F5F5),     // Minimal elevation
    onBackground = Color(0xFF1A1A1A),        // Dark text
    onSurface = Color(0xFF1A1A1A),           // Dark text
    onSurfaceVariant = Color(0xFF666666),    // Muted gray
    error = Color(0xFFFF5252),
    onError = Color.White,
    outline = Color(0xFFE0E0E0),            // Subtle borders
    outlineVariant = Color(0xFFEEEEEE),
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color(0xFFE0E0E0),
)

@Composable
fun AUSOTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()  // "system"
    }

    val colorScheme = if (darkTheme) DeepBlackColorScheme else DeepWhiteColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
