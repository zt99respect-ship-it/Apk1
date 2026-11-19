package com.livetranslator.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationHistoryDao {
  @Query("SELECT * FROM translation_history ORDER BY timestamp DESC")
  fun getAllFlow(): Flow<List<TranslationHistoryEntity>>

  @Query(
    "SELECT * FROM translation_history " +
      "WHERE sourceText LIKE '%' || :query || '%' " +
      "OR translatedText LIKE '%' || :query || '%' " +
      "OR sourceLang LIKE '%' || :query || '%' " +
      "ORDER BY timestamp DESC"
  )
  fun searchFlow(query: String): Flow<List<TranslationHistoryEntity>>

  @Query("SELECT * FROM translation_history ORDER BY timestamp DESC LIMIT 1")
  fun getLatestFlow(): Flow<TranslationHistoryEntity?>

  @Query("SELECT * FROM translation_history ORDER BY timestamp DESC")
  suspend fun getAllOnce(): List<TranslationHistoryEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entity: TranslationHistoryEntity): Long

  @Query("DELETE FROM translation_history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM translation_history")
  suspend fun deleteAll()

  @Query("DELETE FROM translation_history WHERE timestamp < :cutoffTimestamp")
  suspend fun deleteOlderThan(cutoffTimestamp: Long)
}

@Dao
interface TranslationSessionDao {
  @Query("SELECT * FROM translation_sessions ORDER BY startTimestamp DESC")
  fun getAllSessionsFlow(): Flow<List<TranslationSessionEntity>>

  @Query("SELECT * FROM translation_sessions ORDER BY startTimestamp DESC LIMIT 1")
  fun getLatestSessionFlow(): Flow<TranslationSessionEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertSession(session: TranslationSessionEntity): Long

  @Query(
    "UPDATE translation_sessions " +
      "SET endTimestamp = :endTimestamp, " +
      "durationSeconds = :durationSeconds, " +
      "translatedCount = :translatedCount, " +
      "sourceLang = :sourceLang " +
      "WHERE id = :id"
  )
  suspend fun updateSessionProgress(
    id: Long,
    endTimestamp: Long,
    durationSeconds: Long,
    translatedCount: Int,
    sourceLang: String,
  )

  @Query("DELETE FROM translation_sessions WHERE id = :id")
  suspend fun deleteSessionById(id: Long)

  @Query("DELETE FROM translation_sessions")
  suspend fun deleteAllSessions()
}
