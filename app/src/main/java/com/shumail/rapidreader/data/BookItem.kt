package com.shumail.rapidreader.data

/**
 * Lightweight, presentation-ready domain model for book/document items in the library grid.
 * Provides computed helpers for remaining words, reading time, and formatted display.
 */
data class BookItem(
    val id: Long,
    val title: String,
    val author: String = "",
    val sourceType: String,
    val currentWordIndex: Int = 0,
    val totalWords: Int = 0,
    val progressPercentage: Float = 0f,
    val coverImagePath: String? = null,
    val localFilePath: String? = null,
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val preferredWpm: Int = 350
) {
    val wordsRemaining: Int
        get() = (totalWords - currentWordIndex).coerceAtLeast(0)

    val estimatedMinutesRemaining: Int
        get() {
            val speed = if (preferredWpm > 0) preferredWpm else 350
            return (wordsRemaining / speed).coerceAtLeast(if (wordsRemaining > 0) 1 else 0)
        }

    val displayAuthor: String
        get() = if (author.isNotBlank()) {
            author
        } else {
            when (sourceType.lowercase()) {
                "pdf" -> "PDF Document"
                "epub" -> "eBook Edition"
                "web" -> "Web Article"
                "preset" -> "Curated Drill"
                else -> "Personal Text"
            }
        }
}

/**
 * Maps a Room ReadingDocument entity to a presentation BookItem.
 */
fun ReadingDocument.toBookItem(): BookItem {
    return BookItem(
        id = id,
        title = title,
        author = if (author.isNotBlank()) author else "",
        sourceType = sourceType,
        currentWordIndex = currentWordIndex,
        totalWords = totalWords,
        progressPercentage = progressPercentage,
        coverImagePath = coverImagePath,
        localFilePath = localFilePath,
        isCompleted = isCompleted,
        isFavorite = isFavorite,
        lastReadTimestamp = lastReadTimestamp,
        preferredWpm = preferredWpm
    )
}
