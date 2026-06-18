package com.auso.social.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// AUSO Brand Colors - Blue accent (was orange)
val AusoPrimary = androidx.compose.ui.graphics.Color(0xFF2196F3)
val AusoPrimaryDark = androidx.compose.ui.graphics.Color(0xFF1976D2)
val AusoPrimaryLight = androidx.compose.ui.graphics.Color(0xFF64B5F6)
val AusoSecondary = androidx.compose.ui.graphics.Color(0xFF7C3AED)

// Dark theme colors - deeper and more contrast
val AusoDarkBackground = androidx.compose.ui.graphics.Color(0xFF0A0A12)
val AusoDarkSurface = androidx.compose.ui.graphics.Color(0xFF12121E)
val AusoDarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF1C1C2E)

// Light theme colors - clean white
val AusoLightBackground = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
val AusoLightSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
val AusoLightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFE8E8EC)

val AusoOnPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
val AusoError = androidx.compose.ui.graphics.Color(0xFFFF5252)
val AusoSuccess = androidx.compose.ui.graphics.Color(0xFF4CAF50)

private val DarkColorScheme = darkColorScheme(
    primary = AusoPrimary,
    onPrimary = AusoOnPrimary,
    secondary = AusoSecondary,
    background = AusoDarkBackground,
    surface = AusoDarkSurface,
    surfaceVariant = AusoDarkSurfaceVariant,
    onBackground = androidx.compose.ui.graphics.Color(0xFFE8E8F0),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE8E8F0),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9E9EB8),
    error = AusoError,
    outline = androidx.compose.ui.graphics.Color(0xFF2A2A40),
)

private val LightColorScheme = lightColorScheme(
    primary = AusoPrimary,
    onPrimary = AusoOnPrimary,
    secondary = AusoSecondary,
    background = AusoLightBackground,
    surface = AusoLightSurface,
    surfaceVariant = AusoLightSurfaceVariant,
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1A2E),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF666680),
    error = AusoError,
    outline = androidx.compose.ui.graphics.Color(0xFFD0D0D8),
)

@Composable
fun AUSOTheme(
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
