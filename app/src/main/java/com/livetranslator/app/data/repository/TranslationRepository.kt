package com.livetranslator.app.data.repository

import com.livetranslator.app.data.local.db.TranslationHistoryDao
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.data.local.prefs.SettingsDataStore
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.UserSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TranslationRepository(
  private val historyDao: TranslationHistoryDao,
  private val settingsDataStore: SettingsDataStore,
) {
  val settingsFlow: Flow<UserSettings> = settingsDataStore.settingsFlow

  val latestTranslationFlow: Flow<TranslationHistoryEntity?> = historyDao.getLatestFlow()

  fun observeHistory(query: String): Flow<List<TranslationHistoryEntity>> =
    if (query.isBlank()) {
      historyDao.getAllFlow()
    } else {
      historyDao.searchFlow(query.trim())
    }

  suspend fun getCurrentSettings(): UserSettings = settingsDataStore.settingsFlow.first()

  suspend fun saveTranslation(
    sourceText: String,
    translatedText: String,
    sourceLang: String,
    targetLang: String = "ar",
    timestamp: Long = System.currentTimeMillis(),
  ) {
    if (translatedText.isBlank()) return
    historyDao.insert(
      TranslationHistoryEntity(
        timestamp = timestamp,
        sourceText = sourceText.trim(),
        translatedText = translatedText.trim(),
        sourceLang = sourceLang,
        targetLang = targetLang,
      )
    )
  }

  suspend fun deleteHistoryItem(id: Long) {
    historyDao.deleteById(id)
  }

  suspend fun clearHistory() {
    historyDao.deleteAll()
  }

  suspend fun purgeExpiredHistory(nowMs: Long = System.currentTimeMillis()) {
    val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
    historyDao.deleteOlderThan(nowMs - thirtyDaysMs)
  }

  suspend fun buildExportTxt(): String {
    val entries = historyDao.getAllOnce()
    if (entries.isEmpty()) {
      return "سجل ترجمات LiveTranslator\nلا توجد ترجمات محفوظة حالياً.\n"
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
      appendLine("========================================")
      appendLine("LiveTranslator — سجل الترجمة الفورية (بدون إنترنت)")
      appendLine("تاريخ التصدير: ${formatter.format(Date())}")
      appendLine("عدد السجلات: ${entries.size}")
      appendLine("========================================")
      appendLine()
      entries.forEach { item ->
        appendLine("[${formatter.format(Date(item.timestamp))}] (${item.sourceLang} -> ${item.targetLang})")
        if (item.sourceText.isNotBlank()) {
          appendLine("النص: ${item.sourceText}")
        }
        appendLine("العربية: ${item.translatedText}")
        appendLine("----------------------------------------")
      }
    }
  }

  suspend fun setOnboardingCompleted(completed: Boolean) =
    settingsDataStore.setOnboardingCompleted(completed)

  suspend fun setSourceLanguageCode(code: String) = settingsDataStore.setSourceLanguageCode(code)

  suspend fun setSkipArabic(skip: Boolean) = settingsDataStore.setSkipArabic(skip)

  suspend fun setAudioChunkSeconds(seconds: Int) = settingsDataStore.setAudioChunkSeconds(seconds)

  suspend fun setAutoRestartOnBoot(enabled: Boolean) =
    settingsDataStore.setAutoRestartOnBoot(enabled)

  suspend fun setOverlayPosition(position: OverlayPosition) =
    settingsDataStore.setOverlayPosition(position)

  suspend fun setOverlayBackground(background: OverlayBackground) =
    settingsDataStore.setOverlayBackground(background)

  suspend fun setOverlayFontSizeSp(sizeSp: Float) =
    settingsDataStore.setOverlayFontSizeSp(sizeSp)

  suspend fun setOverlayTextColorArgb(argb: Long) =
    settingsDataStore.setOverlayTextColorArgb(argb)

  suspend fun setOverlayFont(font: ArabicFontOption) = settingsDataStore.setOverlayFont(font)

  suspend fun setOverlayCornerRadiusDp(radiusDp: Float) =
    settingsDataStore.setOverlayCornerRadiusDp(radiusDp)
}
