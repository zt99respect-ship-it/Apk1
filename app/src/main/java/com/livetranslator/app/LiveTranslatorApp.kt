package com.livetranslator.app

import android.app.Application
import com.livetranslator.app.data.local.db.LiveTranslatorDatabase
import com.livetranslator.app.data.local.prefs.SettingsDataStore
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.repository.TranslationRepository
import com.livetranslator.app.engine.DownloadHelper
import com.livetranslator.app.engine.MlKitTranslationEngine
import com.livetranslator.app.engine.TranslationSessionManager
import com.livetranslator.app.engine.VoskSpeechEngine
import com.livetranslator.app.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiveTranslatorApp : Application() {

  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val database: LiveTranslatorDatabase by lazy { LiveTranslatorDatabase.getInstance(this) }

  val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

  val repository: TranslationRepository by lazy {
    TranslationRepository(
      historyDao = database.translationHistoryDao(),
      sessionDao = database.translationSessionDao(),
      settingsDataStore = settingsDataStore,
    )
  }

  val downloadHelper: DownloadHelper by lazy { DownloadHelper(this) }

  val voskEngine: VoskSpeechEngine by lazy { VoskSpeechEngine() }

  val mlKitEngine: MlKitTranslationEngine by lazy { MlKitTranslationEngine() }

  val sessionManager: TranslationSessionManager by lazy { TranslationSessionManager() }

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.ensureChannel(this)
    appScope.launch {
      runCatching {
        repository.purgeExpiredHistory()
        val settings = repository.getCurrentSettings()
        val targetLang = SupportedLanguages.resolveVoskLanguage(settings.sourceLanguageCode).code
        downloadHelper.refreshModelStatus(targetLang)
      }
    }
  }
}
