package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun DirectTextInputDialog(
    onDismiss: () -> Unit,
    onSubmitText: (title: String, text: String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var textContent by remember { mutableStateOf("") }

    val wordCount = remember(textContent) {
        textContent.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }.size
    }
    val estimatedMinutes = remember(wordCount) {
        val minutes = wordCount / 350.0
        if (minutes < 1.0) "${(minutes * 60).toInt()} sec" else "${String.format("%.1f", minutes)} min"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp)
                .testTag("direct_text_input_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Type or Paste Text",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (Optional)") },
                    placeholder = { Text("e.g., Morning Article or Chapter") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Action Bar (Paste Clipboard, Clear, Sample Templates)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipData = clipboard?.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val pasted = clipData.getItemAt(0).text?.toString() ?: ""
                                textContent = pasted
                                if (title.isBlank() && pasted.isNotBlank()) {
                                    val firstLine = pasted.lines().firstOrNull()?.take(30) ?: "Pasted Text"
                                    title = firstLine
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("paste_clipboard_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste")
                    }

                    if (textContent.isNotBlank()) {
                        OutlinedButton(
                            onClick = { textContent = ""; title = "" },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-line Text Area
                OutlinedTextField(
                    value = textContent,
                    onValueChange = { textContent = it },
                    label = { Text("Content to Read") },
                    placeholder = { Text("Paste articles, essays, notes, or study material here…") },
                    minLines = 8,
                    maxLines = 14,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_content_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Telemetry footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$wordCount words • ~$estimatedMinutes @ 350 WPM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (textContent.isNotBlank()) {
                                onSubmitText(title, textContent)
                            }
                        },
                        enabled = textContent.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("start_reading_direct_button")
                    ) {
                        Text("Start RSVP Reading")
                    }
                }
            }
        }
    }
}
