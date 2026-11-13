package com.livetranslator.app.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.livetranslator.app.data.model.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MlKitTranslationEngine {

  private val mutex = Mutex()
  private var currentSourceLangCode: String? = null
  private var translator: Translator? = null

  suspend fun translateToArabic(
    text: String,
    sourceLangCode: String,
    skipArabic: Boolean,
  ): Result<String> =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext Result.success("")
        if (skipArabic && isMostlyArabic(trimmed)) {
          return@withContext Result.success("")
        }

        runCatching {
          val activeTranslator = getOrCreateTranslator(sourceLangCode)
          activeTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
          activeTranslator.translate(trimmed).awaitResult().trim()
        }
      }
    }

  private fun getOrCreateTranslator(sourceLangCode: String): Translator {
    val existing = translator
    if (existing != null && currentSourceLangCode == sourceLangCode) {
      return existing
    }
    runCatching { existing?.close() }

    val mlKitSource = SupportedLanguages.findByCode(sourceLangCode).mlKitLanguageCode
    val options =
      TranslatorOptions.Builder()
        .setSourceLanguage(mlKitSource)
        .setTargetLanguage(TranslateLanguage.ARABIC)
        .build()

    return Translation.getClient(options).also {
      translator = it
      currentSourceLangCode = sourceLangCode
    }
  }

  suspend fun close() =
    mutex.withLock {
      runCatching { translator?.close() }
      translator = null
      currentSourceLangCode = null
    }

  companion object {
    fun isMostlyArabic(text: String): Boolean {
      var arabicChars = 0
      var letterChars = 0
      for (ch in text) {
        if (ch.isLetter()) {
          letterChars++
          val code = ch.code
          if (code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF) {
            arabicChars++
          }
        }
      }
      return letterChars > 0 && (arabicChars.toFloat() / letterChars.toFloat()) >= 0.55f
    }
  }
}
