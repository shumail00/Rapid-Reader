package com.shumail.rapidreader.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.shumail.rapidreader.engine.ReadingEngineMode
import com.shumail.rapidreader.engine.ReadingThemeMode
import com.shumail.rapidreader.ui.components.ChapterSelectionSheet
import com.shumail.rapidreader.ui.components.CompletionSummaryDialog
import com.shumail.rapidreader.ui.components.ReaderSettingsSheet
import com.shumail.rapidreader.ui.components.RsvpControlsDeck
import com.shumail.rapidreader.ui.components.RsvpWordDisplay
import com.shumail.rapidreader.ui.components.bouncyClick
import com.shumail.rapidreader.ui.theme.MdDarkPrimary
import com.shumail.rapidreader.ui.theme.getRsvpCanvasPalette
import com.shumail.rapidreader.viewmodel.RsvpUiState
import com.shumail.rapidreader.viewmodel.RsvpViewModel
import com.shumail.rapidreader.viewmodel.ScreenDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RsvpReadingScreen(
    viewModel: RsvpViewModel,
    uiState: RsvpUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val palette = getRsvpCanvasPalette(uiState.themeMode, systemDark)
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showChapterSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val isImmersive = uiState.isFocusMode || uiState.isZenMode

    // Full-screen immersive mode handling: hide status bar and navigation bar in reading mode
    DisposableEffect(isImmersive) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            onDispose { }
        }
    }

    // Prevent Screen Timeout During Reading (Keep-Screen-On)
    DisposableEffect(uiState.isPlaying) {
        val window = (context as? Activity)?.window
        if (uiState.isPlaying && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // If Normal Document / PDF / EPUB / Web Reading Mode is active
    if (uiState.readingEngineMode == ReadingEngineMode.DOCUMENT) {
        DocumentReadingView(
            uiState = uiState,
            palette = palette,
            onBackToHome = { viewModel.backToHome() },
            onWordTapJumpToRsvp = { wordIndex ->
                viewModel.jumpToWordAndStartRsvp(wordIndex)
            },
            onOpenChapters = { showChapterSheet = true },
            onOpenSettings = { showSettingsSheet = true },
            onToggleSave = { viewModel.toggleSaveActiveDocument() },
            onToggleMode = { viewModel.toggleReadingEngineMode() },
            onTogglePdfPageView = { viewModel.togglePdfPageViewMode() },
            onToggleWebContainer = { viewModel.toggleWebContainerMode() },
            onStartRsvpFromPdfPage = { page -> viewModel.startRsvpFromPdfPage(page) },
            modifier = modifier
        )
    } else {
        // RSVP Speed Reading Engine Mode
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .background(palette.backgroundColor),
            containerColor = palette.backgroundColor,
            topBar = {
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.activeTitle.ifBlank { "The Shape of Attention" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val activeCh = uiState.activeChapter
                                val subText = if (activeCh != null) {
                                    "${activeCh.title} · ${uiState.words.size} words"
                                } else {
                                    val metaType = uiState.activeSourceType.ifBlank { "Article" }
                                    "$metaType · ${uiState.words.size} words"
                                }
                                Text(
                                    text = subText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.mutedTextColor.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.backToHome()
                                },
                                modifier = Modifier.testTag("back_to_home_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = palette.textColor
                                )
                            }
                        },
                        actions = {
                            // Switch to Normal Document / PDF Mode Button
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .bouncyClick(scaleDown = 0.92f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleReadingEngineMode()
                                    }
                                    .testTag("switch_to_doc_mode_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Normal Mode",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }

                            // Table of Contents / Chapters Action
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showChapterSheet = true
                                },
                                modifier = Modifier.testTag("rsvp_chapters_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Toc,
                                    contentDescription = "Chapters",
                                    tint = palette.textColor
                                )
                            }

                            // Focus Mode Quick Toggle Action Button
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleFocusMode()
                                },
                                modifier = Modifier.testTag("focus_mode_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CenterFocusStrong,
                                    contentDescription = "Focus Mode",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Bookmark / Saved Action
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleSaveActiveDocument()
                                },
                                modifier = Modifier.testTag("bookmark_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isActiveSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save Document",
                                    tint = if (uiState.isActiveSaved) MaterialTheme.colorScheme.primary else palette.textColor.copy(alpha = 0.85f)
                                )
                            }

                            // More Options Menu
                            Box {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showMenu = true
                                    },
                                    modifier = Modifier.testTag("reader_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = palette.textColor.copy(alpha = 0.85f)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Chapters & TOC") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showChapterSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Normal Document Mode") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.toggleReadingEngineMode()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Focus Mode") },
                                        leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleFocusMode()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            showSettingsSheet = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Cycle Theme") },
                                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                        onClick = {
                                            showMenu = false
                                            val next = when (uiState.themeMode) {
                                                ReadingThemeMode.DYNAMIC -> ReadingThemeMode.OLED_DARK
                                                ReadingThemeMode.OLED_DARK -> ReadingThemeMode.WARM_SEPIA
                                                ReadingThemeMode.WARM_SEPIA -> ReadingThemeMode.MINT_FOCUS
                                                ReadingThemeMode.MINT_FOCUS -> ReadingThemeMode.SOLARIZED_DARK
                                                ReadingThemeMode.SOLARIZED_DARK -> ReadingThemeMode.DYNAMIC
                                            }
                                            viewModel.setThemeMode(next)
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.backgroundColor
                        )
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .then(
                        if (isImmersive) {
                            Modifier.pointerInput(uiState.isPlaying) {
                                detectTapGestures(
                                    onPress = {
                                        var isSlowHold = false
                                        val holdJob = coroutineScope.launch {
                                            delay(160L)
                                            if (uiState.isPlaying) {
                                                isSlowHold = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.onHoldToSlowStart()
                                            }
                                        }
                                        try {
                                            tryAwaitRelease()
                                            holdJob.cancel()
                                            if (isSlowHold) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.onHoldToSlowEnd()
                                            } else {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.togglePlayPause()
                                            }
                                        } catch (e: Exception) {
                                            holdJob.cancel()
                                            if (isSlowHold) {
                                                viewModel.onHoldToSlowEnd()
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Floating Slow-Motion Active Feedback Indicator (Held Down)
                AnimatedVisibility(
                    visible = uiState.isHoldingToSlow,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.85f),
                    exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + scaleOut(targetScale = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .zIndex(20f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Slow Motion (${uiState.dynamicWpm} WPM) • Release to resume",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 960.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Center RSVP Word Focus Box with Guide lines & Context Line (Tappable to toggle play/pause or hold to slow)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (!isImmersive) {
                                    Modifier.pointerInput(uiState.isPlaying) {
                                        detectTapGestures(
                                            onPress = {
                                                var isSlowHold = false
                                                val holdJob = coroutineScope.launch {
                                                    delay(160L)
                                                    if (uiState.isPlaying) {
                                                        isSlowHold = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.onHoldToSlowStart()
                                                    }
                                                }
                                                try {
                                                    tryAwaitRelease()
                                                    holdJob.cancel()
                                                    if (isSlowHold) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.onHoldToSlowEnd()
                                                    } else {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        viewModel.togglePlayPause()
                                                    }
                                                } catch (e: Exception) {
                                                    holdJob.cancel()
                                                    if (isSlowHold) {
                                                        viewModel.onHoldToSlowEnd()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        RsvpWordDisplay(
                            words = uiState.currentChunk,
                            palette = palette,
                            fontFamily = uiState.fontFamily,
                            fontSizeSp = uiState.fontSizeSp,
                            orpColorOption = uiState.orpColor,
                            showGuides = uiState.showGuides,
                            showContextBar = uiState.showContextBar,
                            contextBefore = uiState.contextBefore,
                            contextAfter = uiState.contextAfter,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Reading progress track & Minimalist Metadata Footer Row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .testTag("rsvp_metadata_footer"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Subtle reading progress track
                        LinearProgressIndicator(
                            progress = { uiState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Metadata Footer Row: Current Chapter / Section & Live Dynamic Progress (~Ym left, X%)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val chapterName = uiState.activeChapter?.title?.takeIf { it.isNotBlank() }
                                ?: uiState.activeTitle.takeIf { it.isNotBlank() }
                                ?: "Reading"

                            Text(
                                text = chapterName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            val progressPercent = (uiState.progress * 100).toInt().coerceIn(0, 100)
                            val timeRemaining = uiState.timeRemainingFormatted
                            val progressMeta = if (timeRemaining.isNotBlank() && timeRemaining != "0:00") {
                                "$progressPercent% • ~$timeRemaining left"
                            } else {
                                "$progressPercent%"
                            }

                            Text(
                                text = progressMeta,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Material You 3 Controls Deck with Wavy Scrubber (Hidden in Focus/Zen Mode)
                    AnimatedVisibility(
                        visible = !isImmersive,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        RsvpControlsDeck(
                            isPlaying = uiState.isPlaying,
                            currentIndex = uiState.currentIndex,
                            totalWords = uiState.words.size,
                            progress = uiState.progress,
                            wpm = uiState.wpm,
                            timeRemainingFormatted = uiState.timeRemainingFormatted,
                            palette = palette,
                            chunkSize = uiState.chunkSize,
                            showContextBar = uiState.showContextBar,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onStop = { viewModel.stop() },
                            onSkipBackward = { count -> viewModel.skipBackward(count) },
                            onSkipForward = { count -> viewModel.skipForward(count) },
                            onSeek = { target -> viewModel.seekTo(target) },
                            onWpmChange = { newWpm -> viewModel.setWpm(newWpm) },
                            onChunkSizeChange = { newChunk -> viewModel.setChunkSize(newChunk) },
                            onToggleContextBar = { viewModel.toggleContextBar() },
                            onRestart = { viewModel.restartFromBeginning() }
                        )
                    }

                    // 3. Focus Mode Unobtrusive Bottom Pill
                    if (isImmersive) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = palette.surfaceColor.copy(alpha = 0.85f),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .padding(bottom = 20.dp)
                                .testTag("focus_mode_indicator")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (uiState.isPlaying) "Focus Mode • Playing" else "Focus Mode • Paused (Tap to play)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textColor,
                                    fontWeight = FontWeight.Medium
                                )

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.bouncyClick {
                                        viewModel.toggleFocusMode()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FullscreenExit,
                                            contentDescription = "Exit Focus Mode",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Exit",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Chapters / Table of Contents Modal Bottom Sheet
    if (showChapterSheet) {
        ChapterSelectionSheet(
            uiState = uiState,
            onChapterSelect = { chapter ->
                viewModel.jumpToChapter(chapter)
            },
            onDismiss = { showChapterSheet = false }
        )
    }

    // Settings Modal Bottom Sheet
    if (showSettingsSheet) {
        ReaderSettingsSheet(
            chunkSize = uiState.chunkSize,
            themeMode = uiState.themeMode,
            fontFamily = uiState.fontFamily,
            fontSizeSp = uiState.fontSizeSp,
            orpColorOption = uiState.orpColor,
            showGuides = uiState.showGuides,
            showContextBar = uiState.showContextBar,
            rewindOnResume = uiState.rewindOnResume,
            punctuationPauseEnabled = uiState.punctuationPauseEnabled,
            onChunkSizeChange = { viewModel.setChunkSize(it) },
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onFontFamilyChange = { viewModel.setFontFamily(it) },
            onFontSizeChange = { viewModel.setFontSize(it) },
            onOrpColorChange = { viewModel.setOrpColor(it) },
            onToggleGuides = { viewModel.toggleGuides() },
            onToggleContextBar = { viewModel.toggleContextBar() },
            onToggleRewindOnResume = { viewModel.toggleRewindOnResume() },
            onTogglePunctuationPause = { viewModel.togglePunctuationPause() },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Completion Summary Dialog
    if (uiState.currentScreen == ScreenDestination.COMPLETION_SUMMARY || uiState.isCompleted) {
        CompletionSummaryDialog(
            title = uiState.activeTitle,
            totalWords = uiState.words.size,
            wpm = uiState.wpm,
            durationSeconds = uiState.sessionDurationSeconds,
            onRestart = { viewModel.restartFromBeginning() },
            onReturnHome = { viewModel.backToHome() }
        )
    }
}

