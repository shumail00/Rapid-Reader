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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material You 3 Expressive Circular Wavy Progress Indicator.
 * Renders a smooth circular track with an undulating sinusoidal active arc with rounded caps,
 * providing the authentic Material 3 fluid wavy animation.
 */
@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    size: Dp = 64.dp,
    waveCount: Int = 8,
    strokeWidth: Dp = 4.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    content: @Composable (() -> Unit)? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_wavy_progress_infinite")

    // Phase angle driving wave crests/troughs along the path
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3_wave_phase"
    )

    // Smooth continuous rotation of the wavy arc
    val rotationDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "m3_wave_rotation"
    )

    // Subtle breathing sweep angle for indeterminate mode
    val arcSweepAngle by infiniteTransition.animateFloat(
        initialValue = 240f,
        targetValue = 290f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "m3_wave_sweep"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val amplitude = strokePx * 0.9f
            val baseRadius = (size.toPx() - strokePx * 2f - amplitude * 2f) / 2f

            // 1. Inactive baseline track (smooth circular ring)
            drawCircle(
                color = trackColor,
                radius = baseRadius,
                center = center,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // 2. Active sinusoidal wavy arc with rounded caps
            val isDeterminate = progress != null
            val sweep = if (isDeterminate) {
                (progress!!.coerceIn(0f, 1f) * 360f)
            } else {
                arcSweepAngle
            }

            if (sweep > 0f) {
                val startAngle = if (isDeterminate) -90f else rotationDegrees
                val path = buildWavyArcPath(
                    center = center,
                    baseRadius = baseRadius,
                    amplitude = amplitude,
                    waveCount = waveCount,
                    startAngleDeg = startAngle,
                    sweepAngleDeg = sweep,
                    phase = wavePhase
                )

                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Optional center composable (e.g. icon or percentage text)
        content?.invoke()
    }
}

/**
 * Builds a path along a circular arc where the radius is modulated by a sine wave:
 * r(θ) = baseRadius + amplitude * sin(waveCount * θ + phase)
 */
private fun buildWavyArcPath(
    center: Offset,
    baseRadius: Float,
    amplitude: Float,
    waveCount: Int,
    startAngleDeg: Float,
    sweepAngleDeg: Float,
    phase: Float
): Path {
    val path = Path()
    val steps = (sweepAngleDeg.toInt().coerceAtLeast(10)) * 2
    val startRad = Math.toRadians(startAngleDeg.toDouble())
    val sweepRad = Math.toRadians(sweepAngleDeg.toDouble())

    for (i in 0..steps) {
        val fraction = i.toDouble() / steps
        val theta = startRad + fraction * sweepRad
        val r = baseRadius + amplitude * sin(waveCount * theta + phase).toFloat()

        val x = center.x + r * cos(theta).toFloat()
        val y = center.y + r * sin(theta).toFloat()

        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }

    return path
}
