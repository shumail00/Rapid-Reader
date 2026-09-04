package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RsvpCanvasPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveStylePdfViewer(
    pdfFilePath: String,
    documentTitle: String,
    palette: RsvpCanvasPalette,
    onBack: () -> Unit,
    onStartRsvpFromPage: (Int) -> Unit,
    onSwitchToWordView: () -> Unit,
    onOpenChapters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Track current visible page
    val currentVisiblePage by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }

    // Initialize native PdfRenderer
    DisposableEffect(pdfFilePath) {
        val file = File(pdfFilePath)
        if (file.exists()) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = pfd
                val pdfRen = PdfRenderer(pfd)
                renderer = pdfRen
                pageCount = pdfRen.pageCount
            } catch (e: Exception) {
                errorMessage = "Unable to open PDF: ${e.localizedMessage}"
            }
        } else {
            errorMessage = "PDF file not found on device"
        }

        onDispose {
            renderer?.close()
            fileDescriptor?.close()
            pageBitmaps.values.forEach { it.recycle() }
            pageBitmaps.clear()
        }
    }

    // Function to render a page to bitmap
    fun loadPageBitmap(pageIndex: Int) {
        if (pageBitmaps.containsKey(pageIndex)) return
        val currentRen = renderer ?: return
        if (pageIndex !in 0 until pageCount) return

        coroutineScope.launch(Dispatchers.Default) {
            try {
                synchronized(currentRen) {
                    val page = currentRen.openPage(pageIndex)
                    // Render at crisp 2x resolution for high quality
                    val width = page.width * 2
                    val height = page.height * 2
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pageBitmaps[pageIndex] = bitmap
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Google Drive style Top Bar
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = documentTitle.ifBlank { "PDF Document" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Google Drive PDF Engine · Page ${currentVisiblePage + 1} of $pageCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.mutedTextColor.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textColor
                        )
                    }
                },
                actions = {
                    // Zoom Out
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                            if (zoomScale == 1f) {
                                panOffsetX = 0f
                                panOffsetY = 0f
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomOut,
                            contentDescription = "Zoom Out",
                            tint = palette.textColor
                        )
                    }

                    // Zoom In
                    IconButton(
                        onClick = {
                            zoomScale = (zoomScale + 0.25f).coerceAtMost(3f)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = "Zoom In",
                            tint = palette.textColor
                        )
                    }

                    // Switch to Word-by-Word Tap Mode
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .bouncyClick(scaleDown = 0.92f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwitchToWordView()
                            }
                            .testTag("switch_to_word_view_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Word Tap",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // TOC Chapters
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenChapters()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Toc,
                            contentDescription = "Table of Contents",
                            tint = palette.textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.backgroundColor
                )
            )

            // PDF Content Area
            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "Error rendering PDF",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else if (pageCount == 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularWavyProgressIndicator(
                        size = 56.dp,
                        waveCount = 6,
                        strokeWidth = 3.5.dp,
                        primaryColor = MaterialTheme.colorScheme.primary,
                        secondaryColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                if (zoomScale > 1f) {
                                    panOffsetX += pan.x
                                    panOffsetY += pan.y
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffsetX
                                translationY = panOffsetY
                            }
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(8.dp)) }

                        itemsIndexed((0 until pageCount).toList()) { index, pageIndex ->
                            LaunchedEffect(pageIndex) {
                                loadPageBitmap(pageIndex)
                                // Preload adjacent pages for smooth scrolling
                                loadPageBitmap(pageIndex + 1)
                                loadPageBitmap(pageIndex - 1)
                            }

                            val bmp = pageBitmaps[pageIndex]

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 6.dp,
                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pdf_page_$pageIndex")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Page ${pageIndex + 1}",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.707f) // Standard A4 aspect ratio
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularWavyProgressIndicator(
                                                size = 36.dp,
                                                waveCount = 6,
                                                strokeWidth = 2.5.dp,
                                                primaryColor = MaterialTheme.colorScheme.primary,
                                                secondaryColor = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }

                                    // Page Number Tag
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF1F3F4))
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "- Page ${pageIndex + 1} of $pageCount -",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(120.dp)) }
                    }
                }
            }
        }

        // Floating Bottom Action Pill (Google Drive PDF viewer style)
        if (pageCount > 0) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = palette.surfaceColor,
                tonalElevation = 10.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, palette.guideColor.copy(alpha = 0.3f)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Page Scrub Slider & Indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Page ${currentVisiblePage + 1} / $pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = palette.textColor
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (currentVisiblePage > 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(currentVisiblePage - 1)
                                        }
                                    }
                                },
                                enabled = currentVisiblePage > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Page",
                                    tint = palette.textColor
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (currentVisiblePage < pageCount - 1) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(currentVisiblePage + 1)
                                        }
                                    }
                                },
                                enabled = currentVisiblePage < pageCount - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Page",
                                    tint = palette.textColor
                                )
                            }
                        }
                    }

                    if (pageCount > 1) {
                        Slider(
                            value = (currentVisiblePage + 1).toFloat(),
                            onValueChange = { targetPage ->
                                val targetIndex = (targetPage.toInt() - 1).coerceIn(0, pageCount - 1)
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            },
                            valueRange = 1f..pageCount.toFloat(),
                            steps = (pageCount - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = palette.guideColor.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Primary Action: Start RSVP speed reading from current page
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onStartRsvpFromPage(currentVisiblePage)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("pdf_start_rsvp_button")
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
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start RSVP Speed from Page ${currentVisiblePage + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
