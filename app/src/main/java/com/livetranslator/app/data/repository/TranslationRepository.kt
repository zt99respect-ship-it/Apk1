package com.livetranslator.app.data.repository

import com.livetranslator.app.data.local.db.TranslationHistoryDao
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.data.local.db.TranslationSessionDao
import com.livetranslator.app.data.local.db.TranslationSessionEntity
import com.livetranslator.app.data.local.prefs.SettingsDataStore
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayFontWeight
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.data.model.SpeechEnginePreference
import com.livetranslator.app.data.model.UserSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TranslationRepository(
  private val historyDao: TranslationHistoryDao,
  private val sessionDao: TranslationSessionDao,
  private val settingsDataStore: SettingsDataStore,
) {
  val settingsFlow: Flow<UserSettings> = settingsDataStore.settingsFlow

  val latestTranslationFlow: Flow<TranslationHistoryEntity?> = historyDao.getLatestFlow()

  val allSessionsFlow: Flow<List<TranslationSessionEntity>> = sessionDao.getAllSessionsFlow()

  val latestSessionFlow: Flow<TranslationSessionEntity?> = sessionDao.getLatestSessionFlow()

  fun observeHistory(query: String): Flow<List<TranslationHistoryEntity>> =
    if (query.isBlank()) {
      historyDao.getAllFlow()
    } else {
      historyDao.searchFlow(query.trim())
    }

  suspend fun getCurrentSettings(): UserSettings = settingsDataStore.settingsFlow.first()

  suspend fun createSession(
    startTimestamp: Long,
    sourceLang: String,
    audioMode: String,
  ): Long =
    sessionDao.insertSession(
      TranslationSessionEntity(
        startTimestamp = startTimestamp,
        endTimestamp = startTimestamp,
        durationSeconds = 0L,
        sourceLang = sourceLang,
        translatedCount = 0,
        audioMode = audioMode,
      )
    )

  suspend fun updateSessionProgress(
    sessionId: Long,
    endTimestamp: Long,
    durationSeconds: Long,
    translatedCount: Int,
    sourceLang: String,
  ) {
    sessionDao.updateSessionProgress(
      id = sessionId,
      endTimestamp = endTimestamp,
      durationSeconds = durationSeconds.coerceAtLeast(0L),
      translatedCount = translatedCount.coerceAtLeast(0),
      sourceLang = sourceLang,
    )
  }

  suspend fun deleteSession(sessionId: Long) {
    sessionDao.deleteSessionById(sessionId)
  }

  suspend fun clearAllSessionsAndHistory() {
    sessionDao.deleteAllSessions()
    historyDao.deleteAll()
  }

  suspend fun saveTranslation(
    sourceText: String,
    translatedText: String,
    sourceLang: String,
    targetLang: String = "ar",
    timestamp: Long = System.currentTimeMillis(),
    sessionId: Long? = null,
  ) {
    if (translatedText.isBlank()) return
    historyDao.insert(
      TranslationHistoryEntity(
        timestamp = timestamp,
        sourceText = sourceText.trim(),
        translatedText = translatedText.trim(),
        sourceLang = sourceLang,
        targetLang = targetLang,
        sessionId = sessionId,
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
      appendLine("LiveTranslator — سجل الترجمة الفورية للصوت")
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

  suspend fun setAccountLabel(label: String) = settingsDataStore.setAccountLabel(label)

  suspend fun setSourceLanguageCode(code: String) = settingsDataStore.setSourceLanguageCode(code)

  suspend fun setSkipArabic(skip: Boolean) = settingsDataStore.setSkipArabic(skip)

  suspend fun setAudioChunkSeconds(seconds: Int) = settingsDataStore.setAudioChunkSeconds(seconds)

  suspend fun setAutoRestartOnBoot(enabled: Boolean) =
    settingsDataStore.setAutoRestartOnBoot(enabled)

  suspend fun setAudioSourcePreference(preference: AudioSourcePreference) =
    settingsDataStore.setAudioSourcePreference(preference)

  suspend fun setSpeechEnginePreference(preference: SpeechEnginePreference) =
    settingsDataStore.setSpeechEnginePreference(preference)

  suspend fun setOverlayPosition(position: OverlayPosition) =
    settingsDataStore.setOverlayPosition(position)

  suspend fun setOverlayBackground(background: OverlayBackground) =
    settingsDataStore.setOverlayBackground(background)

  suspend fun saveFullOverlayStyle(
    preset: OverlayStylePreset,
    font: ArabicFontOption,
    weight: OverlayFontWeight,
    fontSizeSp: Float,
    textColorArgb: Long,
    boxColorArgb: Long,
    boxOpacity: Float,
    position: OverlayPosition,
    cornerRadiusDp: Float,
  ) =
    settingsDataStore.saveFullOverlayStyle(
      preset = preset,
      font = font,
      weight = weight,
      fontSizeSp = fontSizeSp,
      textColorArgb = textColorArgb,
      boxColorArgb = boxColorArgb,
      boxOpacity = boxOpacity,
      position = position,
      cornerRadiusDp = cornerRadiusDp,
    )

  suspend fun applyStylePreset(preset: OverlayStylePreset) =
    settingsDataStore.applyStylePreset(preset)

  suspend fun setOverlayFontSizeSp(sizeSp: Float) =
    settingsDataStore.setOverlayFontSizeSp(sizeSp)

  suspend fun setOverlayTextColorArgb(argb: Long) =
    settingsDataStore.setOverlayTextColorArgb(argb)

  suspend fun setOverlayBoxColorArgb(argb: Long) =
    settingsDataStore.setOverlayBoxColorArgb(argb)

  suspend fun setOverlayBoxOpacity(opacity: Float) =
    settingsDataStore.setOverlayBoxOpacity(opacity)

  suspend fun setOverlayFont(font: ArabicFontOption) = settingsDataStore.setOverlayFont(font)

  suspend fun setOverlayFontWeight(weight: OverlayFontWeight) =
    settingsDataStore.setOverlayFontWeight(weight)

  suspend fun setOverlayCornerRadiusDp(radiusDp: Float) =
    settingsDataStore.setOverlayCornerRadiusDp(radiusDp)

  suspend fun setOverlayCustomPosition(x: Int, y: Int) =
    settingsDataStore.setOverlayCustomPosition(x, y)

  suspend fun resetOverlayCustomPosition() = settingsDataStore.resetOverlayCustomPosition()

  suspend fun resetStyleToDefaults() = settingsDataStore.resetStyleToDefaults()

  suspend fun resetAllSettings() = settingsDataStore.resetAllSettings()
}
