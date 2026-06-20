package com.auso.social.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Animated "magical power waves" progress bar.
 *
 * Design:
 * - Body: vivid multicolor gradient that flows/shifts continuously (red → orange → yellow →
 *   green → cyan → violet → magenta), creating the "magic waves" effect.
 * - Tail (left/start): fades in from transparent so it blends with the track.
 * - Tip (right/end): transitions to deep blue, like a magic wand tip.
 * - Leading-edge glow: bright blue halo at the progress frontier.
 * - Energy pulse: a bright spot that travels along the bar like a power surge.
 *
 * @param progress 0f..1f — how much of the bar is filled
 * @param modifier standard modifier (fillMaxWidth + height recommended)
 * @param trackColor color of the unfilled track background
 * @param heightDp thickness of the bar (default 4.dp for visibility)
 */
@Composable
fun MagicProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = Color.White.copy(alpha = 0.2f),
    heightDp: Dp = 4.dp,
) {
    val p = progress.coerceIn(0f, 1f)

    val infinite = rememberInfiniteTransition(label = "magic")

    // Main sweep — drives the flowing gradient shift (0→1 over 3s, then restarts smoothly)
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    // Pulse — breathing brightness
    val pulse by infinite.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Energy dot position — travels along the bar
    val energyPos by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "energy"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        val barW = size.width * p
        val barH = size.height

        // ── Track (background) ──
        drawRect(color = trackColor, size = size)
        if (barW < 1f) return@Canvas

        // ── Vivid rainbow body gradient (flows right continuously) ──
        // We draw the gradient wider than the bar and offset it by `sweep` so it appears to flow.
        // The gradient repeats (same start/end color) for seamless wrapping.
        val repeatCount = 3
        val totalStops = vividColors.size * repeatCount
        val spanW = barW * 1.5f // gradient is wider than bar for smooth flow
        val shiftPx = sweep * spanW

        val colorStops = mutableListOf<Pair<Float, Color>>()
        for (r in 0 until repeatCount) {
            vividColors.forEachIndexed { i, c ->
                val baseFraction = (r * vividColors.size + i).toFloat() / (totalStops - 1)
                colorStops.add(baseFraction to c)
            }
        }

        val bodyBrush = Brush.horizontalGradient(
            colorStops = colorStops.toTypedArray(),
            startX = -shiftPx,
            endX = spanW * repeatCount / vividColors.size * vividColors.size - shiftPx
        )

        // Clip the body to the bar area
        drawRect(
            brush = bodyBrush,
            topLeft = Offset.Zero,
            size = Size(barW, barH)
        )

        // ── Fade-in at the tail (left edge) so it doesn't start abruptly ──
        val fadeInBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.Transparent,
                Color.White.copy(alpha = 0f) // just to blend
            ),
            startX = 0f,
            endX = barW * 0.08f
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = 0f,
                endX = barW * 0.06f
            ),
            topLeft = Offset.Zero,
            size = Size(barW * 0.06f, barH),
            blendMode = BlendMode.DstIn
        )

        // ── Blue tip zone — last 15% of the bar transitions to blue ──
        val tipStart = barW * 0.82f
        if (barW > 20f) { // only if bar is wide enough to see it
            val tipBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,          // body shows through
                    Color.Transparent,          // body shows through
                    Color(0xFF1565C0).copy(alpha = 0.5f * pulse),  // mid blue
                    Color(0xFF0D47A1).copy(alpha = 0.8f * pulse),  // deep blue
                    Color(0xFF0D47A1),          // solid deep blue at tip
                ),
                startX = tipStart,
                endX = barW
            )
            drawRect(
                brush = tipBrush,
                topLeft = Offset(tipStart, 0f),
                size = Size(barW - tipStart, barH)
            )
        }

        // ── Energy pulse — bright traveling spot ──
        val spotX = energyPos * barW
        val spotRadius = barW * 0.12f
        val spotBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.6f * pulse),
                Color.White.copy(alpha = 0.8f * pulse),
                Color.White.copy(alpha = 0.6f * pulse),
                Color.Transparent,
            ),
            startX = spotX - spotRadius,
            endX = spotX + spotRadius
        )
        drawRect(
            brush = spotBrush,
            topLeft = Offset.Zero,
            size = Size(barW, barH)
        )

        // ── Leading-edge glow — bright blue halo at the frontier ──
        if (p > 0.005f) {
            val cx = barW
            val cy = barH / 2f

            // Intense core dot
            drawCircle(
                color = Color(0xFF42A5F5).copy(alpha = 0.95f * pulse),
                radius = barH * 1.5f,
                center = Offset(cx, cy)
            )

            // Outer glow
            val glowR = barH * 6f
            val glowBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF64B5F6).copy(alpha = 0.7f * pulse),
                    Color(0xFF1E88E5).copy(alpha = 0.3f * pulse),
                    Color(0xFF0D47A1).copy(alpha = 0.1f * pulse),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = glowR
            )
            drawRect(
                brush = glowBrush,
                topLeft = Offset((cx - glowR).coerceAtLeast(0f), 0f),
                size = Size(glowR * 2, barH)
            )
        }
    }
}

/** Vivid rainbow palette for the magic wave body — saturated, high-contrast colors. */
private val vividColors = listOf(
    Color(0xFFFF1744),  // Vivid red
    Color(0xFFFF9100),  // Vivid orange
    Color(0xFFFFEA00),  // Vivid yellow
    Color(0xFF00E676),  // Vivid green
    Color(0xFF00E5FF),  // Vivid cyan
    Color(0xFF2979FF),  // Vivid blue
    Color(0xFFD500F9),  // Vivid purple/magenta
    Color(0xFFFF1744),  // Vivid red (wrap for seamless loop)
)
