package com.livetranslator.app.service

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.SpeechEnginePreference
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.engine.AndroidSpeechRecognizerBridge
import com.livetranslator.app.engine.AudioCaptureManager
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TranslationForegroundService : LifecycleService() {

  private val app: LiveTranslatorApp
    get() = application as LiveTranslatorApp

  private lateinit var audioCaptureManager: AudioCaptureManager
  private lateinit var speechBridge: AndroidSpeechRecognizerBridge

  private var mediaProjection: MediaProjection? = null
  private var latestSettings: UserSettings = UserSettings()
  private var projectionCallback: MediaProjection.Callback? = null
  private var lastCommittedSource: String = ""
  private var currentSessionId: Long? = null
  private var sessionStartTimestamp: Long = 0L
  private var sessionSegmentCount: Int = 0
  private var lastResolvedSourceLang: String = "auto"

  @Volatile private var voskReadyInMemory: Boolean = false
  @Volatile private var speechPipeFailedOver: Boolean = false

  override fun onCreate() {
    super.onCreate()
    audioCaptureManager = AudioCaptureManager(this)
    speechBridge = AndroidSpeechRecognizerBridge(this)

    lifecycleScope.launch {
      app.repository.settingsFlow.collectLatest { settings ->
        latestSettings = settings
      }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    when (intent?.action) {
      ACTION_STOP -> {
        stopCaptureAndSelf()
        return START_NOT_STICKY
      }
      ACTION_START_MIC_ONLY -> {
        startForegroundSafely(micOnly = true)
        startOverlayService()
        beginTranslationPipeline(micOnly = true)
      }
      ACTION_START, null -> {
        startForegroundSafely(micOnly = false)
        startOverlayService()
        beginTranslationPipeline(micOnly = false)
      }
    }
    return START_STICKY
  }

  private fun startForegroundSafely(micOnly: Boolean) {
    val notification = NotificationHelper.buildTranslationNotification(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      val hasMic =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      var serviceType =
        if (micOnly && hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
      if (!micOnly && hasMic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
      }
      startForeground(NotificationHelper.TRANSLATION_NOTIFICATION_ID, notification, serviceType)
    } else {
      startForeground(NotificationHelper.TRANSLATION_NOTIFICATION_ID, notification)
    }
  }

  private fun startOverlayService() {
    val overlayIntent =
      Intent(this, OverlayService::class.java).apply {
        action = OverlayService.ACTION_SHOW_ACTIVE
      }
    runCatching { ContextCompat.startForegroundService(this, overlayIntent) }
  }

  private fun beginTranslationPipeline(micOnly: Boolean) {
    lifecycleScope.launch {
      latestSettings = app.repository.getCurrentSettings()
      val effectiveMicOnly =
        micOnly || latestSettings.audioSourcePreference == AudioSourcePreference.MICROPHONE

      sessionStartTimestamp = System.currentTimeMillis()
      sessionSegmentCount = 0
      lastResolvedSourceLang = latestSettings.sourceLanguageCode
      voskReadyInMemory = false
      speechPipeFailedOver = false

      val audioModeLabel =
        if (effectiveMicOnly) "الميكروفون" else "صوت النظام (الهاتف)"

      val createdId =
        app.repository.createSession(
          startTimestamp = sessionStartTimestamp,
          sourceLang = latestSettings.sourceLanguageCode,
          audioMode = audioModeLabel,
        )
      currentSessionId = createdId

      app.sessionManager.updateRunning(
        running = true,
        status = "جاري تهيئة محرك الصوت والترجمة...",
        mode = audioModeLabel,
        sessionId = createdId,
      )

      if (!effectiveMicOnly) {
        val (resultCode, resultData) = app.sessionManager.consumePendingMediaProjection()
        if (resultCode == Activity.RESULT_OK && resultData != null) {
          val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
          val projection =
            runCatching { manager.getMediaProjection(resultCode, resultData) }.getOrNull()
          if (projection != null) {
            val cb =
              object : MediaProjection.Callback() {
                override fun onStop() {
                  stopCaptureAndSelf()
                }
              }
            projectionCallback = cb
            projection.registerCallback(cb, Handler(Looper.getMainLooper()))
            mediaProjection = projection
          }
        }
      }

      // Prepare Vosk model locally (needed for Vosk-only or instant failover in Flexible mode)
      val existingVoskDir: File? =
        app.downloadHelper.resolveVoskModelDir(latestSettings.sourceLanguageCode)
      if (existingVoskDir != null) {
        voskReadyInMemory = runCatching { app.voskEngine.loadModel(existingVoskDir) }.isSuccess
      } else {
        val modelResult = app.downloadHelper.ensureAllModelsReady(latestSettings.sourceLanguageCode)
        val downloadedDir = modelResult.getOrNull()
        if (downloadedDir != null) {
          voskReadyInMemory = runCatching { app.voskEngine.loadModel(downloadedDir) }.isSuccess
        }
      }

      val projectionActive =
        !effectiveMicOnly && mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
      startFlexibleCapture(
        projection = if (projectionActive) mediaProjection else null,
        preferSystemPlayback = !effectiveMicOnly,
      )
    }
  }

  private fun startFlexibleCapture(
    projection: MediaProjection?,
    preferSystemPlayback: Boolean,
  ) {
    lastCommittedSource = ""
    val useFlexibleSpeechPipe =
      latestSettings.speechEnginePreference == SpeechEnginePreference.FLEXIBLE_AUTO &&
        speechBridge.isPipeAvailable()

    val pipeStarted =
      if (useFlexibleSpeechPipe) {
        speechBridge.startWithPcmPipe(
          sourceLanguageCode = latestSettings.sourceLanguageCode,
          onPartial = { partial ->
            app.sessionManager.updatePartialRecognition(partial)
          },
          onFinal = { recognized ->
            lifecycleScope.launch { translateAndPublish(recognized) }
          },
          onFallbackToVosk = { reason ->
            speechPipeFailedOver = true
            app.sessionManager.publishStatus(
              status = if (voskReadyInMemory) "التعرّف المباشر نشط (Vosk)" else reason,
              mode = if (projection != null) "صوت النظام 16kHz (Vosk)" else "الميكروفون 16kHz (Vosk)",
            )
          },
        )
      } else {
        false
      }

    if (!pipeStarted && !voskReadyInMemory) {
      app.sessionManager.publishError(
        "تعذر تشغيل محرك التعرف الصوتي. تأكد من اتصال الإنترنت عند التشغيل الأول لتحميل الحزمة الصوتية."
      )
      stopCaptureAndSelf()
      return
    }

    val activeModeLabel =
      when {
        pipeStarted && projection != null -> "صوت الهاتف • محرك مرن"
        pipeStarted -> "الميكروفون • محرك مرن"
        projection != null -> "صوت الهاتف • محلي"
        else -> "الميكروفون • محلي"
      }

    app.sessionManager.updateRunning(
      running = true,
      status = "يستمع الآن ويترجم للعربية...",
      mode = activeModeLabel,
      sessionId = currentSessionId,
    )

    audioCaptureManager.startPcmStream(
      scope = lifecycleScope,
      mediaProjection = projection,
      preferSystemPlayback = preferSystemPlayback,
      chunkSecondsProvider = { latestSettings.audioChunkSeconds },
      onModeChanged = { mode -> app.sessionManager.publishStatus("يستمع الآن...", mode) },
      onAudioLevel = { level -> app.sessionManager.updateAudioLevel(level) },
      onPcmFrame = { pcmBytes, length ->
        val pipeFed =
          pipeStarted && !speechPipeFailedOver && speechBridge.writePcmFrame(pcmBytes, length)
        if (!pipeFed && voskReadyInMemory) {
          val output = app.voskEngine.acceptPcm(pcmBytes, length)
          if (output != null) {
            if (output.isFinal) {
              translateAndPublish(output.text)
            } else {
              app.sessionManager.updatePartialRecognition(output.text)
            }
          }
        }
      },
      onChunkWindowElapsed = {
        if ((!pipeStarted || speechPipeFailedOver) && voskReadyInMemory) {
          val flushed = app.voskEngine.flushFinalText()
          if (flushed.isNotBlank()) {
            translateAndPublish(flushed)
          }
        }
      },
      onError = { err ->
        app.sessionManager.publishError(err)
        stopCaptureAndSelf()
      },
    )
  }

  private suspend fun translateAndPublish(sourceText: String) {
    val cleanSource = sourceText.trim()
    if (cleanSource.isBlank() || cleanSource == lastCommittedSource) return
    lastCommittedSource = cleanSource

    val settings = latestSettings
    app.sessionManager.updateTranslating(true)
    app.sessionManager.publishStatus("جاري الترجمة للعربية...")

    app.mlKitEngine
      .translateToArabic(
        text = cleanSource,
        sourceLangCode = settings.sourceLanguageCode,
        skipArabic = settings.skipArabic,
      )
      .onSuccess { outcome ->
        val arabic = outcome.translatedText
        if (arabic.isBlank()) {
          app.sessionManager.updateTranslating(false)
          app.sessionManager.publishStatus("يستمع الآن...")
        } else {
          val now = System.currentTimeMillis()
          lastResolvedSourceLang = outcome.resolvedSourceLangCode
          sessionSegmentCount++
          app.repository.saveTranslation(
            sourceText = cleanSource,
            translatedText = arabic,
            sourceLang = outcome.resolvedSourceLangCode,
            timestamp = now,
            sessionId = currentSessionId,
          )
          currentSessionId?.let { sid ->
            val elapsedSeconds = ((now - sessionStartTimestamp) / 1000L).coerceAtLeast(1L)
            app.repository.updateSessionProgress(
              sessionId = sid,
              endTimestamp = now,
              durationSeconds = elapsedSeconds,
              translatedCount = sessionSegmentCount,
              sourceLang = lastResolvedSourceLang,
            )
          }
          app.sessionManager.publishTranslation(
            sourceText = cleanSource,
            translatedText = arabic,
            sourceLang = outcome.resolvedSourceLangCode,
            timestampMs = now,
          )
          app.sessionManager.publishStatus("الترجمة الفورية نشطة")
        }
      }
      .onFailure { err ->
        app.sessionManager.publishError(err.message ?: "تعذرت الترجمة الفورية")
      }
  }

  private fun finalizeSessionRecord() {
    val sid = currentSessionId ?: return
    val endNow = System.currentTimeMillis()
    val elapsed = ((endNow - sessionStartTimestamp) / 1000L).coerceAtLeast(0L)
    val count = sessionSegmentCount
    val lang = lastResolvedSourceLang
    currentSessionId = null
    app.appScope.launch {
      runCatching {
        app.repository.updateSessionProgress(
          sessionId = sid,
          endTimestamp = endNow,
          durationSeconds = elapsed,
          translatedCount = count,
          sourceLang = lang,
        )
      }
    }
  }

  private fun stopCaptureAndSelf() {
    speechBridge.stop()
    audioCaptureManager.stop()
    finalizeSessionRecord()
    projectionCallback?.let { cb -> runCatching { mediaProjection?.unregisterCallback(cb) } }
    projectionCallback = null
    runCatching { mediaProjection?.stop() }
    mediaProjection = null

    app.sessionManager.updateRunning(
      running = false,
      status = "متوقف",
      mode = "خامل",
    )
    app.sessionManager.setCardExpanded(false)

    stopForeground(Service.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    speechBridge.stop()
    audioCaptureManager.stop()
    finalizeSessionRecord()
    projectionCallback?.let { cb -> runCatching { mediaProjection?.unregisterCallback(cb) } }
    runCatching { mediaProjection?.stop() }
    mediaProjection = null
    app.sessionManager.updateRunning(
      running = false,
      status = "الخدمة الفورية جاهزة • بدون قيود",
      mode = "خامل",
    )
    super.onDestroy()
  }

  companion object {
    const val ACTION_SHOW = "com.livetranslator.app.action.START_TRANSLATION"
    const val ACTION_START = "com.livetranslator.app.action.START_TRANSLATION"
    const val ACTION_START_MIC_ONLY = "com.livetranslator.app.action.START_MIC_ONLY"
    const val ACTION_STOP = "com.livetranslator.app.action.STOP_TRANSLATION"
  }
}
