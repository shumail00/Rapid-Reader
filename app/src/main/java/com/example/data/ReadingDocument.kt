package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_documents")
data class ReadingDocument(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val sourceType: String, // "txt", "pdf", "epub", "web", "typed", "preset"
    val currentWordIndex: Int = 0,
    val totalWords: Int = 0,
    val preferredWpm: Int = 350,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val coverImagePath: String? = null,
    val localFilePath: String? = null,
    val webUrl: String? = null,
    val author: String = ""
) {
    val progressPercentage: Float
        get() = if (totalWords > 0) (currentWordIndex.toFloat() / totalWords.toFloat()).coerceIn(0f, 1f) else 0f
}

