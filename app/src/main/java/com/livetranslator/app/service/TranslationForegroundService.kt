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
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.model.TranslationMode
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.engine.AndroidSpeechRecognizerBridge
import com.livetranslator.app.engine.AudioCaptureManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
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
  private var lastLiveTranslatedSource: String = ""
  private var currentSessionId: Long? = null
  private var sessionStartTimestamp: Long = 0L
  private var sessionSegmentCount: Int = 0
  private var lastResolvedSourceLang: String = "auto"

  @Volatile private var voskReadyInMemory: Boolean = false
  @Volatile private var speechPipeFailedOver: Boolean = false
  @Volatile private var latestAudioLevel: Float = 0f

  private data class PartialSignal(val text: String, val seq: Long)

  private val livePartialFlow = MutableStateFlow<PartialSignal?>(null)
  private var partialSeqCounter: Long = 0L

  private var pcmConsumerJob: Job? = null
  private var liveTranslationJob: Job? = null
  private var finalCommitJob: Job? = null
  private var backgroundModelPrepJob: Job? = null

  private var pcmFrameChannel: Channel<ByteArray>? = null
  private var finalSentenceChannel: Channel<String>? = null

  override fun onCreate() {
    super.onCreate()
    audioCaptureManager = AudioCaptureManager(this)
    speechBridge = AndroidSpeechRecognizerBridge(this)

    lifecycleScope.launch {
      app.repository.settingsFlow.collectLatest { settings ->
        val modeChanged = settings.translationMode != latestSettings.translationMode
        latestSettings = settings
        speechBridge.updateSkipArabic(settings.skipArabic || app.remoteControlConfig.value.forceSkipArabic)
        if (modeChanged && app.sessionManager.state.value.isRunning) {
          if (mediaProjection != null) {
            switchRunningModeSafely(settings.translationMode)
          } else if (settings.translationMode == TranslationMode.SCREEN_OCR) {
            app.sessionManager.publishError(
              "لتشغيل ترجمة الشاشة اضغط «ابدأ» مجدداً لمنح إذن التقاط الشاشة",
              expandOverlay = true,
            )
            stopCaptureAndSelf()
          }
        }
      }
    }

    lifecycleScope.launch {
      app.remoteControlConfig.collectLatest { remote ->
        speechBridge.updateSkipArabic(latestSettings.skipArabic || remote.forceSkipArabic)
        if (app.sessionManager.state.value.isRunning) {
          val activeMode = app.sessionManager.state.value.activeTranslationMode
          val blockedByRemote =
            remote.maintenanceEnabled ||
              (activeMode == TranslationMode.AUDIO_LIVE && !remote.allowAudioLive) ||
              (activeMode == TranslationMode.SCREEN_OCR && !remote.allowScreenOcr)
          if (blockedByRemote) {
            app.sessionManager.publishError(
              remote.maintenanceMessage.ifBlank { "تم إيقاف الخدمة مؤقتاً من لوحة تحكم الإدارة." },
              expandOverlay = false,
            )
            stopCaptureAndSelf()
          }
        }
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
      ACTION_SNAP_SCREEN_ONCE -> {
        app.screenOcrEngine.requestImmediateSnap()
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

  private fun switchRunningModeSafely(nextMode: TranslationMode) {
    val activeProjection = mediaProjection ?: return
    stopStreamingJobsOnly()
    speechBridge.stop()
    audioCaptureManager.stop()
    app.screenOcrEngine.stop()
    lastCommittedSource = ""
    lastLiveTranslatedSource = ""

    if (nextMode == TranslationMode.SCREEN_OCR) {
      startScreenOcrPipeline(activeProjection)
    } else {
      lifecycleScope.launch {
        prepareAndStartAudioPipeline(
          projection = activeProjection,
          preferSystemPlayback = latestSettings.audioSourcePreference != AudioSourcePreference.MICROPHONE,
        )
      }
    }
  }

  private fun beginTranslationPipeline(micOnly: Boolean) {
    stopStreamingJobsOnly()
    app.screenOcrEngine.stop()

    lifecycleScope.launch {
      latestSettings = app.repository.getCurrentSettings()
      val effectiveMode =
        if (micOnly) TranslationMode.AUDIO_LIVE else latestSettings.translationMode
      val effectiveMicOnly =
        effectiveMode == TranslationMode.AUDIO_LIVE &&
          (micOnly || latestSettings.audioSourcePreference == AudioSourcePreference.MICROPHONE)

      sessionStartTimestamp = System.currentTimeMillis()
      sessionSegmentCount = 0
      lastResolvedSourceLang = latestSettings.sourceLanguageCode
      voskReadyInMemory = false
      speechPipeFailedOver = false
      lastCommittedSource = ""
      lastLiveTranslatedSource = ""
      livePartialFlow.value = null

      val audioModeLabel =
        when {
          effectiveMode == TranslationMode.SCREEN_OCR -> "ترجمة الشاشة (OCR)"
          effectiveMicOnly -> "الميكروفون"
          else -> "صوت النظام (الهاتف)"
        }

      val createdId =
        app.repository.createSession(
          startTimestamp = sessionStartTimestamp,
          sourceLang = latestSettings.sourceLanguageCode,
          audioMode = audioModeLabel,
        )
      currentSessionId = createdId

      app.sessionManager.updateRunning(
        running = true,
        status =
          if (effectiveMode == TranslationMode.SCREEN_OCR) "جاري تشغيل ترجمة الشاشة..."
          else "جاري تهيئة البث اللحظي...",
        mode = audioModeLabel,
        sessionId = createdId,
        translationMode = effectiveMode,
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

      app.mlKitEngine.resetStickyLanguage(latestSettings.sourceLanguageCode)
      val prewarmJob =
        lifecycleScope.launch(Dispatchers.IO) {
          app.mlKitEngine.prewarm(latestSettings.sourceLanguageCode)
        }

      if (effectiveMode == TranslationMode.SCREEN_OCR) {
        prewarmJob.join()
        val projection = mediaProjection
        if (projection == null) {
          app.sessionManager.publishError("يرجى منح إذن التقاط الشاشة لتشغيل ترجمة الشاشة")
          stopCaptureAndSelf()
          return@launch
        }
        startScreenOcrPipeline(projection)
      } else {
        val existingVoskDir: File? =
          app.downloadHelper.resolveVoskModelDir(latestSettings.sourceLanguageCode)
        if (existingVoskDir != null) {
          voskReadyInMemory = runCatching { app.voskEngine.loadModel(existingVoskDir) }.isSuccess
        }

        val wantsFlexiblePipe =
          latestSettings.speechEnginePreference == SpeechEnginePreference.FLEXIBLE_AUTO &&
            speechBridge.isPipeAvailable()

        if (!voskReadyInMemory) {
          if (wantsFlexiblePipe) {
            backgroundModelPrepJob =
              lifecycleScope.launch(Dispatchers.IO) {
                val downloadedDir =
                  app.downloadHelper.ensureAllModelsReady(latestSettings.sourceLanguageCode).getOrNull()
                if (downloadedDir != null) {
                  voskReadyInMemory = runCatching { app.voskEngine.loadModel(downloadedDir) }.isSuccess
                }
              }
          } else {
            val modelResult = app.downloadHelper.ensureAllModelsReady(latestSettings.sourceLanguageCode)
            val downloadedDir = modelResult.getOrNull()
            if (downloadedDir != null) {
              voskReadyInMemory = runCatching { app.voskEngine.loadModel(downloadedDir) }.isSuccess
            }
          }
        }

        prewarmJob.join()
        val projectionActive =
          !effectiveMicOnly && mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        prepareAndStartAudioPipeline(
          projection = if (projectionActive) mediaProjection else null,
          preferSystemPlayback = !effectiveMicOnly,
        )
      }
    }
  }

  private fun startScreenOcrPipeline(projection: MediaProjection) {
    app.sessionManager.updateRunning(
      running = true,
      status = "ترجمة الشاشة المباشرة نشطة...",
      mode = "ترجمة الشاشة • فوري",
      sessionId = currentSessionId,
      translationMode = TranslationMode.SCREEN_OCR,
    )

    app.screenOcrEngine.startScreenOcrLoop(
      scope = lifecycleScope,
      mediaProjection = projection,
      intervalMsProvider = {
        app.remoteControlConfig.value.remoteScreenScanIntervalMs.toLong()
          .takeIf { it in 450L..3000L } ?: latestSettings.screenScanIntervalMs
      },
      onExtractedScreenText = { screenText ->
        translateAndPublishFinal(screenText)
      },
      onError = { err ->
        app.sessionManager.publishError(err)
        stopCaptureAndSelf()
      },
    )
  }

  private fun prepareAndStartAudioPipeline(
    projection: MediaProjection?,
    preferSystemPlayback: Boolean,
  ) {
    val useFlexibleSpeechPipe =
      latestSettings.speechEnginePreference == SpeechEnginePreference.FLEXIBLE_AUTO &&
        speechBridge.isPipeAvailable()

    val pipeStarted =
      if (useFlexibleSpeechPipe) {
        speechBridge.startWithPcmPipe(
          sourceLanguageCode = latestSettings.sourceLanguageCode,
          skipArabic = latestSettings.skipArabic,
          onPartial = { partial -> submitPartialForInstantTranslation(partial) },
          onFinal = { recognized -> submitFinalForCommit(recognized) },
          onArabicSpeechDetected = {
            app.sessionManager.markArabicSpeechSkipped()
          },
          onFallbackToVosk = { reason ->
            speechPipeFailedOver = true
            app.sessionManager.publishStatus(
              status = if (voskReadyInMemory) "الترجمة اللحظية نشطة (Vosk)" else reason,
              mode = if (projection != null) "صوت النظام السريع (Vosk)" else "الميكروفون السريع (Vosk)",
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
        pipeStarted && projection != null -> "صوت الهاتف • بث لحظي"
        pipeStarted -> "الميكروفون • بث لحظي"
        projection != null -> "صوت الهاتف • محلي سريع"
        else -> "الميكروفون • محلي سريع"
      }

    app.sessionManager.updateRunning(
      running = true,
      status = "ترجمة لحظية فورية نشطة...",
      mode = activeModeLabel,
      sessionId = currentSessionId,
      translationMode = TranslationMode.AUDIO_LIVE,
    )

    liveTranslationJob =
      lifecycleScope.launch(Dispatchers.Default) {
        livePartialFlow.collectLatest { signal ->
          if (signal == null) return@collectLatest
          val clean = signal.text.trim()
          if (clean.length < 2 || clean == lastLiveTranslatedSource) return@collectLatest

          val settings = latestSettings
          val hintLang = resolveHintLanguage(settings)
          app.mlKitEngine
            .translateLiveFast(
              text = clean,
              sourceLangCode = settings.sourceLanguageCode,
              skipArabic = settings.skipArabic,
              hintLangCode = hintLang,
            )
            .onSuccess { outcome ->
              if (outcome.translatedText.isNotBlank()) {
                lastLiveTranslatedSource = clean
                lastResolvedSourceLang = outcome.resolvedSourceLangCode
                app.sessionManager.publishLivePartialTranslation(
                  partialSource = clean,
                  liveArabicText = outcome.translatedText,
                  sourceLang = outcome.resolvedSourceLangCode,
                )
              }
            }
        }
      }

    val commitChannel = Channel<String>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    finalSentenceChannel = commitChannel
    finalCommitJob =
      lifecycleScope.launch(Dispatchers.IO) {
        for (sentence in commitChannel) {
          translateAndPublishFinal(sentence)
        }
      }

    val frameChannel = Channel<ByteArray>(capacity = 40, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    pcmFrameChannel = frameChannel
    pcmConsumerJob =
      lifecycleScope.launch(Dispatchers.IO) {
        for (frame in frameChannel) {
          val speechActive = latestAudioLevel >= 0.04f
          val pipeFed =
            pipeStarted &&
              !speechPipeFailedOver &&
              speechBridge.writePcmFrame(frame, frame.size, isSpeechActive = speechActive)

          if (!pipeFed && voskReadyInMemory) {
            val output =
              app.voskEngine.acceptPcm(
                frame,
                frame.size,
                filterLowConfidence = latestSettings.skipArabic,
              )
            if (output != null) {
              if (output.isFinal) {
                submitFinalForCommit(output.text)
              } else {
                submitPartialForInstantTranslation(output.text)
              }
            }
          }
        }
      }

    audioCaptureManager.startPcmStream(
      scope = lifecycleScope,
      mediaProjection = projection,
      preferSystemPlayback = preferSystemPlayback,
      maxSpeechWindowSecondsProvider = { latestSettings.audioChunkSeconds.coerceAtLeast(6) },
      onModeChanged = { mode -> app.sessionManager.publishStatus("ترجمة لحظية نشطة", mode) },
      onAudioLevel = { level ->
        latestAudioLevel = level
        app.sessionManager.updateAudioLevel(level)
      },
      onPcmFrame = { pcmBytes, _ ->
        frameChannel.trySend(pcmBytes)
      },
      onNaturalPauseFlush = {
        if ((!pipeStarted || speechPipeFailedOver) && voskReadyInMemory) {
          lifecycleScope.launch(Dispatchers.IO) {
            val flushed = app.voskEngine.flushFinalText(filterLowConfidence = latestSettings.skipArabic)
            if (flushed.isNotBlank()) {
              submitFinalForCommit(flushed)
            }
          }
        }
      },
      onError = { err ->
        app.sessionManager.publishError(err)
        stopCaptureAndSelf()
      },
    )
  }

  private fun submitPartialForInstantTranslation(partial: String) {
    val clean = partial.trim()
    if (clean.isEmpty()) return
    app.sessionManager.updatePartialRecognition(clean)
    livePartialFlow.value = PartialSignal(clean, ++partialSeqCounter)
  }

  private fun submitFinalForCommit(recognized: String) {
    val clean = recognized.trim()
    if (clean.isEmpty()) return
    finalSentenceChannel?.trySend(clean)
  }

  private fun resolveHintLanguage(settings: UserSettings): String? {
    if (settings.sourceLanguageCode != SupportedLanguages.AUTO_CODE) {
      return settings.sourceLanguageCode
    }
    return if (speechPipeFailedOver && voskReadyInMemory) {
      SupportedLanguages.resolveVoskLanguage(settings.sourceLanguageCode).code
    } else {
      null
    }
  }

  private suspend fun translateAndPublishFinal(sourceText: String) {
    val cleanSource = sourceText.trim()
    if (cleanSource.isBlank() || cleanSource == lastCommittedSource) return
    lastCommittedSource = cleanSource
    lastLiveTranslatedSource = ""

    val settings = latestSettings
    val hintLang = resolveHintLanguage(settings)

    app.mlKitEngine
      .translateToArabic(
        text = cleanSource,
        sourceLangCode = settings.sourceLanguageCode,
        skipArabic = settings.skipArabic,
        hintLangCode = hintLang,
      )
      .onSuccess { outcome ->
        val arabic = outcome.translatedText
        if (arabic.isBlank()) {
          app.sessionManager.updateTranslating(false)
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
          app.sessionManager.publishStatus(
            if (settings.translationMode == TranslationMode.SCREEN_OCR) "ترجمة الشاشة نشطة"
            else "ترجمة لحظية فورية نشطة"
          )
        }
      }
      .onFailure { err ->
        app.sessionManager.publishError(err.message ?: "تعذرت الترجمة الفورية")
      }
  }

  private fun stopStreamingJobsOnly() {
    backgroundModelPrepJob?.cancel()
    backgroundModelPrepJob = null
    pcmConsumerJob?.cancel()
    pcmConsumerJob = null
    liveTranslationJob?.cancel()
    liveTranslationJob = null
    finalCommitJob?.cancel()
    finalCommitJob = null
    pcmFrameChannel?.close()
    pcmFrameChannel = null
    finalSentenceChannel?.close()
    finalSentenceChannel = null
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
    stopStreamingJobsOnly()
    app.screenOcrEngine.stop()
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
    app.sessionManager.setRepositioningSubtitle(false)

    stopForeground(Service.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    stopStreamingJobsOnly()
    app.screenOcrEngine.stop()
    speechBridge.stop()
    audioCaptureManager.stop()
    finalizeSessionRecord()
    projectionCallback?.let { cb -> runCatching { mediaProjection?.unregisterCallback(cb) } }
    runCatching { mediaProjection?.stop() }
    mediaProjection = null
    app.sessionManager.updateRunning(
      running = false,
      status = "الخدمة الفورية جاهزة • صوت وشاشة",
      mode = "خامل",
    )
    super.onDestroy()
  }

  companion object {
    const val ACTION_SHOW = "com.livetranslator.app.action.START_TRANSLATION"
    const val ACTION_START = "com.livetranslator.app.action.START_TRANSLATION"
    const val ACTION_START_MIC_ONLY = "com.livetranslator.app.action.START_MIC_ONLY"
    const val ACTION_SNAP_SCREEN_ONCE = "com.livetranslator.app.action.SNAP_SCREEN_ONCE"
    const val ACTION_STOP = "com.livetranslator.app.action.STOP_TRANSLATION"
  }
}
