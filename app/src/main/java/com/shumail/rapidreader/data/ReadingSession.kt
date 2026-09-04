package com.shumail.rapidreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_sessions")
data class ReadingSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long = 0,
    val documentTitle: String = "",
    val sourceType: String = "typed",
    val wordsRead: Int = 0,
    val durationSeconds: Long = 0,
    val averageWpm: Int = 350,
    val timestamp: Long = System.currentTimeMillis()
)
