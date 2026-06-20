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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated "magical power threads" progress bar with interlacing weave effect.
 *
 * Design: multiple colored threads/strands that undulate and CROSS OVER each other
 * like interwoven cords. Each thread has its own depth value that varies along the bar,
 * so at crossing points one thread passes in front of the other, then behind — creating
 * a true weaving/braiding visual.
 *
 * - Threads: vivid colors (no yellow/green) — red, orange, magenta, purple, blue, cyan
 * - Weaving: threads interlace, passing over and under each other like braided cords
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

    // Thread definitions: color, amplitude, frequency, phase, thickness, depthFreq, depthPhase
    // depthFreq & depthPhase control the "weave depth" — which thread is in front at any x position
    val threads: List<ThreadSpec> = remember {
        listOf(
            ThreadSpec(Color(0xFFFF1744), 0.9f, 3.0f, 0.0f, 1.8f, 1.5f, 0.0f),   // Vivid red
            ThreadSpec(Color(0xFFFF6D00), 0.7f, 2.5f, 1.0f, 1.5f, 2.0f, 0.8f),   // Vivid orange
            ThreadSpec(Color(0xFFFF1493), 0.8f, 3.5f, 2.0f, 1.4f, 1.8f, 1.6f),   // Deep pink
            ThreadSpec(Color(0xFFD500F9), 0.6f, 2.0f, 0.5f, 1.6f, 2.2f, 2.4f),   // Vivid purple
            ThreadSpec(Color(0xFF651FFF), 0.85f, 2.8f, 1.5f, 1.5f, 1.3f, 3.2f),  // Deep violet
            ThreadSpec(Color(0xFF2979FF), 0.75f, 3.2f, 3.0f, 1.7f, 1.7f, 4.0f),  // Vivid blue
            ThreadSpec(Color(0xFF00E5FF), 0.65f, 2.6f, 2.5f, 1.3f, 2.5f, 4.8f),  // Vivid cyan
            ThreadSpec(Color(0xFF7C4DFF), 0.5f, 3.8f, 0.8f, 1.1f, 1.9f, 5.6f),   // Electric violet
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

        val t = time * 2f * PI.toFloat()
        val step = 2f

        // ── Compute thread paths & depth for interlacing ──
        // For each x position, we compute each thread's y and depth.
        // Then we draw segments sorted by depth (back-to-front) so threads cross over each other.
        val numSteps = (barW / step).toInt() + 1

        // Pre-compute all thread positions and depths
        // threadPaths[threadIndex][stepIndex] = Pair(y, depth)
        val threadPaths = Array(threads.size) { threadIdx ->
            val thread = threads[threadIdx]
            val amp = thread.amplitude * barH * 0.38f
            val freq = thread.frequency
            val phase = thread.phaseOffset
            val depthFreq = thread.depthFreq
            val depthPhase = thread.depthPhase

            FloatArray(numSteps * 2).also { arr ->
                for (i in 0 until numSteps) {
                    val x = (i * step).coerceAtMost(barW)
                    val xNorm = x / barW
                    arr[i * 2] = barH / 2f + amp * sin(freq * xNorm * 2f * PI.toFloat() + t + phase)
                    // depth varies with a different sine — this creates the weave pattern
                    arr[i * 2 + 1] = sin(depthFreq * xNorm * 2f * PI.toFloat() + t * 0.3f + depthPhase)
                }
            }
        }

        // Draw segment by segment, back-to-front (sorted by depth at each x step)
        for (i in 0 until numSteps - 1) {
            val x0 = (i * step).coerceAtMost(barW)
            val x1 = ((i + 1) * step).coerceAtMost(barW)

            // Sort threads by depth at midpoint of this segment (back first, front last)
            val sortedIndices = (0 until threads.size).sortedBy { threadIdx ->
                threadPaths[threadIdx][i * 2 + 1] // depth at current step
            }

            for (threadIdx in sortedIndices) {
                val thread = threads[threadIdx]
                val y0 = threadPaths[threadIdx][i * 2]
                val y1 = threadPaths[threadIdx][(i + 1) * 2]
                val depth = threadPaths[threadIdx][i * 2 + 1]

                // Threads closer to front (higher depth) are brighter and slightly thicker
                val depthFactor = 0.6f + 0.4f * ((depth + 1f) / 2f) // normalize -1..1 → 0.6..1.0
                val thicknessFactor = 0.7f + 0.3f * ((depth + 1f) / 2f)

                drawLine(
                    color = thread.color.copy(alpha = pulse * 0.9f * depthFactor),
                    start = Offset(x0, y0),
                    end = Offset(x1, y1),
                    strokeWidth = thread.thickness * thicknessFactor,
                )
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

            drawCircle(
                color = Color(0xFF42A5F5).copy(alpha = 0.95f * pulse),
                radius = barH * 1.4f,
                center = Offset(cx, cy)
            )

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
    val phaseOffset: Float,     // phase offset for y-wave variety
    val thickness: Float,       // stroke width in dp
    val depthFreq: Float,       // frequency of the depth oscillation (weave pattern)
    val depthPhase: Float,      // phase offset for depth (so threads weave at different points)
)
