package com.livetranslator.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.data.local.db.TranslationSessionEntity
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.OverlayFontWeight
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.data.model.SpeechEnginePreference
import com.livetranslator.app.data.model.TranslationMode
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.data.repository.TranslationRepository
import com.livetranslator.app.engine.DownloadHelper
import com.livetranslator.app.engine.OfflineModelsState
import com.livetranslator.app.engine.OtaCenterState
import com.livetranslator.app.engine.OtaUpdateManager
import com.livetranslator.app.engine.TranslationSessionManager
import com.livetranslator.app.engine.TranslationSessionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
  private val repository: TranslationRepository,
  val sessionManager: TranslationSessionManager,
  private val downloadHelper: DownloadHelper,
  private val otaUpdateManager: OtaUpdateManager,
) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  val sessionState: StateFlow<TranslationSessionState> = sessionManager.state

  val modelsState: StateFlow<OfflineModelsState> = downloadHelper.state

  val otaState: StateFlow<OtaCenterState> = otaUpdateManager.state

  val latestTranslation: StateFlow<TranslationHistoryEntity?> =
    repository.latestTranslationFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null,
    )

  val latestSession: StateFlow<TranslationSessionEntity?> =
    repository.latestSessionFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null,
    )

  fun completeOnboarding() {
    viewModelScope.launch { repository.setOnboardingCompleted(true) }
  }

  fun setTranslationMode(mode: TranslationMode) {
    viewModelScope.launch { repository.setTranslationMode(mode) }
  }

  fun prepareModels(sourceLanguageCode: String) {
    viewModelScope.launch {
      downloadHelper.ensureAllModelsReady(sourceLanguageCode)
    }
  }
}

class OverlayStyleViewModel(private val repository: TranslationRepository) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  fun applyPreset(preset: OverlayStylePreset) {
    viewModelScope.launch { repository.applyStylePreset(preset) }
  }

  fun saveFullStyle(
    preset: OverlayStylePreset,
    font: ArabicFontOption,
    weight: OverlayFontWeight,
    fontSizeSp: Float,
    textColorArgb: Long,
    boxColorArgb: Long,
    boxOpacity: Float,
    position: OverlayPosition,
    cornerRadiusDp: Float,
  ) {
    viewModelScope.launch {
      repository.saveFullOverlayStyle(
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
    }
  }

  fun resetToOriginal() {
    viewModelScope.launch { repository.resetStyleToDefaults() }
  }

  fun setPosition(position: OverlayPosition) {
    viewModelScope.launch { repository.setOverlayPosition(position) }
  }

  fun setCornerRadiusDp(radiusDp: Float) {
    viewModelScope.launch { repository.setOverlayCornerRadiusDp(radiusDp) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(private val repository: TranslationRepository) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  val sessions: StateFlow<List<TranslationSessionEntity>> =
    repository.allSessionsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList(),
    )

  val historyItems: StateFlow<List<TranslationHistoryEntity>> =
    _searchQuery
      .flatMapLatest { query -> repository.observeHistory(query) }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
      )

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun deleteSession(sessionId: Long) {
    viewModelScope.launch { repository.deleteSession(sessionId) }
  }

  fun deleteHistoryItem(id: Long) {
    viewModelScope.launch { repository.deleteHistoryItem(id) }
  }

  fun clearAll() {
    viewModelScope.launch { repository.clearAllSessionsAndHistory() }
  }

  suspend fun buildExportText(): String = repository.buildExportTxt()
}

class DevicesViewModel(
  private val repository: TranslationRepository,
  val sessionManager: TranslationSessionManager,
  private val downloadHelper: DownloadHelper,
) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  val sessionState: StateFlow<TranslationSessionState> = sessionManager.state

  val modelsState: StateFlow<OfflineModelsState> = downloadHelper.state

  fun setTranslationMode(mode: TranslationMode) {
    viewModelScope.launch { repository.setTranslationMode(mode) }
  }

  fun setAudioSourcePreference(preference: AudioSourcePreference) {
    viewModelScope.launch { repository.setAudioSourcePreference(preference) }
  }

  fun setSpeechEnginePreference(preference: SpeechEnginePreference) {
    viewModelScope.launch { repository.setSpeechEnginePreference(preference) }
  }

  fun setSourceLanguageCode(code: String) {
    viewModelScope.launch {
      repository.setSourceLanguageCode(code)
      downloadHelper.refreshModelStatus(code)
    }
  }

  fun setAudioChunkSeconds(seconds: Int) {
    viewModelScope.launch { repository.setAudioChunkSeconds(seconds) }
  }

  fun downloadModelsFor(code: String) {
    viewModelScope.launch { downloadHelper.ensureAllModelsReady(code) }
  }

  fun deleteVoskModel(code: String) {
    viewModelScope.launch { downloadHelper.deleteVoskModel(code) }
  }
}

