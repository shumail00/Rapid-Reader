package com.shumail.rapidreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shumail.rapidreader.engine.OrpColorOption
import com.shumail.rapidreader.engine.ReadingFontFamily
import com.shumail.rapidreader.engine.RsvpWord
import com.shumail.rapidreader.ui.theme.RsvpCanvasPalette

@Composable
fun RsvpWordDisplay(
    words: List<RsvpWord>,
    palette: RsvpCanvasPalette,
    fontFamily: ReadingFontFamily,
    fontSizeSp: Float,
    orpColorOption: OrpColorOption,
    showGuides: Boolean,
    showContextBar: Boolean,
    contextBefore: String,
    contextAfter: String,
    modifier: Modifier = Modifier
) {
    val composeFontFamily = when (fontFamily) {
        ReadingFontFamily.SANS_SERIF -> FontFamily.SansSerif
        ReadingFontFamily.SERIF -> FontFamily.Serif
        ReadingFontFamily.MONOSPACE -> FontFamily.Monospace
        ReadingFontFamily.CURSIVE -> FontFamily.Cursive
    }
    val orpColor = if (orpColorOption == OrpColorOption.DYNAMIC) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(orpColorOption.hexCode)
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val isCompactMobile = maxWidth < 600.dp
        // Fluid scaled responsive font size for phones vs tablets
        val effectiveFontSize = if (isCompactMobile) {
            fontSizeSp.coerceIn(24f, 48f)
        } else {
            fontSizeSp
        }
        val viewportHeight = if (isCompactMobile) 190.dp else 240.dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompactMobile) 4.dp else 8.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Main RSVP Focal Viewport Container
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight)
                    .clip(RoundedCornerShape(if (isCompactMobile) 24.dp else 32.dp))
                    .background(Color.Transparent)
                    .testTag("rsvp_word_viewport"),
                contentAlignment = Alignment.Center
            ) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val centerX = widthPx / 2f

                // Visual Focus Guides & Center Notches (ORP Crosshairs)
                if (showGuides) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val guideYTop = heightPx * 0.20f
                        val guideYBottom = heightPx * 0.80f
                        val notchLength = if (isCompactMobile) 12.dp.toPx() else 16.dp.toPx()

                        // Top horizontal line
                        drawLine(
                            color = palette.guideColor.copy(alpha = 0.25f),
                            start = Offset(x = 12.dp.toPx(), y = guideYTop),
                            end = Offset(x = size.width - 12.dp.toPx(), y = guideYTop),
                            strokeWidth = 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Top ORP Center Notch
                        drawLine(
                            color = orpColor,
                            start = Offset(x = centerX, y = guideYTop - 2.dp.toPx()),
                            end = Offset(x = centerX, y = guideYTop + notchLength),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Bottom horizontal line
                        drawLine(
                            color = palette.guideColor.copy(alpha = 0.25f),
                            start = Offset(x = 12.dp.toPx(), y = guideYBottom),
                            end = Offset(x = size.width - 12.dp.toPx(), y = guideYBottom),
                            strokeWidth = 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Bottom ORP Center Notch
                        drawLine(
                            color = orpColor,
                            start = Offset(x = centerX, y = guideYBottom - notchLength),
                            end = Offset(x = centerX, y = guideYBottom + 2.dp.toPx()),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Word Rendering with Anchored ORP Alignment
                if (words.isNotEmpty()) {
                    val firstWord = words.first()
                    val additionalWords = if (words.size > 1) words.drop(1) else emptyList()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompactMobile) 10.dp else 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Prefix Box (anchored rightwards towards the center pivot)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = firstWord.prefix,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = composeFontFamily,
                                    fontSize = effectiveFontSize.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.5.sp
                                ),
                                color = palette.textColor,
                                textAlign = TextAlign.End,
                                maxLines = 1
                            )
                        }

                        // Center ORP Character (anchored strictly at the focus target)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 0.5.dp)
                        ) {
                            Text(
                                text = firstWord.orpChar,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = composeFontFamily,
                                    fontSize = effectiveFontSize.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = orpColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }

                        // Right Suffix Box (anchored leftwards from the center pivot)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val suffixAndAdditional = buildString {
                                append(firstWord.suffix)
                                if (additionalWords.isNotEmpty()) {
                                    append(" ")
                                    append(additionalWords.joinToString(" ") { it.fullDisplay })
                                }
                            }

                            Text(
                                text = suffixAndAdditional,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = composeFontFamily,
                                    fontSize = effectiveFontSize.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.5.sp
                                ),
                                color = palette.textColor,
                                textAlign = TextAlign.Start,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Ready to Read",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = composeFontFamily,
                            fontSize = if (isCompactMobile) 22.sp else 28.sp
                        ),
                        color = palette.mutedTextColor
                    )
                }
            }

            // Context Preview Sentence Line
            if (showContextBar) {
                Spacer(modifier = Modifier.height(if (isCompactMobile) 8.dp else 14.dp))
                val currentWordRaw = words.joinToString(" ") { it.fullDisplay }

                val annotatedContext = buildAnnotatedString {
                    if (contextBefore.isNotBlank()) {
                        withStyle(SpanStyle(color = palette.mutedTextColor.copy(alpha = 0.65f), fontWeight = FontWeight.Normal)) {
                            append("$contextBefore ")
                        }
                    }

                    if (currentWordRaw.isNotBlank()) {
                        withStyle(SpanStyle(color = palette.textColor, fontWeight = FontWeight.Bold)) {
                            append(currentWordRaw)
                        }
                    }

                    if (contextAfter.isNotBlank()) {
                        withStyle(SpanStyle(color = palette.mutedTextColor.copy(alpha = 0.65f), fontWeight = FontWeight.Normal)) {
                            append(" $contextAfter")
                        }
                    }
                }

                Text(
                    text = annotatedContext,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = composeFontFamily,
                        fontSize = if (isCompactMobile) 13.sp else 15.sp,
                        lineHeight = if (isCompactMobile) 18.sp else 22.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth(if (isCompactMobile) 0.98f else 0.9f)
                        .padding(horizontal = if (isCompactMobile) 8.dp else 16.dp)
                )
            }
        }
    }
}
