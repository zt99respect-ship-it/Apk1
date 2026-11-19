package com.livetranslator.app

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.livetranslator.app.data.local.db.LiveTranslatorDatabase
import com.livetranslator.app.data.local.prefs.SettingsDataStore
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.remote.CloudSyncRepository
import com.livetranslator.app.data.repository.TranslationRepository
import com.livetranslator.app.engine.DownloadHelper
import com.livetranslator.app.engine.HotfixBackgroundWorker
import com.livetranslator.app.engine.MlKitTranslationEngine
import com.livetranslator.app.engine.OtaUpdateManager
import com.livetranslator.app.engine.ScreenCaptureOcrEngine
import com.livetranslator.app.engine.TranslationSessionManager
import com.livetranslator.app.engine.VoskSpeechEngine
import com.livetranslator.app.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LiveTranslatorApp : Application() {

  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val auth: FirebaseAuth by lazy { Firebase.auth }

  val firestore: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
  }

  val database: LiveTranslatorDatabase by lazy { LiveTranslatorDatabase.getInstance(this) }

  val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

  val repository: TranslationRepository by lazy {
    TranslationRepository(
      historyDao = database.translationHistoryDao(),
      sessionDao = database.translationSessionDao(),
      settingsDataStore = settingsDataStore,
    )
  }

  val cloudSyncRepository: CloudSyncRepository by lazy {
    CloudSyncRepository(firestore = firestore, auth = auth)
  }

  val remoteControlConfig =
    kotlinx.coroutines.flow.MutableStateFlow(
      com.livetranslator.app.data.remote.AppControlConfigDoc()
    )

  val downloadHelper: DownloadHelper by lazy { DownloadHelper(this) }

  val voskEngine: VoskSpeechEngine by lazy { VoskSpeechEngine() }

  val mlKitEngine: MlKitTranslationEngine by lazy { MlKitTranslationEngine() }

  val screenOcrEngine: ScreenCaptureOcrEngine by lazy { ScreenCaptureOcrEngine(this) }

  val otaUpdateManager: OtaUpdateManager by lazy {
    OtaUpdateManager(this, repository, mlKitEngine)
  }

  val sessionManager: TranslationSessionManager by lazy { TranslationSessionManager() }

  override fun onCreate() {
    super.onCreate()
    NotificationHelper.ensureChannel(this)
    HotfixBackgroundWorker.schedulePeriodic(this)
    appScope.launch {
      runCatching {
        repository.purgeExpiredHistory()
        otaUpdateManager.initializeFromDisk()
        val settings = repository.getCurrentSettings()
        val targetLang = SupportedLanguages.resolveVoskLanguage(settings.sourceLanguageCode).code
        downloadHelper.refreshModelStatus(targetLang)
        if (settings.otaFeedUrl.isNotBlank()) {
          otaUpdateManager.checkFeedForUpdates(settings.otaFeedUrl, notifyUser = true)
        }
      }
    }
  }
}
