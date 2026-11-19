package com.livetranslator.app.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val timestamp: Long,
  val sourceText: String,
  val translatedText: String,
  val sourceLang: String,
  val targetLang: String = "ar",
  val sessionId: Long? = null,
)

@Entity(tableName = "translation_sessions")
data class TranslationSessionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val startTimestamp: Long,
  val endTimestamp: Long,
  val durationSeconds: Long,
  val sourceLang: String,
  val translatedCount: Int = 0,
  val audioMode: String = "صوت الهاتف",
)
