package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ReadingDocument
import java.io.File

@Composable
fun BookCoverView(
    document: ReadingDocument,
    modifier: Modifier = Modifier,
    onUploadCoverClick: (() -> Unit)? = null
) {
    val hasCustomCover = remember(document.coverImagePath) {
        document.coverImagePath?.let { path ->
            val file = File(path)
            file.exists() && file.length() > 0
        } ?: false
    }

    val coverShape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .clip(coverShape)
            .shadow(4.dp, coverShape)
    ) {
        if (hasCustomCover && document.coverImagePath != null) {
            // Display User-uploaded custom thumbnail stored securely inside app
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(document.coverImagePath))
                    .crossfade(true)
                    .build(),
                contentDescription = "Cover for ${document.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Subtle dark gradient at bottom for legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 100f
                        )
                    )
            )
        } else {
            // Elegant Procedural Material 3 Book Cover
            val primaryColor = MaterialTheme.colorScheme.primaryContainer
            val onPrimary = MaterialTheme.colorScheme.onPrimaryContainer
            val secondaryColor = MaterialTheme.colorScheme.secondaryContainer
            val tertiaryColor = MaterialTheme.colorScheme.tertiaryContainer
            val surfaceHighest = MaterialTheme.colorScheme.surfaceContainerHighest
            val onTertiary = MaterialTheme.colorScheme.onTertiaryContainer

            // Deterministic gradient based on title
            val gradientBrush = remember(document.title, document.sourceType, primaryColor, secondaryColor, tertiaryColor, surfaceHighest) {
                val seed = document.title.hashCode()
                when (document.sourceType.lowercase()) {
                    "pdf" -> Brush.verticalGradient(listOf(Color(0xFF8B2500), Color(0xFF4A1000)))
                    "epub" -> Brush.verticalGradient(listOf(Color(0xFF1B4D3E), Color(0xFF0A2920)))
                    "web" -> Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF0D2D5E)))
                    "typed" -> Brush.verticalGradient(listOf(Color(0xFF5E35B1), Color(0xFF311B92)))
                    else -> {
                        val c1 = if (seed % 2 == 0) primaryColor else secondaryColor
                        val c2 = if (seed % 3 == 0) tertiaryColor else surfaceHighest
                        Brush.verticalGradient(listOf(c1, c2))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            ) {
                // Book Spine Left Shadow
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startX = 0f,
                                endX = 35f
                            )
                        )
                )

                // Book Content Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Source Icon pill
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.25f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val icon = when (document.sourceType.lowercase()) {
                                "pdf" -> Icons.Default.MenuBook
                                "epub" -> Icons.Default.AutoStories
                                "web" -> Icons.Default.Public
                                else -> Icons.Default.TextFields
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Document Title on Cover
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Word count badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.3f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${document.totalWords}w",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Upload/Change Cover Button Overlay if callback provided
        if (onUploadCoverClick != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clickable { onUploadCoverClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (hasCustomCover) Icons.Default.Image else Icons.Default.AddPhotoAlternate,
                        contentDescription = "Change Cover",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
