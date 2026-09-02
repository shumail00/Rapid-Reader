package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beautiful Circular Wavy Progress Indicator with fluid harmonic sine wave animations.
 * Provides a modern, organic, fluid visual representation during PDF parsing and text processing.
 */
@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    waveCount: Int = 8,
    strokeWidth: Dp = 4.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    content: @Composable (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy_progress_infinite")

    // Phase angle for wave ripple motion
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Base rotation of the entire circle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    // Breathing wave amplitude
    val amplitudeScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amplitude_scale"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val strokePx = strokeWidth.toPx()
            val baseRadius = (size.toPx() - strokePx * 4f) / 2f
            val baseAmplitude = strokePx * 1.5f * amplitudeScale

            // 1. Subtle background track circle
            drawCircle(
                color = trackColor,
                radius = baseRadius,
                center = center,
                style = Stroke(width = strokePx * 0.75f)
            )

            // 2. Secondary counter-rotating harmonic wave
            drawWavyCircle(
                center = center,
                radius = baseRadius,
                waveCount = waveCount,
                amplitude = baseAmplitude * 0.65f,
                phase = -phase * 1.2f,
                rotationDegrees = -rotationAngle * 0.75f,
                color = secondaryColor.copy(alpha = 0.45f),
                strokeWidth = strokePx * 0.8f
            )

            // 3. Primary vibrant wave with gradient sweep
            val gradientBrush = Brush.sweepGradient(
                colors = listOf(
                    primaryColor,
                    secondaryColor,
                    primaryColor.copy(alpha = 0.8f),
                    primaryColor
                ),
                center = center
            )

            drawWavyCircleWithBrush(
                center = center,
                radius = baseRadius,
                waveCount = waveCount,
                amplitude = baseAmplitude,
                phase = phase,
                rotationDegrees = rotationAngle,
                brush = gradientBrush,
                strokeWidth = strokePx
            )

            // 4. Subtle inner pulsing center dot
            drawCircle(
                color = primaryColor.copy(alpha = 0.25f * amplitudeScale),
                radius = baseRadius * 0.25f,
                center = center
            )
        }

        // Optional Center Composable (e.g. icon or badge)
        content?.invoke()
    }
}

/**
 * Helper to draw a continuous smooth sine-wave modulated circle.
 */
private fun DrawScope.drawWavyCircle(
    center: Offset,
    radius: Float,
    waveCount: Int,
    amplitude: Float,
    phase: Float,
    rotationDegrees: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = buildWavyPath(center, radius, waveCount, amplitude, phase, rotationDegrees)
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawWavyCircleWithBrush(
    center: Offset,
    radius: Float,
    waveCount: Int,
    amplitude: Float,
    phase: Float,
    rotationDegrees: Float,
    brush: Brush,
    strokeWidth: Float
) {
    val path = buildWavyPath(center, radius, waveCount, amplitude, phase, rotationDegrees)
    drawPath(
        path = path,
        brush = brush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun buildWavyPath(
    center: Offset,
    radius: Float,
    waveCount: Int,
    amplitude: Float,
    phase: Float,
    rotationDegrees: Float
): Path {
    val path = Path()
    val steps = 180
    val rotRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()

    for (i in 0..steps) {
        val angle = (i.toDouble() / steps) * 2 * PI
        val totalAngle = angle.toFloat() + rotRad
        val r = radius + amplitude * sin(waveCount * angle + phase).toFloat()

        val x = center.x + r * cos(totalAngle)
        val y = center.y + r * sin(totalAngle)

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}
