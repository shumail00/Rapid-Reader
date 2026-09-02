package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/**
 * Modifier that adds an ultra-responsive spring-based bouncy scaling effect when clicked or pressed.
 */
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.90f,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    coroutineScope.launch {
                        scale.animateTo(
                            targetValue = scaleDown,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    val up = waitForUpOrCancellation()
                    coroutineScope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                    }
                    if (up != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            }
        }
}

/**
 * A wrapper composable providing bouncy tactile click behavior with a spring pop.
 */
@Composable
fun BouncyBox(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scaleDown: Float = 0.92f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.bouncyClick(
            enabled = enabled,
            scaleDown = scaleDown,
            onClick = onClick
        )
    ) {
        content()
    }
}
