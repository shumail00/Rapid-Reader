package com.shumail.rapidreader.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shumail.rapidreader.data.BookItem
import com.shumail.rapidreader.data.ReadingDocument
import com.shumail.rapidreader.data.ReadingSession
import com.shumail.rapidreader.data.toBookItem
import com.shumail.rapidreader.viewmodel.RsvpViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DailyActivityData(
    val dayLabel: String,
    val minutes: Float,
    val isToday: Boolean
)

@Composable
fun AnalyticsDashboardScreen(
    viewModel: RsvpViewModel,
    savedDocuments: List<ReadingDocument>,
    onStartNewReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.readingSessions.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    // 1. Calculate Quick Metrics
    val totalWordsRead = remember(sessions, savedDocuments) {
        if (sessions.isNotEmpty()) {
            sessions.sumOf { it.wordsRead }
        } else {
            savedDocuments.sumOf { it.currentWordIndex }
        }
    }

    val totalDurationSeconds = remember(sessions) {
        sessions.sumOf { it.durationSeconds }
    }

    val totalHours = totalDurationSeconds / 3600
    val totalMinutesRemainder = (totalDurationSeconds % 3600) / 60
    val readingTimeFormatted = if (totalHours > 0) {
        "${totalHours}h ${totalMinutesRemainder}m"
    } else {
        "${totalDurationSeconds / 60}m"
    }

    val averageWpm = remember(sessions, totalWordsRead, totalDurationSeconds) {
        if (totalDurationSeconds > 0) {
            ((totalWordsRead.toDouble() / totalDurationSeconds.toDouble()) * 60).toInt().coerceIn(100, 1500)
        } else if (sessions.isNotEmpty()) {
            sessions.map { it.averageWpm }.average().toInt()
        } else {
            uiState.wpm
        }
    }

    // Daily streak calculation
    val currentStreak = remember(sessions) {
        calculateDailyStreak(sessions)
    }

    // 2. Compute Last 7 Days Daily Activity Minutes for Compose Canvas Bar Chart
    val dailyActivityList = remember(sessions) {
        calculateLast7DaysActivity(sessions)
    }

    // 3. Current Session / Active Book Stats
    val activeBook: BookItem? = remember(savedDocuments, uiState.activeDocumentId) {
        val target = savedDocuments.firstOrNull { it.id == uiState.activeDocumentId }
            ?: savedDocuments.firstOrNull()
        target?.toBookItem()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_dashboard_screen"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Dashboard Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ANALYTICS & INSIGHTS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reading Performance",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (sessions.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { showResetDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset stats",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 1. High-Density Quick Metrics (4 Cards: Time, Words, WPM, Streak)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Reading Time",
                        value = readingTimeFormatted,
                        subtitle = "${sessions.size} sessions",
                        icon = Icons.Default.Timer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Words Read",
                        value = if (totalWordsRead >= 1000) String.format(Locale.US, "%,d", totalWordsRead) else "$totalWordsRead",
                        subtitle = "total processed",
                        icon = Icons.Default.MenuBook,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricCard(
                        title = "Average Speed",
                        value = "$averageWpm",
                        subtitle = "words / min",
                        icon = Icons.Default.Speed,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Daily Streak",
                        value = "$currentStreak ${if (currentStreak == 1) "Day" else "Days"}",
                        subtitle = if (currentStreak > 0) "Keep it going!" else "Read today to start",
                        icon = Icons.Default.LocalFireDepartment,
                        iconTint = Color(0xFFFF6D00),
                        containerColor = Color(0xFFFF6D00).copy(alpha = 0.12f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Daily Activity Graph (Compose Canvas Bar Chart for Last 7 Days)
        item {
            WeeklyActivityBarChart(
                activityData = dailyActivityList,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 3. Current Session / Active Book Breakdown Stats
        item {
            activeBook?.let { book ->
                ActiveBookStatsCard(
                    book = book,
                    currentWpm = uiState.wpm,
                    onResume = {
                        val doc = savedDocuments.firstOrNull { it.id == book.id }
                        if (doc != null) viewModel.resumeDocument(doc)
                    }
                )
            }
        }

        // 4. Recent Reading Sessions History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Sessions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${sessions.size} logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No reading sessions recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Complete an RSVP reading sprint to record duration and WPM metrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(sessions.take(10), key = { it.id }) { session ->
                SessionHistoryRow(session = session)
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Analytics History?") },
            text = { Text("This will clear all recorded reading sessions and recalculate metrics from document progress. Continue?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllReadingSessions()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * High-density Material 3 metric tile.
 */
@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = CircleShape,
                    color = containerColor,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Sleek Weekly/Daily Activity Bar Chart rendered directly onto Compose Canvas.
 * Shows daily reading minutes over the last 7 days with animated heights, rounded caps, and gridlines.
 */
@Composable
fun WeeklyActivityBarChart(
    activityData: List<DailyActivityData>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    val maxMinutes = remember(activityData) {
        (activityData.maxOfOrNull { it.minutes } ?: 15f).coerceAtLeast(15f)
    }

    val totalWeekMinutes = remember(activityData) {
        activityData.sumOf { it.minutes.toDouble() }.toInt()
    }

    // Animation progress 0f -> 1f for fluid bar growth
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(activityData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
        )
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Chart Title & Weekly Total Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Daily Reading Activity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Focus time in minutes (last 7 days)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${totalWeekMinutes}m this week",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Canvas Bar Chart
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    val barCount = activityData.size
                    if (barCount == 0) return@Canvas

                    // Draw 3 subtle horizontal guideline lines
                    val lineStep = canvasHeight / 3f
                    for (i in 1..2) {
                        val y = i * lineStep
                        drawLine(
                            color = outlineVariant,
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val slotWidth = canvasWidth / barCount
                    val barWidth = (slotWidth * 0.48f).coerceIn(16.dp.toPx(), 36.dp.toPx())

                    activityData.forEachIndexed { index, data ->
                        val centerX = (index * slotWidth) + (slotWidth / 2f)
                        val barHeightFraction = (data.minutes / maxMinutes).coerceIn(0.04f, 1f)
                        val animatedHeight = barHeightFraction * canvasHeight * animationProgress.value

                        val topY = canvasHeight - animatedHeight
                        val leftX = centerX - (barWidth / 2f)

                        val barColor = if (data.isToday) {
                            primaryColor
                        } else {
                            if (data.minutes > 0f) primaryContainer else outlineVariant.copy(alpha = 0.5f)
                        }

                        // Rounded bar rect
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(leftX, topY),
                            size = Size(barWidth, animatedHeight),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-Axis Day Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                activityData.forEach { data ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (data.minutes > 0) "${data.minutes.toInt()}m" else "-",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = if (data.isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (data.isToday) primaryColor else onSurfaceVariant
                        )
                        Text(
                            text = data.dayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (data.isToday) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (data.isToday) primaryColor else onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Current Session / Active Book Detailed Breakdown.
 * Shows percentage completed, estimated reading time remaining at current WPM, and words left.
 */
@Composable
fun ActiveBookStatsCard(
    book: BookItem,
    currentWpm: Int,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimatedMins = book.estimatedMinutesRemaining

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE READING SESSION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${(book.progressPercentage * 100).toInt()}% Done",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Book Cover Squircle Thumbnail (2:3 aspect ratio)
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    BookCoverVisual(
                        title = book.title,
                        author = book.displayAuthor,
                        sourceType = book.sourceType,
                        totalWords = book.totalWords,
                        coverImagePath = book.coverImagePath,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Breakdown details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${book.displayAuthor} · ${book.sourceType.uppercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress bar
                    LinearProgressIndicator(
                        progress = { book.progressPercentage },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        strokeCap = StrokeCap.Round,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Time and Words remaining
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (estimatedMins > 0) "~${estimatedMins} min left at ${currentWpm} WPM" else "Completed",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${book.wordsRemaining} words left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action button to resume
            Button(
                onClick = onResume,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resume Reading at ${currentWpm} WPM", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Row for individual recorded reading sessions.
 */
@Composable
fun SessionHistoryRow(
    session: ReadingSession,
    modifier: Modifier = Modifier
) {
    val dateFormatted = remember(session.timestamp) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(session.timestamp))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.documentTitle.ifBlank { "Speed Reading Sprint" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${session.averageWpm} WPM",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${session.wordsRead} words · ${session.durationSeconds}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Analytics & Streak Math Helpers ---

private fun calculateDailyStreak(sessions: List<ReadingSession>): Int {
    if (sessions.isEmpty()) return 0

    val dayTimestamps = sessions.map { session ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = session.timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }.distinct().sortedDescending()

    val todayMs = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val yesterdayMs = todayMs - 86400000L

    if (dayTimestamps.firstOrNull() != todayMs && dayTimestamps.firstOrNull() != yesterdayMs) {
        return 0
    }

    var streak = 0
    var expectedDay = dayTimestamps.first()
    for (day in dayTimestamps) {
        if (day == expectedDay) {
            streak++
            expectedDay -= 86400000L
        } else {
            break
        }
    }
    return streak
}

private fun calculateLast7DaysActivity(sessions: List<ReadingSession>): List<DailyActivityData> {
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val result = mutableListOf<DailyActivityData>()
    val cal = Calendar.getInstance()

    // 7 days ending today
    for (i in 6 downTo 0) {
        val targetDayCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -i)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStartMs = targetDayCal.timeInMillis
        val dayEndMs = dayStartMs + 86400000L

        val daySessions = sessions.filter { it.timestamp in dayStartMs until dayEndMs }
        val minutesRead = daySessions.sumOf { it.durationSeconds }.toFloat() / 60f

        result.add(
            DailyActivityData(
                dayLabel = if (i == 0) "Today" else dayFormat.format(targetDayCal.time),
                minutes = minutesRead,
                isToday = (i == 0)
            )
        )
    }

    return result
}
