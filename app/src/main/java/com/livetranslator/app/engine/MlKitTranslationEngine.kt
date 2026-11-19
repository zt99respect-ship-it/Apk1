package com.livetranslator.app.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.livetranslator.app.data.model.SupportedLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TranslationOutcome(
  val translatedText: String,
  val resolvedSourceLangCode: String,
)

class MlKitTranslationEngine {

  private val mutex = Mutex()
  private var currentSourceLangCode: String? = null
  private var translator: Translator? = null
  private val languageIdentifier: LanguageIdentifier by lazy { LanguageIdentification.getClient() }

  suspend fun translateToArabic(
    text: String,
    sourceLangCode: String,
    skipArabic: Boolean,
  ): Result<TranslationOutcome> =
    mutex.withLock {
      withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
          return@withContext Result.success(TranslationOutcome("", sourceLangCode))
        }
        if (skipArabic && isMostlyArabic(trimmed)) {
          return@withContext Result.success(TranslationOutcome("", TranslateLanguage.ARABIC))
        }

        runCatching {
          val resolvedMlKitSource = resolveMlKitSourceLanguage(trimmed, sourceLangCode)
          if (skipArabic && resolvedMlKitSource == TranslateLanguage.ARABIC) {
            return@runCatching TranslationOutcome("", TranslateLanguage.ARABIC)
          }
          val activeTranslator = getOrCreateTranslator(resolvedMlKitSource)
          activeTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
          val translated = activeTranslator.translate(trimmed).awaitResult().trim()
          TranslationOutcome(
            translatedText = translated,
            resolvedSourceLangCode = resolvedMlKitSource,
          )
        }
      }
    }

  private suspend fun resolveMlKitSourceLanguage(text: String, configuredCode: String): String {
    if (configuredCode != SupportedLanguages.AUTO_CODE) {
      return SupportedLanguages.findByCode(configuredCode).mlKitLanguageCode
    }
    val identifiedTag =
      runCatching { languageIdentifier.identifyLanguage(text).awaitResult() }
        .getOrDefault("und")
    if (identifiedTag == LanguageIdentifier.UNDETERMINED_LANGUAGE_TAG || identifiedTag.isBlank()) {
      return TranslateLanguage.ENGLISH
    }
    if (identifiedTag.startsWith("ar")) {
      return TranslateLanguage.ARABIC
    }
    return TranslateLanguage.fromLanguageTag(identifiedTag) ?: TranslateLanguage.ENGLISH
  }

  private fun getOrCreateTranslator(mlKitSourceCode: String): Translator {
    val existing = translator
    if (existing != null && currentSourceLangCode == mlKitSourceCode) {
      return existing
    }
    runCatching { existing?.close() }

    val options =
      TranslatorOptions.Builder()
        .setSourceLanguage(mlKitSourceCode)
        .setTargetLanguage(TranslateLanguage.ARABIC)
        .build()

    return Translation.getClient(options).also {
      translator = it
      currentSourceLangCode = mlKitSourceCode
    }
  }

  suspend fun close() =
    mutex.withLock {
      runCatching { translator?.close() }
      runCatching { languageIdentifier.close() }
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
