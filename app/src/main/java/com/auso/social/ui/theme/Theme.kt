package com.auso.social.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// AUSO Brand Colors
val AusoPrimary = androidx.compose.ui.graphics.Color(0xFFFF6B35)
val AusoPrimaryDark = androidx.compose.ui.graphics.Color(0xFFE55A2B)
val AusoPrimaryLight = androidx.compose.ui.graphics.Color(0xFFFF8C5A)
val AusoSecondary = androidx.compose.ui.graphics.Color(0xFF7C3AED)
val AusoBackground = androidx.compose.ui.graphics.Color(0xFF0F0F1A)
val AusoSurface = androidx.compose.ui.graphics.Color(0xFF1A1A2E)
val AusoSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF252540)
val AusoOnPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
val AusoOnBackground = androidx.compose.ui.graphics.Color(0xFFE8E8F0)
val AusoOnSurface = androidx.compose.ui.graphics.Color(0xFFE8E8F0)
val AusoOnSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF9E9EB8)
val AusoError = androidx.compose.ui.graphics.Color(0xFFFF5252)
val AusoSuccess = androidx.compose.ui.graphics.Color(0xFF4CAF50)
val AusoDivider = androidx.compose.ui.graphics.Color(0xFF2A2A45)

private val DarkColorScheme = darkColorScheme(
    primary = AusoPrimary,
    onPrimary = AusoOnPrimary,
    secondary = AusoSecondary,
    background = AusoBackground,
    surface = AusoSurface,
    surfaceVariant = AusoSurfaceVariant,
    onBackground = AusoOnBackground,
    onSurface = AusoOnSurface,
    onSurfaceVariant = AusoOnSurfaceVariant,
    error = AusoError,
    outline = AusoDivider,
)

@Composable
fun AUSOTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AusoBackground.toArgb()
            window.navigationBarColor = AusoSurface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
