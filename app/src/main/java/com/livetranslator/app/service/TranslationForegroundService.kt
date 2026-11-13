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
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.engine.AudioCaptureManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class TranslationForegroundService : LifecycleService() {

  private val app: LiveTranslatorApp
    get() = application as LiveTranslatorApp

  private lateinit var audioCaptureManager: AudioCaptureManager

  private var mediaProjection: MediaProjection? = null
  private var latestSettings: UserSettings = UserSettings()
  private var projectionCallback: MediaProjection.Callback? = null
  private var lastCommittedSource: String = ""

  override fun onCreate() {
    super.onCreate()
    audioCaptureManager = AudioCaptureManager(this)

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
      app.sessionManager.updateRunning(
        running = true,
        status = "جاري تحميل نماذج Vosk و ML Kit...",
        mode = "تهيئة محلية",
      )

      if (!micOnly) {
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

      val modelResult = app.downloadHelper.ensureAllModelsReady(latestSettings.sourceLanguageCode)
      val modelDir =
        modelResult.getOrElse { err ->
          app.sessionManager.publishError(
            err.message ?: "تعذر تجهيز نموذج Vosk أو ML Kit المحلي"
          )
          stopCaptureAndSelf()
          return@launch
        }

      val loadResult = runCatching { app.voskEngine.loadModel(modelDir) }
      if (loadResult.isFailure) {
        app.sessionManager.publishError(
          loadResult.exceptionOrNull()?.message ?: "تعذر تحميل نموذج Vosk في الذاكرة"
        )
        stopCaptureAndSelf()
        return@launch
      }

      val projectionActive =
        !micOnly && mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
      startVoskCapture(if (projectionActive) mediaProjection else null)
    }
  }

  private fun startVoskCapture(projection: MediaProjection?) {
    lastCommittedSource = ""
    app.sessionManager.updateRunning(
      running = true,
      status = "يستمع الآن (بدون إنترنت)...",
      mode = if (projection != null) "صوت النظام 16kHz (Vosk)" else "الميكروفون 16kHz (Vosk)",
    )

    audioCaptureManager.startPcmStream(
      scope = lifecycleScope,
      mediaProjection = projection,
      chunkSecondsProvider = { latestSettings.audioChunkSeconds },
      onModeChanged = { mode -> app.sessionManager.publishStatus("يستمع الآن (بدون نت)", mode) },
      onAudioLevel = { level -> app.sessionManager.updateAudioLevel(level) },
      onPcmFrame = { pcmBytes, length ->
        val output = app.voskEngine.acceptPcm(pcmBytes, length)
        if (output != null) {
          if (output.isFinal) {
            translateAndPublish(output.text)
          } else {
            app.sessionManager.updatePartialRecognition(output.text)
          }
        }
      },
      onChunkWindowElapsed = {
        val flushed = app.voskEngine.flushFinalText()
        if (flushed.isNotBlank()) {
          translateAndPublish(flushed)
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
    app.sessionManager.publishStatus("جاري الترجمة محلياً (ML Kit)...")

    app.mlKitEngine
      .translateToArabic(
        text = cleanSource,
        sourceLangCode = settings.sourceLanguageCode,
        skipArabic = settings.skipArabic,
      )
      .onSuccess { arabic ->
        if (arabic.isBlank()) {
          app.sessionManager.updateTranslating(false)
          app.sessionManager.publishStatus("يستمع الآن (بدون نت)")
        } else {
          val now = System.currentTimeMillis()
          app.repository.saveTranslation(
            sourceText = cleanSource,
            translatedText = arabic,
            sourceLang = settings.sourceLanguageCode,
            timestamp = now,
          )
          app.sessionManager.publishTranslation(
            sourceText = cleanSource,
            translatedText = arabic,
            sourceLang = settings.sourceLanguageCode,
            timestampMs = now,
          )
          app.sessionManager.publishStatus("ترجمة فورية محلية نشطة")
        }
      }
      .onFailure { err ->
        app.sessionManager.publishError(err.message ?: "فشلت ترجمة ML Kit المحلية")
      }
  }

  private fun stopCaptureAndSelf() {
    audioCaptureManager.stop()
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
    audioCaptureManager.stop()
    projectionCallback?.let { cb -> runCatching { mediaProjection?.unregisterCallback(cb) } }
    runCatching { mediaProjection?.stop() }
    mediaProjection = null
    app.sessionManager.updateRunning(
      running = false,
      status = "جاهز للالتقاط (بدون إنترنت)",
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
