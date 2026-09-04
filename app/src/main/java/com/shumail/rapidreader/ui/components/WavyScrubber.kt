package com.shumail.rapidreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material You Wavy Scrubber / Progress Bar
 * Features an undulating sine wave for the elapsed track when playing,
 * a straight muted track for remaining progress, and a vertical pill thumb.
 */
@Composable
fun WavyScrubber(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackHeight: Dp = 6.dp,
    waveAmplitude: Dp = 4.dp,
    wavelength: Dp = 22.dp,
    thumbWidth: Dp = 6.dp,
    thumbHeight: Dp = 22.dp
) {
    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val waveAmpPx = with(density) { waveAmplitude.toPx() }
    val wavelengthPx = with(density) { wavelength.toPx() }
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    // Wave animation phase
    val infiniteTransition = rememberInfiniteTransition(label = "wave_phase")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_angle"
    )

    // Animated amplitude when playing vs paused
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) waveAmpPx else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "amplitude"
    )

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val newProg = (offset.x / size.width).coerceIn(0f, 1f)
                    onProgressChange(newProg)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onProgressChange(dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val newProg = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragProgress = newProg
                        onProgressChange(newProg)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val thumbX = (currentProgress * width).coerceIn(0f, width)

            // 1. Draw Inactive / Remaining Track (Flat)
            if (thumbX < width) {
                drawLine(
                    color = inactiveColor,
                    start = Offset(thumbX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = trackHeightPx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw Active Track (Wavy Sine Path or Flat based on animatedAmplitude)
            if (thumbX > 0f) {
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                if (animatedAmplitude > 0.5f) {
                    val step = 3f // px step for smooth curves
                    var x = 0f
                    val frequency = (2 * PI / wavelengthPx).toFloat()

                    while (x <= thumbX) {
                        // Dampen wave amplitude near the start and near the thumb for smooth entry/exit
                        val edgeFadeStart = (x / 20f).coerceIn(0f, 1f)
                        val edgeFadeEnd = ((thumbX - x) / 20f).coerceIn(0f, 1f)
                        val damping = edgeFadeStart * edgeFadeEnd

                        val y = centerY + sin(x * frequency - wavePhase) * animatedAmplitude * damping
                        wavePath.lineTo(x, y)
                        x += step
                    }
                    wavePath.lineTo(thumbX, centerY)

                    drawPath(
                        path = wavePath,
                        color = activeColor,
                        style = Stroke(
                            width = trackHeightPx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                } else {
                    // Paused straight active track
                    drawLine(
                        color = activeColor,
                        start = Offset(0f, centerY),
                        end = Offset(thumbX, centerY),
                        strokeWidth = trackHeightPx,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Draw Vertical Pill Thumb
            val halfThumbW = thumbWidthPx / 2f
            val halfThumbH = thumbHeightPx / 2f
            val thumbLeft = (thumbX - halfThumbW).coerceIn(0f, width - thumbWidthPx)
            val thumbTop = centerY - halfThumbH

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }
    }
}
