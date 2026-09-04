package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.BookItem
import com.example.data.ReadingDocument
import com.example.data.toBookItem
import com.example.viewmodel.RsvpViewModel
import kotlinx.coroutines.launch
import java.io.File

enum class LibraryLayoutMode {
    GRID_2_COL,
    GRID_3_COL,
    LIST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryGridScreen(
    documents: List<ReadingDocument>,
    viewModel: RsvpViewModel,
    modifier: Modifier = Modifier,
    onOpenReader: (ReadingDocument) -> Unit = { viewModel.resumeDocument(it) }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var layoutMode by remember { mutableStateOf(LibraryLayoutMode.GRID_2_COL) }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedSort by remember { mutableStateOf("Recent") }

    // Cover picker bottom sheet state
    var documentForCoverChange by remember { mutableStateOf<ReadingDocument?>(null) }
    var showCoverPickerSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Document to delete confirmation
    var documentToDelete by remember { mutableStateOf<ReadingDocument?>(null) }

    // Photo picker launcher (PickVisualMedia contract for Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            documentForCoverChange?.let { doc ->
                viewModel.updateDocumentCover(doc.id, pickedUri, context)
            }
        }
        coroutineScope.launch {
            sheetState.hide()
            showCoverPickerSheet = false
            documentForCoverChange = null
        }
    }

    // Categories filter list
    val categories = listOf("All", "In Progress", "Favorites", "PDF", "EPUB", "Web", "Completed")

    // Filter & sort documents
    val bookItems = remember(documents) {
        documents.map { it.toBookItem() }
    }

    val filteredItems = remember(bookItems, selectedCategory, selectedSort) {
        val filtered = when (selectedCategory) {
            "In Progress" -> bookItems.filter { !it.isCompleted && it.currentWordIndex > 0 }
            "Favorites" -> bookItems.filter { it.isFavorite }
            "PDF" -> bookItems.filter { it.sourceType.equals("pdf", ignoreCase = true) }
            "EPUB" -> bookItems.filter { it.sourceType.equals("epub", ignoreCase = true) }
            "Web" -> bookItems.filter { it.sourceType.equals("web", ignoreCase = true) }
            "Completed" -> bookItems.filter { it.isCompleted }
            else -> bookItems
        }

        when (selectedSort) {
            "Title" -> filtered.sortedBy { it.title.lowercase() }
            "Progress" -> filtered.sortedByDescending { it.progressPercentage }
            "Words" -> filtered.sortedByDescending { it.totalWords }
            else -> filtered.sortedByDescending { it.lastReadTimestamp }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_grid_screen")
    ) {
        // Top App Bar / Library Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${filteredItems.size} ${if (filteredItems.size == 1) "book" else "books"} · Speed Reading Archive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Layout Toggle Button (2-col -> 3-col -> List)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clickable {
                        layoutMode = when (layoutMode) {
                            LibraryLayoutMode.GRID_2_COL -> LibraryLayoutMode.GRID_3_COL
                            LibraryLayoutMode.GRID_3_COL -> LibraryLayoutMode.LIST
                            LibraryLayoutMode.LIST -> LibraryLayoutMode.GRID_2_COL
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (layoutMode) {
                                LibraryLayoutMode.GRID_2_COL -> Icons.Default.GridView
                                LibraryLayoutMode.GRID_3_COL -> Icons.Default.ViewModule
                                LibraryLayoutMode.LIST -> Icons.Default.ViewAgenda
                            },
                            contentDescription = "Toggle Grid/List Columns",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (layoutMode) {
                                LibraryLayoutMode.GRID_2_COL -> "2-Col"
                                LibraryLayoutMode.GRID_3_COL -> "3-Col"
                                LibraryLayoutMode.LIST -> "List"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Sort selector
                var showSortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("library_sort_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort library",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        listOf("Recent", "Title", "Progress", "Words").forEach { sortOption ->
                            DropdownMenuItem(
                                text = { Text(sortOption) },
                                onClick = {
                                    selectedSort = sortOption
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("lib_chip_$cat")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main Library Content (2-col grid, 3-col grid, or List view)
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CollectionsBookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = if (documents.isEmpty()) "Your library is empty" else "No books found in $selectedCategory",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import a PDF, EPUB, or web article to view thumbnails and speed read effortlessly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            when (layoutMode) {
                LibraryLayoutMode.GRID_2_COL -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val doc = documents.firstOrNull { it.id == item.id } ?: return@items
                            BookCardTile(
                                item = item,
                                isThreeColumn = false,
                                onOpen = { onOpenReader(doc) },
                                onChangeCover = {
                                    documentForCoverChange = doc
                                    showCoverPickerSheet = true
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(doc.id, !doc.isFavorite) },
                                onResetProgress = { viewModel.resetProgress(doc.id) },
                                onDelete = { documentToDelete = doc }
                            )
                        }
                    }
                }
                LibraryLayoutMode.GRID_3_COL -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val doc = documents.firstOrNull { it.id == item.id } ?: return@items
                            BookCardTile(
                                item = item,
                                isThreeColumn = true,
                                onOpen = { onOpenReader(doc) },
                                onChangeCover = {
                                    documentForCoverChange = doc
                                    showCoverPickerSheet = true
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(doc.id, !doc.isFavorite) },
                                onResetProgress = { viewModel.resetProgress(doc.id) },
                                onDelete = { documentToDelete = doc }
                            )
                        }
                    }
                }
                LibraryLayoutMode.LIST -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            val doc = documents.firstOrNull { it.id == item.id } ?: return@items
                            BookListRow(
                                item = item,
                                onOpen = { onOpenReader(doc) },
                                onChangeCover = {
                                    documentForCoverChange = doc
                                    showCoverPickerSheet = true
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(doc.id, !doc.isFavorite) },
                                onDelete = { documentToDelete = doc }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Custom Cover Picker Bottom Sheet with 2:3 Ratio Guidance ---
    if (showCoverPickerSheet && documentForCoverChange != null) {
        val targetDoc = documentForCoverChange!!
        ModalBottomSheet(
            onDismissRequest = {
                showCoverPickerSheet = false
                documentForCoverChange = null
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle title
                Text(
                    text = "Change Book Cover",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = targetDoc.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Recommended Ratio Banner (Requirement 2)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Recommended: 2:3 ratio (e.g., 600×900 px)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Screenshots or photos are auto-cropped to 2:3 so covers never stretch or distort.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Current Cover Preview in standard 2:3 Squircle
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .shadow(6.dp, RoundedCornerShape(16.dp))
                ) {
                    BookCoverVisual(
                        title = targetDoc.title,
                        author = targetDoc.author,
                        sourceType = targetDoc.sourceType,
                        totalWords = targetDoc.totalWords,
                        coverImagePath = targetDoc.coverImagePath,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Choose from Gallery Button
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose Image from Gallery", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reset to Default Generated Cover (if custom exists)
                if (!targetDoc.coverImagePath.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.removeDocumentCover(targetDoc.id)
                            coroutineScope.launch {
                                sheetState.hide()
                                showCoverPickerSheet = false
                                documentForCoverChange = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Procedural Material 3 Cover")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // File Persistence reassurance note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saved into private app storage (filesDir). Stays intact even if moved or deleted from gallery.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // --- Delete Confirmation Dialog ---
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete from Library?") },
            text = { Text("Are you sure you want to delete \"${doc.title}\"? This removes the document and any cached thumbnails.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDocument(doc.id)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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

/**
 * 2-Column or 3-Column Book Card Tile.
 * Squircle cover image on top (2:3 aspect ratio), reading progress bar underneath,
 * and compact 2-line title / author header.
 */
@Composable
fun BookCardTile(
    item: BookItem,
    isThreeColumn: Boolean,
    onOpen: () -> Unit,
    onChangeCover: () -> Unit,
    onToggleFavorite: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("book_card_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Squircle Book Cover (Top portion with 2:3 ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                BookCoverVisual(
                    title = item.title,
                    author = item.displayAuthor,
                    sourceType = item.sourceType,
                    totalWords = item.totalWords,
                    coverImagePath = item.coverImagePath,
                    modifier = Modifier.fillMaxSize()
                )

                // Favorite Bookmark badge (top-left)
                if (item.isFavorite) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = "Favorite",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Source format pill (top-right)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = item.sourceType.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Progress Badge Overlaid at Bottom of Cover
                if (item.progressPercentage > 0f) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${(item.progressPercentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Progress Bar directly underneath cover
            LinearProgressIndicator(
                progress = { item.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = if (item.isCompleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            // Compact 2-line title/author header and overflow menu
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = item.title,
                        style = if (isThreeColumn) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = if (isThreeColumn) 14.sp else 18.sp,
                        modifier = Modifier.weight(1f)
                    )

                    // Context dropdown menu button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(24.dp)
                                .padding(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Resume Reading") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, null) },
                                onClick = {
                                    showMenu = false
                                    onOpen()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change Cover") },
                                leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, null) },
                                onClick = {
                                    showMenu = false
                                    onChangeCover()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (item.isFavorite) "Remove Favorite" else "Mark Favorite") },
                                leadingIcon = { Icon(if (item.isFavorite) Icons.Outlined.BookmarkBorder else Icons.Default.Bookmark, null) },
                                onClick = {
                                    showMenu = false
                                    onToggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Progress") },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, null) },
                                onClick = {
                                    showMenu = false
                                    onResetProgress()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Author / Words subtitle
                Text(
                    text = "${item.displayAuthor} · ${item.totalWords}w",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = if (isThreeColumn) 9.sp else 11.sp
                )
            }
        }
    }
}

/**
 * Compact horizontal row layout for the library List view mode.
 */
@Composable
fun BookListRow(
    item: BookItem,
    onOpen: () -> Unit,
    onChangeCover: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Squircle mini thumbnail
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                BookCoverVisual(
                    title = item.title,
                    author = item.displayAuthor,
                    sourceType = item.sourceType,
                    totalWords = item.totalWords,
                    coverImagePath = item.coverImagePath,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${item.displayAuthor} · ${item.totalWords} words",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(onClick = onChangeCover) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = "Change Cover",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Universal Book Cover visualizer.
 * If a custom/extracted thumbnail exists on disk, renders it with standard ContentScale.Crop.
 * If none exists, generates a clean Material 3 styled card with dynamic background color
 * and book title/author typography.
 */
@Composable
fun BookCoverVisual(
    title: String,
    author: String,
    sourceType: String,
    totalWords: Int,
    coverImagePath: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasValidFile = remember(coverImagePath) {
        coverImagePath?.let { path ->
            val file = File(path)
            file.exists() && file.length() > 0
        } ?: false
    }

    Box(modifier = modifier) {
        if (hasValidFile && coverImagePath != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(coverImagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Cover for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle bottom gradient shadow for legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                            startY = 60f
                        )
                    )
            )
        } else {
            // Procedural Material 3 Styled Book Cover with Dynamic Color and Typography
            val primaryColor = MaterialTheme.colorScheme.primaryContainer
            val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
            val tertiaryColor = MaterialTheme.colorScheme.tertiaryContainer
            val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHighest

            val gradientBrush = remember(title, sourceType, primaryColor, secondaryColor) {
                when (sourceType.lowercase()) {
                    "pdf" -> Brush.verticalGradient(listOf(Color(0xFF8B2500), Color(0xFF421000)))
                    "epub" -> Brush.verticalGradient(listOf(Color(0xFF1B4D3E), Color(0xFF0A2920)))
                    "web" -> Brush.verticalGradient(listOf(Color(0xFF0D47A1), Color(0xFF0A192F)))
                    "typed" -> Brush.verticalGradient(listOf(Color(0xFF4A148C), Color(0xFF1A0033)))
                    else -> {
                        val seed = title.hashCode()
                        val c1 = if (seed % 2 == 0) primaryColor else secondaryColor
                        val c2 = if (seed % 3 == 0) tertiaryColor else surfaceContainer
                        Brush.verticalGradient(listOf(c1, c2))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            ) {
                // Book Spine Left Subtle Gradient Shadow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 24f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top format icon badge
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.28f),
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val icon = when (sourceType.lowercase()) {
                                "pdf" -> Icons.Default.MenuBook
                                "epub" -> Icons.Default.AutoStories
                                "web" -> Icons.Default.Public
                                else -> Icons.Default.TextFields
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }

                    // Book Title & Author Typography
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 14.sp
                        )

                        if (author.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Word count badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = "${totalWords}w",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
