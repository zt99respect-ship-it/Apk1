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
  val avgConfidence: Float = 1f,
)

class VoskSpeechEngine {

  private val mutex = Mutex()
  private var model: Model? = null
  private var recognizer: Recognizer? = null
  private var loadedModelPath: String? = null
  private var lastEmittedPartial: String = ""

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
        val loadedRecognizer =
          Recognizer(loadedModel, SAMPLE_RATE_HZ).apply {
            runCatching { setWords(true) }
            runCatching { setPartialWords(true) }
          }
        model = loadedModel
        recognizer = loadedRecognizer
        loadedModelPath = targetPath
        lastEmittedPartial = ""
      }
    }

  suspend fun acceptPcm(
    pcmBytes: ByteArray,
    length: Int,
    filterLowConfidence: Boolean = true,
  ): VoskRecognitionOutput? =
    mutex.withLock {
      val rec = recognizer ?: return@withLock null
      if (length <= 0) return@withLock null

      val hasFinalSentence = rec.acceptWaveForm(pcmBytes, length)
      if (hasFinalSentence) {
        lastEmittedPartial = ""
        val parsed = parseFinalResultWithConfidence(rec.result)
        if (parsed.text.isNotBlank() && (!filterLowConfidence || isReliableSpeech(parsed))) {
          parsed
        } else {
          null
        }
      } else {
        val partialText = parsePartialText(rec.partialResult)
        if (
          partialText.isNotBlank() &&
            partialText != lastEmittedPartial &&
            (!filterLowConfidence || !isObviousNoiseWord(partialText))
        ) {
          lastEmittedPartial = partialText
          VoskRecognitionOutput(text = partialText, isFinal = false)
        } else {
          null
        }
      }
    }

  suspend fun flushFinalText(filterLowConfidence: Boolean = true): String =
    mutex.withLock {
      val rec = recognizer ?: return@withLock ""
      lastEmittedPartial = ""
      val parsed = parseFinalResultWithConfidence(rec.finalResult)
      if (parsed.text.isNotBlank() && (!filterLowConfidence || isReliableSpeech(parsed))) {
        parsed.text
      } else {
        ""
      }
    }

  suspend fun resetPartialState() =
    mutex.withLock {
      lastEmittedPartial = ""
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
    lastEmittedPartial = ""
  }

  companion object {
    private const val SAMPLE_RATE_HZ = 16000.0f

    private val NOISE_WORDS =
      setOf("uh", "um", "ah", "eh", "oh", "hmm", "hm", "huh", "er", "the", "a", "an", "i")

    internal fun isObviousNoiseWord(text: String): Boolean {
      val normalized = text.trim().lowercase()
      if (normalized.length < 2) return true
      return NOISE_WORDS.contains(normalized)
    }

    internal fun isReliableSpeech(output: VoskRecognitionOutput): Boolean {
      val clean = output.text.trim()
      if (clean.length < 2 || isObviousNoiseWord(clean)) return false
      val words = clean.split(' ').filter { it.isNotBlank() }
      // Phonetic Arabic forced through English Vosk consistently yields low confidence (<0.66)
      val minConf = if (words.size <= 2) 0.68f else 0.60f
      return output.avgConfidence >= minConf
    }

    internal fun parseFinalText(jsonString: String?): String =
      parseFinalResultWithConfidence(jsonString).text

    internal fun parseFinalResultWithConfidence(jsonString: String?): VoskRecognitionOutput {
      if (jsonString.isNullOrBlank()) return VoskRecognitionOutput("", isFinal = true, avgConfidence = 0f)
      return runCatching {
          val obj = JSONObject(jsonString)
          val text = obj.optString("text", "").trim()
          val wordsArr = obj.optJSONArray("result")
          var avgConf = 1f
          if (wordsArr != null && wordsArr.length() > 0) {
            var sum = 0.0
            var count = 0
            for (i in 0 until wordsArr.length()) {
              val item = wordsArr.optJSONObject(i) ?: continue
              if (item.has("conf")) {
                sum += item.optDouble("conf", 1.0)
                count++
              }
            }
            if (count > 0) {
              avgConf = (sum / count).toFloat()
            }
          }
          VoskRecognitionOutput(text = text, isFinal = true, avgConfidence = avgConf)
        }
        .getOrDefault(VoskRecognitionOutput("", isFinal = true, avgConfidence = 0f))
    }

    internal fun parsePartialText(jsonString: String?): String {
      if (jsonString.isNullOrBlank()) return ""
      return runCatching { JSONObject(jsonString).optString("partial", "").trim() }.getOrDefault("")
    }
  }
}
