package com.livetranslator.app.engine

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.livetranslator.app.data.model.SupportedLanguages
import java.io.OutputStream

/**
 * Low-latency streaming SpeechRecognizer bridge (supporting API 33+ EXTRA_AUDIO_SOURCE PCM pipe
 * for internal system audio) with automatic silent-stall watchdog failover to local Vosk.
 */
class AndroidSpeechRecognizerBridge(private val context: Context) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var speechRecognizer: SpeechRecognizer? = null
  private var pipeWriteFd: ParcelFileDescriptor? = null
  private var pipeReadFd: ParcelFileDescriptor? = null
  private var pipeOutputStream: OutputStream? = null

  @Volatile private var active = false
  @Volatile private var usingAudioPipe = false
  @Volatile private var firstSpeechAudioMs = 0L
  @Volatile private var lastRecognitionActivityMs = 0L
  @Volatile private var receivedAnyRecognitionOutput = false

  private var configuredLangCode: String = SupportedLanguages.AUTO_CODE
  private var onPartialCallback: ((String) -> Unit)? = null
  private var onFinalCallback: ((String) -> Unit)? = null
  private var onFallbackCallback: ((String) -> Unit)? = null

  fun isPipeAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      SpeechRecognizer.isRecognitionAvailable(context)

  fun startWithPcmPipe(
    sourceLanguageCode: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit,
    onFallbackToVosk: (String) -> Unit,
  ): Boolean {
    if (!isPipeAvailable()) return false
    stop()

    configuredLangCode = sourceLanguageCode
    onPartialCallback = onPartial
    onFinalCallback = onFinal
    onFallbackCallback = onFallbackToVosk
    usingAudioPipe = true
    active = true
    firstSpeechAudioMs = 0L
    lastRecognitionActivityMs = System.currentTimeMillis()
    receivedAnyRecognitionOutput = false

    val pipe =
      runCatching { ParcelFileDescriptor.createPipe() }.getOrElse {
        stop()
        return false
      }
    pipeReadFd = pipe[0]
    pipeWriteFd = pipe[1]
    pipeOutputStream = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

    mainHandler.post {
      if (!active) return@post
      startRecognizerInternal(usePipe = true)
    }
    return true
  }

  /**
   * Feeds a PCM frame to the SpeechRecognizer pipe and checks whether active speech has stalled
   * silently without producing any partials/finals (common when an OEM speech service blocks pipes).
   */
  fun writePcmFrame(pcmBytes: ByteArray, length: Int, isSpeechActive: Boolean): Boolean {
    if (!active || !usingAudioPipe) return false
    val stream = pipeOutputStream ?: return false

    val now = System.currentTimeMillis()
    if (isSpeechActive) {
      if (firstSpeechAudioMs == 0L) {
        firstSpeechAudioMs = now
      } else if (
        !receivedAnyRecognitionOutput &&
          now - firstSpeechAudioMs >= STALL_WATCHDOG_MS &&
          now - lastRecognitionActivityMs >= STALL_WATCHDOG_MS
      ) {
        mainHandler.post {
          if (active && !receivedAnyRecognitionOutput) {
            triggerFallback("التبديل التلقائي الفوري لمحرك Vosk للاستجابة السريعة")
          }
        }
        return false
      }
    }

    return try {
      stream.write(pcmBytes, 0, length)
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun startRecognizerInternal(usePipe: Boolean) {
    if (!active) return
    runCatching { speechRecognizer?.destroy() }

    val recognizer =
      runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        ?: run {
          triggerFallback("تعذر إنشاء محرك التعرّف الصوتي المرن")
          return
        }

    speechRecognizer = recognizer
    recognizer.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          lastRecognitionActivityMs = System.currentTimeMillis()
        }

        override fun onBeginningOfSpeech() {
          lastRecognitionActivityMs = System.currentTimeMillis()
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
          if (!active) return
          if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            restartSoon(usePipe = usePipe)
          } else {
            triggerFallback("التحويل التلقائي لمحرك Vosk (رمز $error)")
          }
        }

        override fun onResults(results: Bundle?) {
          if (!active) return
          receivedAnyRecognitionOutput = true
          lastRecognitionActivityMs = System.currentTimeMillis()
          firstSpeechAudioMs = 0L
          val text =
            results
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
          if (text.isNotBlank()) {
            onFinalCallback?.invoke(text)
          }
          restartSoon(usePipe = usePipe)
        }

        override fun onPartialResults(partialResults: Bundle?) {
          if (!active) return
          val partial =
            partialResults
              ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
              ?.firstOrNull()
              ?.trim()
              .orEmpty()
          if (partial.isNotBlank()) {
            receivedAnyRecognitionOutput = true
            lastRecognitionActivityMs = System.currentTimeMillis()
            firstSpeechAudioMs = 0L
            onPartialCallback?.invoke(partial)
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
      }
    )

    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 280L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 480L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
        val localeTag = SupportedLanguages.findByCode(configuredLangCode).speechLocaleTag
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
        if (usePipe && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          pipeReadFd?.let { readFd ->
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readFd)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(
              RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
              AudioCaptureManager.SAMPLE_RATE_HZ,
            )
          }
        }
      }

    runCatching { recognizer.startListening(intent) }.onFailure {
      triggerFallback("التحويل التلقائي لمحرك Vosk الاحتياطي")
    }
  }

  private fun restartSoon(usePipe: Boolean) {
    if (!active) return
    mainHandler.postDelayed(
      {
        if (active) {
          startRecognizerInternal(usePipe = usePipe)
        }
      },
      20L,
    )
  }

  private fun triggerFallback(reason: String) {
    val cb = onFallbackCallback
    stop()
    cb?.invoke(reason)
  }

  fun stop() {
    active = false
    usingAudioPipe = false
    firstSpeechAudioMs = 0L
    receivedAnyRecognitionOutput = false
    onPartialCallback = null
    onFinalCallback = null
    onFallbackCallback = null
    mainHandler.removeCallbacksAndMessages(null)
    mainHandler.post {
      runCatching { speechRecognizer?.destroy() }
      speechRecognizer = null
    }
    runCatching { pipeOutputStream?.close() }
    runCatching { pipeWriteFd?.close() }
    runCatching { pipeReadFd?.close() }
    pipeOutputStream = null
    pipeWriteFd = null
    pipeReadFd = null
  }

  companion object {
    private const val STALL_WATCHDOG_MS = 2200L
  }
}
