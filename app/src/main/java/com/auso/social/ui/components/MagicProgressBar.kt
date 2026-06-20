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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated "magical power threads" progress bar.
 *
 * Design: multiple colored threads/strands that undulate like energy filaments
 * flowing through the bar. Each thread has its own color, amplitude, frequency,
 * and phase — creating an organic "magic waves" effect.
 *
 * - Threads: vivid colors (no yellow/green) — red, orange, magenta, purple, blue, cyan
 * - Tip: transitions to deep blue at the leading edge
 * - Leading-edge glow: bright blue halo at the progress frontier
 *
 * @param progress 0f..1f
 * @param modifier standard modifier
 * @param trackColor background track color
 * @param heightDp thickness (default 4.dp)
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

    // Main time driver — continuous flow
    val time by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Pulse — breathing brightness
    val pulse by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Thread definitions: color, amplitude factor, frequency factor, phase offset, thickness
    val threads: List<ThreadSpec> = remember {
        listOf(
            ThreadSpec(Color(0xFFFF1744), 0.9f, 3.0f, 0.0f, 1.6f),    // Vivid red
            ThreadSpec(Color(0xFFFF6D00), 0.7f, 2.5f, 1.0f, 1.4f),    // Vivid orange
            ThreadSpec(Color(0xFFFF1493), 0.8f, 3.5f, 2.0f, 1.3f),    // Deep pink
            ThreadSpec(Color(0xFFD500F9), 0.6f, 2.0f, 0.5f, 1.5f),    // Vivid purple
            ThreadSpec(Color(0xFF651FFF), 0.85f, 2.8f, 1.5f, 1.4f),   // Deep violet
            ThreadSpec(Color(0xFF2979FF), 0.75f, 3.2f, 3.0f, 1.6f),   // Vivid blue
            ThreadSpec(Color(0xFF00E5FF), 0.65f, 2.6f, 2.5f, 1.2f),   // Vivid cyan
            ThreadSpec(Color(0xFF7C4DFF), 0.5f, 3.8f, 0.8f, 1.0f),    // Electric violet
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp)
    ) {
        val barW = size.width * p
        val barH = size.height

        // ── Track (background) ──
        drawRect(color = trackColor, size = size)
        if (barW < 2f) return@Canvas

        // ── Draw undulating threads ──
        val t = time * 2f * PI.toFloat() // convert 0..1 → 0..2π for sine

        threads.forEach { thread ->
            val amp = thread.amplitude * barH * 0.35f // max vertical displacement
            val freq = thread.frequency
            val phase = thread.phaseOffset
            val strokeWidth = thread.thickness

            // Draw the thread as a series of short line segments forming a sine wave
            var prevX = 0f
            var prevY = barH / 2f + amp * sin(freq * (prevX / barW) * 2f * PI.toFloat() + t + phase)

            val step = 2f // px per segment — smooth enough for a thin bar
            var x = step
            while (x <= barW) {
                val y = barH / 2f + amp * sin(freq * (x / barW) * 2f * PI.toFloat() + t + phase)

                drawLine(
                    color = thread.color.copy(alpha = pulse * 0.9f),
                    start = Offset(prevX, prevY),
                    end = Offset(x, y),
                    strokeWidth = strokeWidth,
                    // Cap.ROUND would be ideal but isn't available in drawLine directly
                )

                prevX = x
                prevY = y
                x += step
            }
        }

        // ── Bright overlay pulse — white energy wave traveling along ──
        val waveX = time * barW
        val waveRadius = barW * 0.15f
        val waveBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.0f),
                Color.White.copy(alpha = 0.4f * pulse),
                Color.White.copy(alpha = 0.0f),
                Color.Transparent,
            ),
            startX = waveX - waveRadius,
            endX = waveX + waveRadius
        )
        drawRect(
            brush = waveBrush,
            topLeft = Offset.Zero,
            size = Size(barW, barH)
        )

        // ── Blue tip zone — last 18% transitions to deep blue ──
        val tipStart = barW * 0.82f
        if (barW > 30f) {
            val tipBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    Color(0xFF1565C0).copy(alpha = 0.6f * pulse),
                    Color(0xFF0D47A1).copy(alpha = 0.85f * pulse),
                    Color(0xFF0D47A1),
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

        // ── Leading-edge glow ──
        if (p > 0.005f) {
            val cx = barW
            val cy = barH / 2f

            // Bright core
            drawCircle(
                color = Color(0xFF42A5F5).copy(alpha = 0.95f * pulse),
                radius = barH * 1.4f,
                center = Offset(cx, cy)
            )

            // Outer halo
            val glowR = barH * 5f
            val glowBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF64B5F6).copy(alpha = 0.6f * pulse),
                    Color(0xFF1E88E5).copy(alpha = 0.25f * pulse),
                    Color(0xFF0D47A1).copy(alpha = 0.08f * pulse),
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

/** Describes a single undulating thread in the progress bar. */
private data class ThreadSpec(
    val color: Color,
    val amplitude: Float,       // 0..1 relative amplitude factor
    val frequency: Float,       // how many wave cycles across the bar
    val phaseOffset: Float,     // phase offset for variety
    val thickness: Float,       // stroke width in dp
)
