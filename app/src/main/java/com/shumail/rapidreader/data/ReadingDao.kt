package com.shumail.rapidreader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingDao {
    @Query("SELECT * FROM reading_documents ORDER BY lastReadTimestamp DESC")
    fun getAllDocuments(): Flow<List<ReadingDocument>>

    @Query("SELECT * FROM reading_documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): ReadingDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: ReadingDocument): Long

    @Update
    suspend fun updateDocument(document: ReadingDocument)

    @Query("UPDATE reading_documents SET currentWordIndex = :index, lastReadTimestamp = :timestamp, isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateProgress(id: Long, index: Int, timestamp: Long, isCompleted: Boolean)

    @Query("UPDATE reading_documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE reading_documents SET coverImagePath = :coverPath WHERE id = :id")
    suspend fun updateCoverImage(id: Long, coverPath: String?)

    @Delete
    suspend fun deleteDocument(document: ReadingDocument)

    @Query("DELETE FROM reading_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("DELETE FROM reading_documents")
    suspend fun clearAllDocuments()

    // Sessions & Progress Tracking
    @Query("SELECT * FROM reading_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ReadingSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSession): Long

    @Query("DELETE FROM reading_sessions")
    suspend fun clearAllSessions()
}

