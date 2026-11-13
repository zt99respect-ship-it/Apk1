package com.livetranslator.app.engine

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

data class VoskRecognitionOutput(
  val text: String,
  val isFinal: Boolean,
)

class VoskSpeechEngine {

  private val mutex = Mutex()
  private var model: Model? = null
  private var recognizer: Recognizer? = null
  private var loadedModelPath: String? = null

  suspend fun loadModel(modelDir: File) =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val targetPath = modelDir.absolutePath
        if (loadedModelPath == targetPath && model != null && recognizer != null) {
          return@withContext
        }
        releaseInternal()
        runCatching { LibVosk.setLogLevel(LogLevel.WARNINGS) }
        val loadedModel = Model(targetPath)
        val loadedRecognizer = Recognizer(loadedModel, SAMPLE_RATE_HZ)
        model = loadedModel
        recognizer = loadedRecognizer
        loadedModelPath = targetPath
      }
    }

  suspend fun acceptPcm(pcmBytes: ByteArray, length: Int): VoskRecognitionOutput? =
    mutex.withLock {
      val rec = recognizer ?: return@withLock null
      if (length <= 0) return@withLock null

      val hasFinalSentence = rec.acceptWaveForm(pcmBytes, length)
      if (hasFinalSentence) {
        val finalText = parseFinalText(rec.result)
        if (finalText.isNotBlank()) {
          VoskRecognitionOutput(text = finalText, isFinal = true)
        } else {
          null
        }
      } else {
        val partialText = parsePartialText(rec.partialResult)
        if (partialText.isNotBlank()) {
          VoskRecognitionOutput(text = partialText, isFinal = false)
        } else {
          null
        }
      }
    }

  suspend fun flushFinalText(): String =
    mutex.withLock {
      val rec = recognizer ?: return@withLock ""
      parseFinalText(rec.finalResult)
    }

  suspend fun release() =
    mutex.withLock {
      releaseInternal()
    }

  private fun releaseInternal() {
    runCatching { recognizer?.close() }
    runCatching { model?.close() }
    recognizer = null
    model = null
    loadedModelPath = null
  }

  companion object {
    private const val SAMPLE_RATE_HZ = 16000.0f

    internal fun parseFinalText(jsonString: String?): String {
      if (jsonString.isNullOrBlank()) return ""
      return runCatching { JSONObject(jsonString).optString("text", "").trim() }.getOrDefault("")
    }

    internal fun parsePartialText(jsonString: String?): String {
      if (jsonString.isNullOrBlank()) return ""
      return runCatching { JSONObject(jsonString).optString("partial", "").trim() }.getOrDefault("")
    }
  }
}
