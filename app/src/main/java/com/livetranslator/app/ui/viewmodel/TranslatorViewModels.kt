package com.livetranslator.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.data.repository.TranslationRepository
import com.livetranslator.app.engine.DownloadHelper
import com.livetranslator.app.engine.OfflineModelsState
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
) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  val sessionState: StateFlow<TranslationSessionState> = sessionManager.state

  val modelsState: StateFlow<OfflineModelsState> = downloadHelper.state

  val latestTranslation: StateFlow<TranslationHistoryEntity?> =
    repository.latestTranslationFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = null,
    )

  fun completeOnboarding() {
    viewModelScope.launch { repository.setOnboardingCompleted(true) }
  }

  fun prepareModels(sourceLanguageCode: String) {
    viewModelScope.launch {
      downloadHelper.ensureAllModelsReady(sourceLanguageCode)
    }
  }
}

class OverlaySettingsViewModel(private val repository: TranslationRepository) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  fun setPosition(position: OverlayPosition) {
    viewModelScope.launch { repository.setOverlayPosition(position) }
  }

  fun setBackground(background: OverlayBackground) {
    viewModelScope.launch { repository.setOverlayBackground(background) }
  }

  fun setFontSizeSp(sizeSp: Float) {
    viewModelScope.launch { repository.setOverlayFontSizeSp(sizeSp) }
  }

  fun setTextColorArgb(argb: Long) {
    viewModelScope.launch { repository.setOverlayTextColorArgb(argb) }
  }

  fun setFont(font: ArabicFontOption) {
    viewModelScope.launch { repository.setOverlayFont(font) }
  }

  fun setCornerRadiusDp(radiusDp: Float) {
    viewModelScope.launch { repository.setOverlayCornerRadiusDp(radiusDp) }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: TranslationRepository) : ViewModel() {

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

  fun deleteItem(id: Long) {
    viewModelScope.launch { repository.deleteHistoryItem(id) }
  }

  fun clearAll() {
    viewModelScope.launch { repository.clearHistory() }
  }

  suspend fun buildExportText(): String = repository.buildExportTxt()
}

class SettingsViewModel(
  private val repository: TranslationRepository,
  private val downloadHelper: DownloadHelper,
) : ViewModel() {

  val settings: StateFlow<UserSettings> =
    repository.settingsFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = UserSettings(),
    )

  val modelsState: StateFlow<OfflineModelsState> = downloadHelper.state

  fun setSourceLanguageCode(code: String) {
    viewModelScope.launch {
      repository.setSourceLanguageCode(code)
      downloadHelper.refreshModelStatus(code)
    }
  }

  fun downloadModelsFor(code: String) {
    viewModelScope.launch {
      downloadHelper.ensureAllModelsReady(code)
    }
  }

  fun deleteVoskModel(code: String) {
    viewModelScope.launch {
      downloadHelper.deleteVoskModel(code)
    }
  }

  fun setSkipArabic(skip: Boolean) {
    viewModelScope.launch { repository.setSkipArabic(skip) }
  }

  fun setAudioChunkSeconds(seconds: Int) {
    viewModelScope.launch { repository.setAudioChunkSeconds(seconds) }
  }

  fun setAutoRestartOnBoot(enabled: Boolean) {
    viewModelScope.launch { repository.setAutoRestartOnBoot(enabled) }
  }
}

class LiveTranslatorViewModelFactory(private val app: LiveTranslatorApp) :
  ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    when {
      modelClass.isAssignableFrom(MainViewModel::class.java) ->
        MainViewModel(app.repository, app.sessionManager, app.downloadHelper) as T
      modelClass.isAssignableFrom(OverlaySettingsViewModel::class.java) ->
        OverlaySettingsViewModel(app.repository) as T
      modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
        HistoryViewModel(app.repository) as T
      modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
        SettingsViewModel(app.repository, app.downloadHelper) as T
      else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
