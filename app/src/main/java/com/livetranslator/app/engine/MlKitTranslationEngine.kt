package com.livetranslator.app.engine

import android.util.LruCache
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.livetranslator.app.data.model.SupportedLanguages
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class TranslationOutcome(
  val translatedText: String,
  val resolvedSourceLangCode: String,
)

class MlKitTranslationEngine {

  private val translators = ConcurrentHashMap<String, Translator>()
  private val translatorMutexes = ConcurrentHashMap<String, Mutex>()
  private val warmedSourceLangs: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val translationCache = LruCache<String, TranslationOutcome>(192)

  // Dynamic incremental OTA glossary & noise filter rules (updated without APK reinstall)
  private val dynamicGlossary = ConcurrentHashMap<String, String>()
  private val dynamicNoiseFilters: MutableSet<String> = ConcurrentHashMap.newKeySet()

  @Volatile private var stickyAutoLangCode: String = TranslateLanguage.ENGLISH

  private val languageIdentifier: LanguageIdentifier by lazy {
    LanguageIdentification.getClient(
      LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.55f).build()
    )
  }

  fun updateDynamicRules(glossary: Map<String, String>, noiseFilters: List<String>) {
    dynamicGlossary.clear()
    glossary.forEach { (k, v) ->
      val key = k.trim().lowercase()
      val value = v.trim()
      if (key.isNotBlank() && value.isNotBlank()) {
        dynamicGlossary[key] = value
      }
    }
    dynamicNoiseFilters.clear()
    noiseFilters.forEach { entry ->
      val clean = entry.trim().lowercase()
      if (clean.isNotBlank()) dynamicNoiseFilters.add(clean)
    }
    translationCache.evictAll()
  }

  suspend fun prewarm(sourceLangCode: String) =
    withContext(Dispatchers.IO) {
      val initialLang =
        if (sourceLangCode == SupportedLanguages.AUTO_CODE) {
          stickyAutoLangCode
        } else {
          SupportedLanguages.findByCode(sourceLangCode).mlKitLanguageCode
        }
      stickyAutoLangCode = initialLang
      runCatching {
        ensurePairDownloadedCached(initialLang)
        getOrCreateTranslator(initialLang)
      }
    }

  suspend fun translateLiveFast(
    text: String,
    sourceLangCode: String,
    skipArabic: Boolean,
    hintLangCode: String? = null,
  ): Result<TranslationOutcome> =
    withContext(Dispatchers.IO) {
      val trimmed = text.trim()
      if (trimmed.length < 2) {
        return@withContext Result.success(TranslationOutcome("", sourceLangCode))
      }
      if (skipArabic && isMostlyArabic(trimmed)) {
        return@withContext Result.success(TranslationOutcome("", TranslateLanguage.ARABIC))
      }
      if (isFilteredNoiseOrPhoneticArabic(trimmed, skipArabic, dynamicNoiseFilters)) {
        return@withContext Result.success(TranslationOutcome("", sourceLangCode))
      }

      val normalizedKey = trimmed.lowercase()
      dynamicGlossary[normalizedKey]?.let { directArabic ->
        return@withContext Result.success(TranslationOutcome(directArabic, "glossary"))
      }

      runCatching {
        val resolvedMlKitSource = resolveMlKitSourceLanguage(trimmed, sourceLangCode, hintLangCode)
        if (skipArabic && resolvedMlKitSource == TranslateLanguage.ARABIC) {
          return@runCatching TranslationOutcome("", TranslateLanguage.ARABIC)
        }

        val cacheKey = "$resolvedMlKitSource|$trimmed"
        translationCache.get(cacheKey)?.let { cached ->
          return@runCatching cached
        }

        val langMutex = translatorMutexes.getOrPut(resolvedMlKitSource) { Mutex() }
        langMutex.withLock {
          translationCache.get(cacheKey)?.let { return@withLock it }
          ensurePairDownloadedCached(resolvedMlKitSource)
          val activeTranslator = getOrCreateTranslator(resolvedMlKitSource)
          val rawTranslated = activeTranslator.translate(trimmed).awaitResult().trim()
          val polished = applyGlossaryReplacements(rawTranslated, trimmed, dynamicGlossary)
          val outcome =
            TranslationOutcome(
              translatedText = polished,
              resolvedSourceLangCode = resolvedMlKitSource,
            )
          if (polished.isNotBlank()) {
            translationCache.put(cacheKey, outcome)
          }
          outcome
        }
      }
    }

  suspend fun translateToArabic(
    text: String,
    sourceLangCode: String,
    skipArabic: Boolean,
    hintLangCode: String? = null,
  ): Result<TranslationOutcome> = translateLiveFast(text, sourceLangCode, skipArabic, hintLangCode)

  private suspend fun ensurePairDownloadedCached(mlKitSourceCode: String) {
    if (warmedSourceLangs.contains(mlKitSourceCode)) return

    val manager = RemoteModelManager.getInstance()
    val srcModel = TranslateRemoteModel.Builder(mlKitSourceCode).build()
    val arModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()

    val srcReady = runCatching { manager.isModelDownloaded(srcModel).awaitResult() }.getOrDefault(false)
    val arReady = runCatching { manager.isModelDownloaded(arModel).awaitResult() }.getOrDefault(false)
    if (!srcReady || !arReady) {
      val activeTranslator = getOrCreateTranslator(mlKitSourceCode)
      activeTranslator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
    }
    warmedSourceLangs.add(mlKitSourceCode)
  }

  private suspend fun resolveMlKitSourceLanguage(
    text: String,
    configuredCode: String,
    hintLangCode: String?,
  ): String {
    if (configuredCode != SupportedLanguages.AUTO_CODE) {
      val explicit = SupportedLanguages.findByCode(configuredCode).mlKitLanguageCode
      stickyAutoLangCode = explicit
      return explicit
    }

    detectScriptFast(text)?.let { scriptLang ->
      stickyAutoLangCode = scriptLang
      return scriptLang
    }

    val normalizedHint =
      hintLangCode
        ?.takeIf { it != SupportedLanguages.AUTO_CODE }
        ?.let { SupportedLanguages.findByCode(it).mlKitLanguageCode }

    val wordCount = text.count { it == ' ' } + 1
    if (text.length < 12 && wordCount < 3) {
      return normalizedHint ?: stickyAutoLangCode
    }

    val candidates =
      runCatching { languageIdentifier.identifyPossibleLanguages(text).awaitResult() }
        .getOrDefault(emptyList())

    val topCandidate =
      candidates.firstOrNull { cand ->
        cand.languageTag != LanguageIdentifier.UNDETERMINED_LANGUAGE_TAG &&
          (cand.languageTag.startsWith("ar") || TranslateLanguage.fromLanguageTag(cand.languageTag) != null)
      }

    if (topCandidate == null) {
      return normalizedHint ?: stickyAutoLangCode
    }

    if (topCandidate.languageTag.startsWith("ar") && topCandidate.confidence >= 0.65f) {
      return TranslateLanguage.ARABIC
    }

    val mlKitTag = TranslateLanguage.fromLanguageTag(topCandidate.languageTag)
      ?: return normalizedHint ?: stickyAutoLangCode

    val requiredConfidence = if (mlKitTag == stickyAutoLangCode) 0.45f else 0.72f
    return if (topCandidate.confidence >= requiredConfidence) {
      stickyAutoLangCode = mlKitTag
      mlKitTag
    } else {
      normalizedHint ?: stickyAutoLangCode
    }
  }

  private fun getOrCreateTranslator(mlKitSourceCode: String): Translator {
    return translators.getOrPut(mlKitSourceCode) {
      val options =
        TranslatorOptions.Builder()
          .setSourceLanguage(mlKitSourceCode)
          .setTargetLanguage(TranslateLanguage.ARABIC)
          .build()
      Translation.getClient(options)
    }
  }

  fun resetStickyLanguage(sourceLangCode: String) {
    stickyAutoLangCode =
      if (sourceLangCode == SupportedLanguages.AUTO_CODE) TranslateLanguage.ENGLISH
      else SupportedLanguages.findByCode(sourceLangCode).mlKitLanguageCode
  }

  suspend fun close() =
    withContext(Dispatchers.IO) {
      translators.values.forEach { runCatching { it.close() } }
      translators.clear()
      warmedSourceLangs.clear()
      translationCache.evictAll()
      runCatching { languageIdentifier.close() }
    }

  companion object {
    private val BUILTIN_NOISE_PHRASES =
      setOf(
        "uh", "um", "ah", "eh", "oh", "hmm", "huh", "the", "a", "an",
        "ya", "wallah", "yalla", "habibi", "inshallah", "mashallah", "subhanallah",
        "alhamdulillah", "astaghfirullah", "bismillah", "salam", "assalamu",
      )

    internal fun isFilteredNoiseOrPhoneticArabic(
      text: String,
      skipArabic: Boolean,
      extraNoiseFilters: Set<String> = emptySet(),
    ): Boolean {
      val lower = text.trim().lowercase()
      if (lower.length < 2) return true
      if (BUILTIN_NOISE_PHRASES.contains(lower) || extraNoiseFilters.contains(lower)) {
        return true
      }
      if (!skipArabic) return false

      // Transliterated Arabic words often forced by Latin STT models
      val tokens = lower.split(' ').filter { it.isNotBlank() }
      val romanArabicHits =
        tokens.count { tok ->
          tok in setOf("wallah", "yalla", "ya", "habibi", "shabab", "khallas", "ana", "enta", "allah", "inshallah")
        }
      return tokens.isNotEmpty() && romanArabicHits * 2 >= tokens.size
    }

    private fun applyGlossaryReplacements(
      translatedArabic: String,
      originalSource: String,
      glossary: Map<String, String>,
    ): String {
      if (glossary.isEmpty()) return translatedArabic
      val exact = glossary[originalSource.trim().lowercase()]
      return exact ?: translatedArabic
    }

    internal fun detectScriptFast(text: String): String? {
      var arabic = 0
      var cyrillic = 0
      var kana = 0
      var hangul = 0
      var cjk = 0
      var devanagari = 0
      var letters = 0

      for (ch in text) {
        if (!ch.isLetter()) continue
        letters++
        val code = ch.code
        when {
          code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF -> arabic++
          code in 0x0400..0x04FF -> cyrillic++
          code in 0x3040..0x309F || code in 0x30A0..0x30FF -> kana++
          code in 0xAC00..0xD7AF || code in 0x1100..0x11FF -> hangul++
          code in 0x4E00..0x9FFF -> cjk++
          code in 0x0900..0x097F -> devanagari++
        }
      }
      if (letters == 0) return null
      val half = letters * 0.45f
      return when {
        arabic >= half -> TranslateLanguage.ARABIC
        kana > 0 -> TranslateLanguage.JAPANESE
        hangul >= half -> TranslateLanguage.KOREAN
        cjk >= half -> TranslateLanguage.CHINESE
        cyrillic >= half -> TranslateLanguage.RUSSIAN
        devanagari >= half -> TranslateLanguage.HINDI
        else -> null
      }
    }

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
