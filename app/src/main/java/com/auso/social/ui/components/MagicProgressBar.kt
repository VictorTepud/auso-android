package com.auso.social.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Animated "magical power waves" progress bar with a blue-trending multicolor gradient.
 *
 * The effect uses:
 * 1. A base horizontal gradient that shifts over time (animated offset) — creates the
 *    "flowing magic" look with colors cycling from deep blue → cyan → violet → electric blue.
 * 2. A pulsing glow layer that breathes in and out to simulate energy intensity.
 * 3. Bright "wave peaks" — lighter spots that travel along the bar like energy pulses.
 * 4. A leading-edge glow at the tip of the progress — like a magic wand tip.
 *
 * @param progress 0f..1f — how much of the bar is filled
 * @param modifier standard modifier (fillMaxWidth + height recommended)
 * @param trackColor color of the unfilled track background
 * @param heightDp thickness of the bar (default 3.dp for visibility of the effect)
 */
@Composable
fun MagicProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    heightDp: Dp = 3.dp,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)

    // Infinite animations — run continuously while the composable is composed
    val infiniteTransition = rememberInfiniteTransition(label = "magic")

    // 1. Horizontal sweep offset — moves the gradient from left to right and back
    val sweepOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // 2. Pulse — subtle brightness oscillation
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // 3. Secondary wave — offset from the main sweep for more organic feel
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    // Color palette — blue-trending multicolor ("magical power")
    // Deep blue → Electric blue → Cyan → Violet → Blue again
    val baseColors = listOf(
        Color(0xFF0D47A1),  // Deep navy blue
        Color(0xFF1565C0),  // Strong blue
        Color(0xFF1E88E5),  // Medium blue
        Color(0xFF00BCD4),  // Cyan
        Color(0xFF7C4DFF),  // Electric violet
        Color(0xFF448AFF),  // Bright blue
        Color(0xFF00E5FF),  // Neon cyan
        Color(0xFF304FFE),  // Deep indigo-blue
        Color(0xFF0D47A1),  // Deep navy (wrap)
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        val barWidth = size.width * clampedProgress
        val barHeight = size.height

        // ── Track (background) ──
        drawRect(
            color = trackColor,
            size = size
        )

        if (barWidth <= 0f) return@Canvas

        // ── Layer 1: Animated flowing gradient ──
        // The gradient shifts horizontally based on sweepOffset, creating the "flowing magic" effect.
        val gradientSpan = barWidth * 2.5f
        val offsetShift = sweepOffset * gradientSpan

        val flowBrush = Brush.horizontalGradient(
            colorStops = baseColors.mapIndexed { index, color ->
                val baseStop = index.toFloat() / (baseColors.size - 1)
                // Shift stops by offset and wrap around
                val shifted = ((baseStop + sweepOffset) % 1f)
                shifted to color.copy(alpha = pulse)
            }.sortedBy { it.first }.toTypedArray(),
            startX = -offsetShift,
            endX = gradientSpan - offsetShift
        )

        drawRect(
            brush = flowBrush,
            topLeft = Offset.Zero,
            size = Size(barWidth, barHeight)
        )

        // ── Layer 2: Secondary wave — lighter "energy pulse" that travels opposite direction ──
        val wave2Span = barWidth * 2f
        val wave2Shift = wave2 * wave2Span

        val wave2Colors = listOf(
            Color(0xFF00E5FF).copy(alpha = 0.0f),
            Color(0xFF00E5FF).copy(alpha = 0.35f * pulse),
            Color(0xFF7C4DFF).copy(alpha = 0.25f * pulse),
            Color(0xFF00E5FF).copy(alpha = 0.0f),
        )

        val wave2Brush = Brush.horizontalGradient(
            colors = wave2Colors,
            startX = wave2Shift,
            endX = wave2Shift + barWidth * 0.6f
        )

        drawRect(
            brush = wave2Brush,
            topLeft = Offset.Zero,
            size = Size(barWidth, barHeight)
        )

        // ── Layer 3: Leading-edge glow — bright spot at the very tip of progress ──
        if (clampedProgress > 0.01f) {
            val glowCenterX = barWidth
            val glowCenterY = barHeight / 2f

            // Bright leading dot
            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.9f * pulse),
                radius = barHeight * 1.2f,
                center = Offset(glowCenterX, glowCenterY)
            )

            // Soft halo around leading edge
            val glowRadius = barHeight * 4f
            val haloBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.5f * pulse),
                    Color(0xFF7C4DFF).copy(alpha = 0.2f * pulse),
                    Color.Transparent,
                ),
                center = Offset(glowCenterX, glowCenterY),
                radius = glowRadius
            )
            drawRect(
                brush = haloBrush,
                topLeft = Offset((glowCenterX - glowRadius).coerceAtLeast(0f), 0f),
                size = Size(glowRadius * 2, barHeight)
            )
        }
    }
}
