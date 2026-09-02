package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.DocumentChapter
import com.example.engine.ReadingFontFamily
import com.example.engine.RsvpWord
import com.example.ui.components.DriveStylePdfViewer
import com.example.ui.components.WebContainerView
import com.example.ui.components.bouncyClick
import com.example.ui.theme.RsvpCanvasPalette
import com.example.viewmodel.RsvpUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentReadingView(
    uiState: RsvpUiState,
    palette: RsvpCanvasPalette,
    onBackToHome: () -> Unit,
    onWordTapJumpToRsvp: (Int) -> Unit,
    onOpenChapters: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleMode: () -> Unit,
    onTogglePdfPageView: () -> Unit = {},
    onToggleWebContainer: () -> Unit = {},
    onStartRsvpFromPdfPage: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // If PDF and Google Drive viewer mode is enabled
    if (uiState.activeSourceType.equals("pdf", ignoreCase = true) && uiState.isPdfPageViewMode && uiState.activeLocalFilePath != null) {
        DriveStylePdfViewer(
            pdfFilePath = uiState.activeLocalFilePath,
            documentTitle = uiState.activeTitle,
            palette = palette,
            onBack = onBackToHome,
            onStartRsvpFromPage = { page -> onStartRsvpFromPdfPage(page) },
            onSwitchToWordView = onTogglePdfPageView,
            onOpenChapters = onOpenChapters,
            modifier = modifier
        )
        return
    }

    // If Web page and live Web Container mode is enabled
    if (uiState.activeSourceType.equals("web", ignoreCase = true) && uiState.isWebContainerMode && uiState.activeWebUrl != null) {
        WebContainerView(
            url = uiState.activeWebUrl,
            documentTitle = uiState.activeTitle,
            palette = palette,
            onBack = onBackToHome,
            onStartRsvp = { onWordTapJumpToRsvp(0) },
            onSwitchToWordView = onToggleWebContainer,
            onSaveToLibrary = onToggleSave,
            onOpenChapters = onOpenChapters,
            modifier = modifier
        )
        return
    }

    // Interactive Word Tap / Text Document & EPUB Viewer
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val chapters = uiState.chapters
    val activeChapterIndex = uiState.activeChapterIndex
    val activeChapter = uiState.activeChapter
    val currentIndex = uiState.currentIndex

    val composeFontFamily = when (uiState.fontFamily) {
        ReadingFontFamily.SERIF -> FontFamily.Serif
        ReadingFontFamily.MONOSPACE -> FontFamily.Monospace
        ReadingFontFamily.CURSIVE -> FontFamily.Cursive
        ReadingFontFamily.SANS_SERIF -> FontFamily.SansSerif
    }

    val baseFontSize = (uiState.fontSizeSp * 0.42f).coerceIn(16f, 32f).sp
    val lineHeight = (baseFontSize.value * 1.55f).sp

    // Group words into paragraphs based on isParagraphBreak flag
    val paragraphs = remember(uiState.words) {
        val groups = mutableListOf<List<RsvpWord>>()
        var currentPara = mutableListOf<RsvpWord>()
        for (w in uiState.words) {
            currentPara.add(w)
            if (w.isParagraphBreak) {
                groups.add(currentPara)
                currentPara = mutableListOf()
            }
        }
        if (currentPara.isNotEmpty()) {
            groups.add(currentPara)
        }
        groups
    }

    LaunchedEffect(Unit) {
        val targetParaIndex = paragraphs.indexOfFirst { para ->
            para.any { it.wordIndex == currentIndex }
        }
        if (targetParaIndex > 0) {
            listState.animateScrollToItem((targetParaIndex + 1).coerceAtMost(paragraphs.size))
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundColor),
        containerColor = palette.backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.activeTitle.ifBlank { "Document" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subHeader = if (activeChapter != null) {
                            "${activeChapter.title} · ${uiState.activeSourceType.uppercase()}"
                        } else {
                            "${uiState.activeSourceType.uppercase()} · ${uiState.words.size} words"
                        }
                        Text(
                            text = subHeader,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.mutedTextColor.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBackToHome()
                        },
                        modifier = Modifier.testTag("doc_back_to_home_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textColor
                        )
                    }
                },
                actions = {
                    // PDF Drive viewer toggle (if PDF)
                    if (uiState.activeSourceType.equals("pdf", ignoreCase = true) && uiState.activeLocalFilePath != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onTogglePdfPageView()
                            },
                            modifier = Modifier.testTag("toggle_pdf_drive_viewer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "Google Drive PDF View",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Web Container toggle (if Web)
                    if (uiState.activeSourceType.equals("web", ignoreCase = true) && uiState.activeWebUrl != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleWebContainer()
                            },
                            modifier = Modifier.testTag("toggle_web_container_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Live Web View",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Mode Switcher Button (RSVP Speed)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .bouncyClick(scaleDown = 0.92f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleMode()
                            }
                            .testTag("switch_to_rsvp_mode_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "RSVP Speed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Chapter TOC Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenChapters()
                        },
                        modifier = Modifier.testTag("doc_chapters_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Toc,
                            contentDescription = "Chapters",
                            tint = palette.textColor
                        )
                    }

                    // Bookmark Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleSave()
                        },
                        modifier = Modifier.testTag("doc_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isActiveSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save Document",
                            tint = if (uiState.isActiveSaved) MaterialTheme.colorScheme.primary else palette.textColor.copy(alpha = 0.85f)
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag("doc_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = palette.textColor.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.backgroundColor
                )
            )
        },
        bottomBar = {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = palette.surfaceColor,
                border = BorderStroke(1.dp, palette.guideColor.copy(alpha = 0.3f)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(8.dp)
                            ) {}
                            Text(
                                text = "Position: Word ${currentIndex + 1} of ${uiState.words.size} (${(uiState.progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.mutedTextColor
                            )
                        }

                        Text(
                            text = "Tap any word to RSVP Read",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { uiState.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = palette.guideColor.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .bouncyClick(scaleDown = 0.95f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenChapters()
                                }
                                .testTag("doc_bottom_chapters_button")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = palette.textColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Chapters (${chapters.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textColor
                                )
                            }
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleMode()
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1.8f)
                                .height(46.dp)
                                .bouncyClick(scaleDown = 0.95f) {
                                    onToggleMode()
                                }
                                .testTag("doc_bottom_play_rsvp_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Resume RSVP Speed",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            // Header Info Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surfaceColor,
                    border = BorderStroke(1.dp, palette.guideColor.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = uiState.activeTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = uiState.activeSourceType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "· ${uiState.words.size} words · ${chapters.size} chapters",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.mutedTextColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Paragraphs and interactive words
            itemsIndexed(paragraphs, key = { index, _ -> "para_$index" }) { paraIndex, paraWords ->
                // Check if this paragraph is a chapter header
                val firstWord = paraWords.firstOrNull()
                val isChapterHeader = firstWord?.original?.startsWith("#") == true

                if (isChapterHeader) {
                    val headerText = paraWords.joinToString(" ") { it.original }.replace(Regex("""^#+\s*"""), "")
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = palette.textColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (word in paraWords) {
                            val isCurrentWord = (word.wordIndex == currentIndex)
                            val isPastWord = (word.wordIndex < currentIndex)

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isCurrentWord) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isPastWord) {
                                    Color.Transparent
                                } else {
                                    Color.Transparent
                                },
                                modifier = Modifier
                                    .bouncyClick(scaleDown = 0.90f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onWordTapJumpToRsvp(word.wordIndex)
                                    }
                                    .testTag("doc_word_${word.wordIndex}")
                            ) {
                                Text(
                                    text = word.fullDisplay,
                                    fontSize = baseFontSize,
                                    lineHeight = lineHeight,
                                    fontFamily = composeFontFamily,
                                    fontWeight = if (isCurrentWord) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentWord) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else if (isPastWord) {
                                        palette.textColor.copy(alpha = 0.70f)
                                    } else {
                                        palette.textColor
                                    },
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
