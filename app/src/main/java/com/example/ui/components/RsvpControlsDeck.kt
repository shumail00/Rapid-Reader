package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RsvpCanvasPalette

@Composable
fun RsvpControlsDeck(
    isPlaying: Boolean,
    currentIndex: Int,
    totalWords: Int,
    progress: Float,
    wpm: Int,
    timeRemainingFormatted: String,
    palette: RsvpCanvasPalette,
    chunkSize: Int = 1,
    showContextBar: Boolean = true,
    onTogglePlayPause: () -> Unit,
    onStop: () -> Unit,
    onSkipBackward: (Int) -> Unit,
    onSkipForward: (Int) -> Unit,
    onSeek: (Int) -> Unit,
    onWpmChange: (Int) -> Unit,
    onChunkSizeChange: (Int) -> Unit = {},
    onToggleContextBar: () -> Unit = {},
    onRestart: () -> Unit = { onSeek(0) },
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Calculate elapsed and remaining time formatted (mm:ss)
    val wordsRead = currentIndex.coerceAtLeast(0)
    val wordsLeft = (totalWords - currentIndex).coerceAtLeast(0)
    val elapsedSeconds = if (wpm > 0) (wordsRead * 60) / wpm else 0
    val remainingSeconds = if (wpm > 0) (wordsLeft * 60) / wpm else 0

    val elapsedText = String.format("%d:%02d", elapsedSeconds / 60, elapsedSeconds % 60)
    val remainingText = String.format("-%d:%02d", remainingSeconds / 60, remainingSeconds % 60)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactMobile = maxWidth < 600.dp

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rsvp_controls_deck"),
            shape = RoundedCornerShape(if (isCompactMobile) 24.dp else 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isCompactMobile) 16.dp else 24.dp,
                        vertical = if (isCompactMobile) 14.dp else 20.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. TOP SCRUBBER BAR: Elapsed Time | Wavy Scrubber | Remaining Time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = elapsedText,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.mutedTextColor.copy(alpha = 0.8f),
                        modifier = Modifier.width(36.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    WavyScrubber(
                        progress = progress,
                        onProgressChange = { newProg ->
                            if (totalWords > 0) {
                                val targetIndex = (newProg * totalWords).toInt().coerceIn(0, totalWords - 1)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSeek(targetIndex)
                            }
                        },
                        isPlaying = isPlaying,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        thumbColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("wavy_progress_scrubber")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = remainingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.mutedTextColor.copy(alpha = 0.8f),
                        modifier = Modifier.width(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactMobile) 10.dp else 14.dp))

                if (isCompactMobile) {
                    // MOBILE COMPACT STACKED CONTROLS
                    // Row A: Main Hero Playback Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Restart
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClick(scaleDown = 0.86f) { onRestart() }
                                .testTag("restart_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Restart",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Skip Backward 25
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClick(scaleDown = 0.86f) { onSkipBackward(25) }
                                .testTag("skip_backward_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 25 words",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Hero Play / Pause Squircle Button with Bouncy Spring Pop
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(width = 72.dp, height = 52.dp)
                                .bouncyClick(scaleDown = 0.88f) { onTogglePlayPause() }
                                .testTag("play_pause_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "play_pause_hero"
                                ) { playing ->
                                    if (playing) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Skip Forward 25
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClick(scaleDown = 0.86f) { onSkipForward(25) }
                                .testTag("skip_forward_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 25 words",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Stop / Reset Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .bouncyClick(scaleDown = 0.86f) { onStop() }
                                .testTag("stop_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Row B: WPM Slider Bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Speed (WPM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.mutedTextColor
                            )
                            Text(
                                text = "$wpm WPM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textColor
                            )
                        }

                        Slider(
                            value = wpm.toFloat(),
                            onValueChange = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onWpmChange(it.toInt())
                            },
                            valueRange = 100f..1000f,
                            steps = 35,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .testTag("wpm_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row C: Word chunk & Context line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Word Chunk selector
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1 to "1w", 2 to "2w", 3 to "3w").forEach { (size, label) ->
                                val selected = (chunkSize == size)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .bouncyClick(scaleDown = 0.90f) {
                                            onChunkSizeChange(size)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                        .testTag("chunk_size_${size}_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Context Line Toggle Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .bouncyClick(scaleDown = 0.92f) {
                                    onToggleContextBar()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("context_line_toggle"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showContextBar) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Context line",
                                tint = if (showContextBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Context",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showContextBar) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // WIDE / TABLET 3-COLUMN CONTROLS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT: Speed Setting
                        Column(
                            modifier = Modifier.width(170.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Speed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.mutedTextColor
                                )
                                Text(
                                    text = "$wpm wpm",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textColor
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Slider(
                                value = wpm.toFloat(),
                                onValueChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onWpmChange(it.toInt())
                                },
                                valueRange = 100f..1000f,
                                steps = 35,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .testTag("wpm_slider"),
                                colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }

                    // CENTER: Playback Action Controls
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Restart from start
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .bouncyClick(scaleDown = 0.86f) {
                                    onRestart()
                                }
                                .testTag("restart_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Restart",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Skip Backward 25
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .bouncyClick(scaleDown = 0.86f) {
                                    onSkipBackward(25)
                                }
                                .testTag("skip_backward_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind 25 words",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Hero Play / Pause Squircle Button with Bouncy Spring Pop
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(width = 84.dp, height = 62.dp)
                                .bouncyClick(scaleDown = 0.88f) {
                                    onTogglePlayPause()
                                }
                                .testTag("play_pause_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AnimatedContent(
                                    targetState = isPlaying,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "play_pause_hero"
                                ) { playing ->
                                    if (playing) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Skip Forward 25
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .bouncyClick(scaleDown = 0.86f) {
                                    onSkipForward(25)
                                }
                                .testTag("skip_forward_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward 25 words",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Stop / Reset Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .bouncyClick(scaleDown = 0.86f) {
                                    onStop()
                                }
                                .testTag("stop_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = palette.textColor.copy(alpha = 0.9f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // RIGHT: Chunk Size Selector + Context Line Toggle
                    Column(
                        horizontalAlignment = Alignment.End
                    ) {
                        // Segmented Word Chunking (1 word | 2 | 3)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(1 to "1 word", 2 to "2", 3 to "3").forEach { (size, label) ->
                                val selected = (chunkSize == size)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                        .bouncyClick(scaleDown = 0.90f) {
                                            onChunkSizeChange(size)
                                        }
                                        .padding(horizontal = if (size == 1) 12.dp else 10.dp, vertical = 6.dp)
                                        .testTag("chunk_size_${size}_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (selected && size == 1) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Context Line Toggle Button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .bouncyClick(scaleDown = 0.92f) {
                                    onToggleContextBar()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("context_line_toggle"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showContextBar) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Context line",
                                tint = if (showContextBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Context line",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showContextBar) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
}
