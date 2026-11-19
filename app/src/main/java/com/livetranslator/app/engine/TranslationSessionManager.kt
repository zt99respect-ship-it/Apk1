package com.livetranslator.app.engine

import android.content.Intent
import com.livetranslator.app.data.model.SupportedLanguages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TranslationSessionState(
  val isRunning: Boolean = false,
  val isTranslating: Boolean = false,
  val isLivePartial: Boolean = false,
  val isOverlayActive: Boolean = false,
  val isControlPanelOpen: Boolean = false,
  val isSubtitleVisible: Boolean = true,
  val isRepositioningSubtitle: Boolean = false,
  val pendingStartRequestId: Long = 0L,
  val bubbleSnoozedUntilMs: Long = 0L,
  val statusMessage: String = "الخدمة الفورية جاهزة • بدون قيود",
  val engineMode: String = "خامل",
  val audioLevel: Float = 0f,
  val segmentsTranslated: Int = 0,
  val sessionStartMs: Long = 0L,
  val activeSessionId: Long? = null,
  val lastPartialSource: String = "",
  val lastSourceText: String = "",
  val lastTranslatedText: String = "",
  val lastSourceLang: String = SupportedLanguages.AUTO_CODE,
  val lastTimestampMs: Long = 0L,
  val lastError: String? = null,
) {
  val isBubbleSnoozed: Boolean
    get() = bubbleSnoozedUntilMs > System.currentTimeMillis()
}

class TranslationSessionManager {

  private val _state = MutableStateFlow(TranslationSessionState())
  val state: StateFlow<TranslationSessionState> = _state.asStateFlow()

  @Volatile private var pendingResultCode: Int = 0
  @Volatile private var pendingResultData: Intent? = null

  fun setPendingMediaProjection(resultCode: Int, data: Intent?) {
    pendingResultCode = resultCode
    pendingResultData = data
  }

  fun consumePendingMediaProjection(): Pair<Int, Intent?> {
    val result = pendingResultCode to pendingResultData
    pendingResultCode = 0
    pendingResultData = null
    return result
  }

  fun updateRunning(
    running: Boolean,
    status: String,
    mode: String,
    sessionId: Long? = null,
  ) {
    _state.update {
      it.copy(
        isRunning = running,
        isLivePartial = if (running) it.isLivePartial else false,
        statusMessage = status,
        engineMode = mode,
        audioLevel = if (running) it.audioLevel else 0f,
        segmentsTranslated = if (running) 0 else it.segmentsTranslated,
        sessionStartMs = if (running) System.currentTimeMillis() else 0L,
        activeSessionId = if (running) (sessionId ?: it.activeSessionId) else null,
        lastPartialSource = if (running) "" else it.lastPartialSource,
        lastError = if (running) null else it.lastError,
        isSubtitleVisible = if (running) true else it.isSubtitleVisible,
      )
    }
  }

  fun setActiveSessionId(sessionId: Long?) {
    _state.update { it.copy(activeSessionId = sessionId) }
  }

  fun updateAudioLevel(level: Float) {
    _state.update { it.copy(audioLevel = level.coerceIn(0f, 1f)) }
  }

  fun updatePartialRecognition(partialText: String) {
    _state.update {
      it.copy(
        lastPartialSource = partialText,
        isTranslating = partialText.isNotBlank(),
      )
    }
  }

  /**
   * Publishes real-time Arabic translation directly from partial speech recognition
   * so subtitles appear instantaneously while the speaker is still talking.
   */
  fun publishLivePartialTranslation(
    partialSource: String,
    liveArabicText: String,
    sourceLang: String,
  ) {
    _state.update {
      it.copy(
        isTranslating = false,
        isLivePartial = true,
        lastPartialSource = partialSource,
        lastTranslatedText = liveArabicText,
        lastSourceLang = sourceLang,
        lastTimestampMs = System.currentTimeMillis(),
        lastError = null,
      )
    }
  }

  fun updateTranslating(translating: Boolean) {
    _state.update { it.copy(isTranslating = translating) }
  }

  fun updateOverlayActive(active: Boolean) {
    _state.update {
      it.copy(
        isOverlayActive = active,
        isControlPanelOpen = if (!active) false else it.isControlPanelOpen,
        isRepositioningSubtitle = if (!active) false else it.isRepositioningSubtitle,
      )
    }
  }

  fun setControlPanelOpen(open: Boolean) {
    _state.update { it.copy(isControlPanelOpen = open) }
  }

  fun toggleControlPanel() {
    _state.update { it.copy(isControlPanelOpen = !it.isControlPanelOpen) }
  }

  fun setSubtitleVisible(visible: Boolean) {
    _state.update { it.copy(isSubtitleVisible = visible) }
  }

  fun setRepositioningSubtitle(repositioning: Boolean) {
    _state.update {
      it.copy(
        isRepositioningSubtitle = repositioning,
        isSubtitleVisible = if (repositioning) true else it.isSubtitleVisible,
      )
    }
  }

  fun requestStartFromOverlay() {
    _state.update { it.copy(pendingStartRequestId = System.nanoTime()) }
  }

  fun clearPendingStartRequest() {
    _state.update { it.copy(pendingStartRequestId = 0L) }
  }

  fun snoozeBubbleFor(durationMs: Long) {
    _state.update {
      it.copy(
        bubbleSnoozedUntilMs = System.currentTimeMillis() + durationMs,
        isControlPanelOpen = false,
      )
    }
  }

  fun clearSnooze() {
    _state.update { it.copy(bubbleSnoozedUntilMs = 0L) }
  }

  fun publishTranslation(
    sourceText: String,
    translatedText: String,
    sourceLang: String,
    timestampMs: Long = System.currentTimeMillis(),
  ) {
    _state.update {
      it.copy(
        isTranslating = false,
        isLivePartial = false,
        segmentsTranslated = it.segmentsTranslated + 1,
        lastPartialSource = "",
        lastSourceText = sourceText,
        lastTranslatedText = translatedText,
        lastSourceLang = sourceLang,
        lastTimestampMs = timestampMs,
        lastError = null,
      )
    }
  }

  fun publishStatus(status: String, mode: String? = null) {
    _state.update {
      it.copy(
        statusMessage = status,
        engineMode = mode ?: it.engineMode,
      )
    }
  }

  fun publishError(error: String, expandOverlay: Boolean = false) {
    _state.update {
      it.copy(
        isTranslating = false,
        lastError = error,
        statusMessage = "تنبيه في خدمة الترجمة",
        isControlPanelOpen = if (expandOverlay && it.isOverlayActive) true else it.isControlPanelOpen,
      )
    }
  }
}
