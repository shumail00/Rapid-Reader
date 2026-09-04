package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ReadingDocument
import com.example.data.ReadingSession
import com.example.data.SamplePreset
import com.example.data.SamplePresets
import com.example.ui.components.AppNavTab
import com.example.ui.components.BookCoverView
import com.example.ui.components.CircularWavyProgressIndicator
import com.example.ui.components.DirectTextInputDialog
import com.example.ui.components.FloatingNavigationRail
import com.example.ui.components.MobileBottomNavigationBar
import com.example.ui.components.ReaderSettingsSheet
import com.example.ui.components.WebImportDialog
import com.example.ui.components.bouncyClick
import com.example.viewmodel.RsvpUiState
import com.example.viewmodel.RsvpViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: RsvpViewModel,
    uiState: RsvpUiState,
    savedDocuments: List<ReadingDocument>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var currentTab by remember { mutableStateOf(AppNavTab.HOME) }
    var selectedFilter by remember { mutableStateOf("All") }
    var showWebDialog by remember { mutableStateOf(false) }
    var showDirectTextDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var documentToDelete by remember { mutableStateOf<ReadingDocument?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // File pickers
    val txtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromUri(it, "txt", context) }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromUri(it, "pdf", context) }
    }

    val epubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromUri(it, "epub", context) }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // Dynamic Greeting String without streak: just day and time (e.g., "Tuesday evening", "Wednesday morning")
    val greetingSubtitle = remember {
        val cal = Calendar.getInstance()
        val dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
        "$dayName $timeOfDay"
    }

    val contentBg = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompactMobile = maxWidth < 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = contentBg,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (isCompactMobile && currentTab == AppNavTab.HOME) {
                    FloatingActionButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDirectTextDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .padding(bottom = 84.dp)
                            .testTag("mobile_fab_new_read")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Reading",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = innerPadding.calculateTopPadding(),
                        start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = 0.dp
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. Floating Pill-Shaped Navigation Rail for Tablets / Wide Screens Only
                    if (!isCompactMobile) {
                        FloatingNavigationRail(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it },
                            onNewReadingClick = { showDirectTextDialog = true },
                            onSettingsClick = { showSettingsSheet = true }
                        )
                    }

                    // 2. Main Content Canvas with Animated Tab Transitions
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                                .togetherWith(fadeOut(animationSpec = tween(180)))
                        },
                        label = "tab_content_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            AppNavTab.HOME -> {
                                HomeMainContent(
                                    viewModel = viewModel,
                                    savedDocuments = savedDocuments,
                                    greetingSubtitle = greetingSubtitle,
                                    selectedFilter = selectedFilter,
                                    isCompactMobile = isCompactMobile,
                                    onFilterChange = { selectedFilter = it },
                                    onShowDirectText = { showDirectTextDialog = true },
                                    onShowWebDialog = { showWebDialog = true },
                                    onLaunchTxt = { txtLauncher.launch("text/*") },
                                    onLaunchPdf = { pdfLauncher.launch("application/pdf") },
                                    onLaunchEpub = { epubLauncher.launch("*/*") },
                                    onSearchClick = { currentTab = AppNavTab.SEARCH },
                                    onThisWeekClick = { currentTab = AppNavTab.PROGRESS },
                                    onOpenSettings = { showSettingsSheet = true },
                                    onDeleteDoc = { documentToDelete = it }
                                )
                            }
                            AppNavTab.LIBRARY -> {
                                LibraryGridScreen(
                                    documents = savedDocuments,
                                    viewModel = viewModel
                                )
                            }
                            AppNavTab.PROGRESS -> {
                                AnalyticsDashboardScreen(
                                    viewModel = viewModel,
                                    savedDocuments = savedDocuments,
                                    onStartNewReadClick = { showDirectTextDialog = true }
                                )
                            }
                            AppNavTab.SAVED -> {
                                SavedView(
                                    documents = savedDocuments,
                                    viewModel = viewModel
                                )
                            }
                            AppNavTab.SEARCH -> {
                                SearchView(
                                    documents = savedDocuments,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

                    // Global Loading Overlay with Circular Wavy Progress Indicator
                    if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Card(
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                                modifier = Modifier
                                    .padding(32.dp)
                                    .testTag("loading_overlay_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularWavyProgressIndicator(
                                        size = 76.dp,
                                        waveCount = 8,
                                        strokeWidth = 4.5.dp,
                                        primaryColor = MaterialTheme.colorScheme.primary,
                                        secondaryColor = MaterialTheme.colorScheme.tertiary
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = uiState.loadingMessage ?: "Processing Document...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Powered by PDFBox Engine",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Floating Mobile Bottom Navigation Bar (Floats directly above content with zero bottom bar / black gap)
            if (isCompactMobile) {
                MobileBottomNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Dialogs
    if (showWebDialog) {
        WebImportDialog(
            onDismiss = { showWebDialog = false },
            onFetchUrl = { url ->
                showWebDialog = false
                viewModel.loadFromWeb(url, openInWebContainer = false)
            },
            onOpenInWebContainer = { url ->
                showWebDialog = false
                viewModel.loadFromWeb(url, openInWebContainer = true)
            }
        )
    }

    if (showDirectTextDialog) {
        DirectTextInputDialog(
            onDismiss = { showDirectTextDialog = false },
            onSubmitText = { title, content ->
                showDirectTextDialog = false
                viewModel.loadDirectText(title, content)
            }
        )
    }

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

    // Delete Confirmation Dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to remove \"${doc.title}\" from your library?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        documentToDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
}

@Composable
private fun HomeMainContent(
    viewModel: RsvpViewModel,
    savedDocuments: List<ReadingDocument>,
    greetingSubtitle: String,
    selectedFilter: String,
    isCompactMobile: Boolean,
    onFilterChange: (String) -> Unit,
    onShowDirectText: () -> Unit,
    onShowWebDialog: () -> Unit,
    onLaunchTxt: () -> Unit,
    onLaunchPdf: () -> Unit,
    onLaunchEpub: () -> Unit,
    onSearchClick: () -> Unit,
    onThisWeekClick: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteDoc: (ReadingDocument) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sessions by viewModel.readingSessions.collectAsState()
    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val cardBgHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val heroPrimaryBg = MaterialTheme.colorScheme.primaryContainer
    val heroPrimaryOnColor = MaterialTheme.colorScheme.onPrimaryContainer
    val heroTertiaryBg = MaterialTheme.colorScheme.tertiaryContainer
    val heroTertiaryOnColor = MaterialTheme.colorScheme.onTertiaryContainer
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (isCompactMobile) 16.dp else 24.dp, vertical = if (isCompactMobile) 12.dp else 16.dp),
        contentPadding = PaddingValues(bottom = if (isCompactMobile) 96.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCompactMobile) 16.dp else 22.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = greetingSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textSecondary.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "What are we reading?",
                        style = if (isCompactMobile) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                }

                if (isCompactMobile) {
                    Surface(
                        shape = CircleShape,
                        color = cardBgHigh,
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenSettings()
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Top Cards (Continue Card + This Week Stats Card)
        item {
            val defaultPreset = SamplePresets.presets.first()
            val activeDoc = savedDocuments.firstOrNull()

            if (isCompactMobile) {
                // Mobile: Stacked Vertical Cards for readability
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. Hero Continue Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = heroPrimaryBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .bouncyClick(scaleDown = 0.97f) {
                                if (activeDoc != null) {
                                    viewModel.resumeDocument(activeDoc)
                                } else {
                                    viewModel.loadPreset(defaultPreset)
                                }
                            }
                            .testTag("hero_continue_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.76f),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (activeDoc != null) "CONTINUE" else "START READING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = heroPrimaryOnColor.copy(alpha = 0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = activeDoc?.title ?: "Ready for your first read",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = heroPrimaryOnColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (activeDoc != null) {
                                            "${activeDoc.sourceType.uppercase()} · ${activeDoc.totalWords} words"
                                        } else {
                                            "Sample drill · Tap to begin RSVP"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = heroPrimaryOnColor.copy(alpha = 0.75f)
                                    )
                                }

                                Column {
                                    val progressVal = activeDoc?.progressPercentage ?: 0f
                                    val progressPct = (progressVal * 100).toInt()
                                    val paceWpm = activeDoc?.preferredWpm ?: viewModel.uiState.value.wpm
                                    val minutesLeft = if (activeDoc != null) {
                                        ((activeDoc.totalWords * (1f - progressVal)) / paceWpm).toInt().coerceAtLeast(1)
                                    } else {
                                        0
                                    }

                                    LinearProgressIndicator(
                                        progress = { progressVal },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (activeDoc != null) {
                                            "$progressPct% · $minutesLeft min left at $paceWpm wpm"
                                        } else {
                                            "0% · Tap to start reading"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = heroPrimaryOnColor.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            // Play button in bottom right
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(52.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. THIS WEEK Stats Card (Compact Row for Mobile)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bouncyClick(scaleDown = 0.97f) {
                                onThisWeekClick()
                            }
                            .testTag("hero_stats_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val totalWordsCalculated = if (sessions.isNotEmpty()) {
                                sessions.sumOf { s: ReadingSession -> s.wordsRead }
                            } else {
                                savedDocuments.sumOf { d: ReadingDocument -> d.currentWordIndex }
                            }

                            val wordsDisplay = if (totalWordsCalculated >= 1000) {
                                "${String.format(Locale.US, "%.1f", totalWordsCalculated / 1000.0)} k"
                            } else {
                                totalWordsCalculated.toString()
                            }
                            val sessionCount = sessions.size
                            val avgWpm = if (sessions.isNotEmpty()) sessions.map { s: ReadingSession -> s.averageWpm }.average().toInt() else 0

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = cardBgHigh,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = wordsDisplay,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "words read this week",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textSecondary.copy(alpha = 0.8f)
                                        )
                                    }
                                    Text(
                                        text = if (sessionCount > 0) "$sessionCount sessions · avg $avgWpm wpm" else "Tap to view statistics",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textSecondary.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Progress",
                                tint = textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else {
                // Wide / Tablet: Side-by-side Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Hero Continue Card
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = heroPrimaryBg),
                        modifier = Modifier
                            .weight(1.35f)
                            .height(210.dp)
                            .bouncyClick(scaleDown = 0.97f) {
                                if (activeDoc != null) {
                                    viewModel.resumeDocument(activeDoc)
                                } else {
                                    viewModel.loadPreset(defaultPreset)
                                }
                            }
                            .testTag("hero_continue_card")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(22.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.72f),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = if (activeDoc != null) "CONTINUE" else "START READING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = heroPrimaryOnColor.copy(alpha = 0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = activeDoc?.title ?: "Ready for your first read",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = heroPrimaryOnColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (activeDoc != null) {
                                            "${activeDoc.sourceType.uppercase()} · ${activeDoc.totalWords} words"
                                        } else {
                                            "Sample drill · Tap to begin RSVP"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = heroPrimaryOnColor.copy(alpha = 0.75f)
                                    )
                                }

                                Column {
                                    val progressVal = activeDoc?.progressPercentage ?: 0f
                                    val progressPct = (progressVal * 100).toInt()
                                    val paceWpm = activeDoc?.preferredWpm ?: viewModel.uiState.value.wpm
                                    val minutesLeft = if (activeDoc != null) {
                                        ((activeDoc.totalWords * (1f - progressVal)) / paceWpm).toInt().coerceAtLeast(1)
                                    } else {
                                        0
                                    }

                                    LinearProgressIndicator(
                                        progress = { progressVal },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (activeDoc != null) {
                                            "$progressPct% · $minutesLeft min left at $paceWpm wpm"
                                        } else {
                                            "0% · Tap to start reading"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = heroPrimaryOnColor.copy(alpha = 0.9f)
                                    )
                                }
                            }

                            // Play button in bottom right
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(64.dp)
                                    .align(Alignment.BottomEnd)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
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

                    // 2. THIS WEEK Stats Card (Clickable to open ProgressView)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(210.dp)
                            .bouncyClick(scaleDown = 0.97f) {
                                onThisWeekClick()
                            }
                            .testTag("hero_stats_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(22.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "THIS WEEK",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp,
                                        color = textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "View Progress",
                                    tint = textSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val totalWordsCalculated = if (sessions.isNotEmpty()) {
                                sessions.sumOf { s: ReadingSession -> s.wordsRead }
                            } else {
                                savedDocuments.sumOf { d: ReadingDocument -> d.currentWordIndex }
                            }

                            val wordsDisplay = if (totalWordsCalculated >= 1000) {
                                "${String.format(Locale.US, "%.1f", totalWordsCalculated / 1000.0)} k"
                            } else {
                                totalWordsCalculated.toString()
                            }

                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = wordsDisplay,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "words",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = textSecondary.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val sessionCount = sessions.size
                                val avgWpm = if (sessions.isNotEmpty()) sessions.map { s: ReadingSession -> s.averageWpm }.average().toInt() else 0
                                Text(
                                    text = if (sessionCount > 0) {
                                        "$sessionCount sessions · avg $avgWpm wpm"
                                    } else if (totalWordsCalculated > 0) {
                                        "${savedDocuments.size} documents in progress"
                                    } else {
                                        "0 sessions · Tap for progress"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // "Start something new" Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Start something new",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Direct text card
                    item {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = heroTertiaryBg),
                            modifier = Modifier
                                .width(if (isCompactMobile) 220.dp else 250.dp)
                                .height(if (isCompactMobile) 115.dp else 125.dp)
                                .bouncyClick(scaleDown = 0.96f) {
                                    onShowDirectText()
                                }
                                .testTag("source_direct_olive_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(if (isCompactMobile) 14.dp else 18.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = heroTertiaryOnColor,
                                    modifier = Modifier.size(if (isCompactMobile) 24.dp else 28.dp)
                                )
                                Column {
                                    Text(
                                        text = "Type or paste text",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = heroTertiaryOnColor
                                    )
                                    Text(
                                        text = "Fastest way in — start in one tap",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = heroTertiaryOnColor.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Text File Card
                    item {
                        SourceOptionCard(
                            title = "Text file",
                            subtitle = ".txt, .md",
                            icon = Icons.Default.Description,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            isCompact = isCompactMobile,
                            onClick = onLaunchTxt,
                            testTag = "source_txt_card"
                        )
                    }

                    // 3. PDF Card
                    item {
                        SourceOptionCard(
                            title = "PDF",
                            subtitle = "extract text",
                            icon = Icons.Default.PictureAsPdf,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            isCompact = isCompactMobile,
                            onClick = onLaunchPdf,
                            testTag = "source_pdf_card"
                        )
                    }

                    // 4. EPUB Card
                    item {
                        SourceOptionCard(
                            title = "EPUB",
                            subtitle = "by chapter",
                            icon = Icons.Default.MenuBook,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            isCompact = isCompactMobile,
                            onClick = onLaunchEpub,
                            testTag = "source_epub_card"
                        )
                    }

                    // 5. Web Page Card
                    item {
                        SourceOptionCard(
                            title = "Web page",
                            subtitle = "paste a link",
                            icon = Icons.Default.Language,
                            cardBg = cardBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            isCompact = isCompactMobile,
                            onClick = onShowWebDialog,
                            testTag = "source_web_card"
                        )
                    }
                }
            }
        }

        // "Recent" Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )

                    // Filter Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("All", "Essays", "Books", "Web").forEach { tab ->
                            val isSelected = selectedFilter == tab
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else cardBgHigh,
                                modifier = Modifier
                                    .bouncyClick(scaleDown = 0.92f) { onFilterChange(tab) }
                                    .testTag("filter_$tab")
                            ) {
                                Text(
                                    text = tab,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else textSecondary,
                                    modifier = Modifier.padding(horizontal = if (isCompactMobile) 10.dp else 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Items list
        val filteredDocs = when (selectedFilter) {
            "Essays" -> savedDocuments.filter { it.sourceType == "preset" || it.sourceType == "typed" }
            "Books" -> savedDocuments.filter { it.sourceType == "pdf" || it.sourceType == "epub" }
            "Web" -> savedDocuments.filter { it.sourceType == "web" }
            else -> savedDocuments
        }

        if (filteredDocs.isNotEmpty()) {
            items(filteredDocs) { doc ->
                RecentItemCard(
                    document = doc,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    isCompact = isCompactMobile,
                    onResume = { viewModel.resumeDocument(doc) },
                    onDelete = { onDeleteDoc(doc) }
                )
            }
        } else {
            // Show Sample Presets as starters if no recent documents exist
            items(SamplePresets.presets) { preset ->
                SamplePresetItemCard(
                    preset = preset,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onRead = { viewModel.loadPreset(preset) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SourceOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isCompact: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .width(if (isCompact) 140.dp else 170.dp)
            .height(if (isCompact) 115.dp else 125.dp)
            .bouncyClick(scaleDown = 0.96f) { onClick() }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isCompact) 14.dp else 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(if (isCompact) 22.dp else 26.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun RecentItemCard(
    document: ReadingDocument,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    isCompact: Boolean = false,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    val progressPct = (document.progressPercentage * 100).toInt()

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(scaleDown = 0.98f) { onResume() }
            .testTag("recent_doc_${document.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = if (isCompact) 10.dp else 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Book Cover Thumbnail
                BookCoverView(
                    document = document,
                    modifier = Modifier
                        .width(if (isCompact) 36.dp else 44.dp)
                        .height(if (isCompact) 50.dp else 60.dp)
                )

                Column {
                    Text(
                        text = document.title,
                        style = if (isCompact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${document.sourceType.uppercase()} · ${document.totalWords} words",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.width(if (isCompact) 56.dp else 80.dp)
                ) {
                    Text(
                        text = "$progressPct%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { document.progressPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SamplePresetItemCard(
    preset: SamplePreset,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onRead: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClick(scaleDown = 0.98f) { onRead() }
            .testTag("recent_preset_${preset.title.replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${preset.category} · ${preset.wordCount} words · est. ${preset.readingTimeEst}",
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = textSecondary
            )
        }
    }
}

@Composable
private fun ProgressView(
    viewModel: RsvpViewModel,
    savedDocuments: List<ReadingDocument>,
    onStartNewReadClick: () -> Unit
) {
    AnalyticsDashboardScreen(
        viewModel = viewModel,
        savedDocuments = savedDocuments,
        onStartNewReadClick = onStartNewReadClick
    )
}

@Composable
private fun SavedView(
    documents: List<ReadingDocument>,
    viewModel: RsvpViewModel
) {
    val savedDocs = documents.filter { it.isFavorite }
    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Saved Bookmarks",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${savedDocs.size} pinned items",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary
            )
        }

        if (savedDocs.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No saved bookmarks yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bookmark documents while reading to quickly return to them",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }
                }
            }
        } else {
            items(savedDocs) { doc ->
                RecentItemCard(
                    document = doc,
                    cardBg = cardBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onResume = { viewModel.resumeDocument(doc) },
                    onDelete = { viewModel.deleteDocument(doc.id) }
                )
            }
        }
    }
}

@Composable
private fun SearchView(
    documents: List<ReadingDocument>,
    viewModel: RsvpViewModel
) {
    var searchQuery by remember { mutableStateOf("") }
    val cardBg = MaterialTheme.colorScheme.surfaceContainer
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    val searchResults = if (searchQuery.isBlank()) {
        documents
    } else {
        documents.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.content.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Search Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search title, authors, or text content...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = textSecondary
                    )
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_text_input")
            )
        }

        items(searchResults) { doc ->
            RecentItemCard(
                document = doc,
                cardBg = cardBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                onResume = { viewModel.resumeDocument(doc) },
                onDelete = { viewModel.deleteDocument(doc.id) }
            )
        }
    }
}