class AccountViewModel(
  private val repository: TranslationRepository,
  private val downloadHelper: DownloadHelper,
  private val otaUpdateManager: OtaUpdateManager,
) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  val latestSession: StateFlow<TranslationSessionEntity?> =
    repository.latestSessionFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null,
    )

  val modelsState: StateFlow<OfflineModelsState> = downloadHelper.state

  val otaState: StateFlow<OtaCenterState> = otaUpdateManager.state

  fun setAccountLabel(label: String) {
    viewModelScope.launch { repository.setAccountLabel(label) }
  }

  fun setSkipArabic(skip: Boolean) {
    viewModelScope.launch { repository.setSkipArabic(skip) }
  }

  fun setAutoRestartOnBoot(enabled: Boolean) {
    viewModelScope.launch { repository.setAutoRestartOnBoot(enabled) }
  }

  fun setAudioChunkSeconds(seconds: Int) {
    viewModelScope.launch { repository.setAudioChunkSeconds(seconds) }
  }

  fun setOverlayPosition(position: OverlayPosition) {
    viewModelScope.launch { repository.setOverlayPosition(position) }
  }

  fun clearAllSessionsAndHistory() {
    viewModelScope.launch { repository.clearAllSessionsAndHistory() }
  }

  fun resetAllSettings() {
    viewModelScope.launch { repository.resetAllSettings() }
  }
}

class UpdatesCenterViewModel(
  private val app: LiveTranslatorApp,
  private val repository: TranslationRepository,
  private val otaUpdateManager: OtaUpdateManager,
) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  val otaState: StateFlow<OtaCenterState> = otaUpdateManager.state

  fun checkUpdatesNow(feedUrl: String) {
    viewModelScope.launch {
      val clean = feedUrl.trim()
      repository.setOtaFeedUrl(clean)
      otaUpdateManager.checkFeedForUpdates(clean, notifyUser = true)
    }
  }

  fun applyPendingPatch() {
    viewModelScope.launch {
      otaUpdateManager.applyAvailableManifest { appliedVersion ->
        app.auth.currentUser?.uid?.let { uid ->
          app.cloudSyncRepository.syncAppliedPatchVersion(uid, appliedVersion)
        }
      }
    }
  }

  fun importLocalPatch(uri: Uri) {
    viewModelScope.launch { otaUpdateManager.importLocalDeltaPatch(uri) }
  }

  fun sendCustomNotification(title: String, body: String, isUpdateAlert: Boolean) {
    viewModelScope.launch {
      otaUpdateManager.sendLocalNotification(
        title = title,
        body = body,
        isUpdateAlert = isUpdateAlert,
      )
    }
  }
}

class LiveTranslatorViewModelFactory(private val app: LiveTranslatorApp) :
  ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    when {
      modelClass.isAssignableFrom(MainViewModel::class.java) ->
        MainViewModel(app.repository, app.sessionManager, app.downloadHelper, app.otaUpdateManager) as T
      modelClass.isAssignableFrom(OverlayStyleViewModel::class.java) ->
        OverlayStyleViewModel(app.repository) as T
      modelClass.isAssignableFrom(SummaryViewModel::class.java) ->
        SummaryViewModel(app.repository) as T
      modelClass.isAssignableFrom(DevicesViewModel::class.java) ->
        DevicesViewModel(app.repository, app.sessionManager, app.downloadHelper) as T
      modelClass.isAssignableFrom(AccountViewModel::class.java) ->
        AccountViewModel(app.repository, app.downloadHelper, app.otaUpdateManager) as T
      modelClass.isAssignableFrom(UpdatesCenterViewModel::class.java) ->
        UpdatesCenterViewModel(app, app.repository, app.otaUpdateManager) as T
      else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
