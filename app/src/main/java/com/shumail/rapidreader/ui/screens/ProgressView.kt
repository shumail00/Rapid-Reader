package com.shumail.rapidreader.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shumail.rapidreader.data.ReadingDocument
import com.shumail.rapidreader.viewmodel.RsvpViewModel

/**
 * Backward-compatible facade delegating to the modular AnalyticsDashboardScreen.
 */
@Composable
fun ProgressView(
    viewModel: RsvpViewModel,
    savedDocuments: List<ReadingDocument>,
    onStartNewReadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnalyticsDashboardScreen(
        viewModel = viewModel,
        savedDocuments = savedDocuments,
        onStartNewReadClick = onStartNewReadClick,
        modifier = modifier
    )
}
