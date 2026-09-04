package com.shumail.rapidreader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shumail.rapidreader.data.ReadingDocument
import com.shumail.rapidreader.viewmodel.RsvpViewModel

/**
 * Backward-compatible facade delegating to the modular LibraryGridScreen.
 */
@Composable
fun LibraryView(
    documents: List<ReadingDocument>,
    viewModel: RsvpViewModel,
    modifier: Modifier = Modifier
) {
    LibraryGridScreen(
        documents = documents,
        viewModel = viewModel,
        modifier = modifier
    )
}
