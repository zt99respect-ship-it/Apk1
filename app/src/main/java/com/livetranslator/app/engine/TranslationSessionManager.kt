package com.livetranslator.app.engine

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TranslationSessionState(
  val isRunning: Boolean = false,
  val isTranslating: Boolean = false,
  val isOverlayActive: Boolean = false,
  val isCardExpanded: Boolean = false,
  val bubbleSnoozedUntilMs: Long = 0L,
  val statusMessage: String = "جاهز للالتقاط (بدون إنترنت)",
  val engineMode: String = "خامل",
  val audioLevel: Float = 0f,
  val segmentsTranslated: Int = 0,
  val lastPartialSource: String = "",
  val lastSourceText: String = "",
  val lastTranslatedText: String = "",
  val lastSourceLang: String = "en",
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

  fun updateRunning(running: Boolean, status: String, mode: String) {
    _state.update {
      it.copy(
        isRunning = running,
        statusMessage = status,
        engineMode = mode,
        audioLevel = if (running) it.audioLevel else 0f,
        segmentsTranslated = if (running) 0 else it.segmentsTranslated,
        lastPartialSource = if (running) "" else it.lastPartialSource,
        lastError = if (running) null else it.lastError,
      )
    }
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

  fun updateTranslating(translating: Boolean) {
    _state.update { it.copy(isTranslating = translating) }
  }

  fun updateOverlayActive(active: Boolean) {
    _state.update { it.copy(isOverlayActive = active) }
  }

  fun setCardExpanded(expanded: Boolean) {
    _state.update { it.copy(isCardExpanded = expanded) }
  }

  fun snoozeBubbleFor(durationMs: Long) {
    _state.update {
      it.copy(
        bubbleSnoozedUntilMs = System.currentTimeMillis() + durationMs,
        isCardExpanded = false,
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
        segmentsTranslated = it.segmentsTranslated + 1,
        lastPartialSource = "",
        lastSourceText = sourceText,
        lastTranslatedText = translatedText,
        lastSourceLang = sourceLang,
        lastTimestampMs = timestampMs,
        lastError = null,
        isCardExpanded = true,
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

  fun publishError(error: String, expandOverlay: Boolean = true) {
    _state.update {
      it.copy(
        isTranslating = false,
        lastError = error,
        statusMessage = "تنبيه في المحرك المحلي",
        isCardExpanded = if (expandOverlay && it.isOverlayActive) true else it.isCardExpanded,
      )
    }
  }
}
