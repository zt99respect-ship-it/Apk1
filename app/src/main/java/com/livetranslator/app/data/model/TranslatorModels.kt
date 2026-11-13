package com.livetranslator.app.data.model

import com.google.mlkit.nl.translate.TranslateLanguage

enum class OverlayPosition(val label: String) {
  TOP("أعلى"),
  CENTER("وسط"),
  BOTTOM("أسفل"),
}

enum class OverlayBackground(val label: String) {
  NONE("شفاف"),
  GLASS("زجاجي"),
  SOLID("داكن"),
}

enum class ArabicFontOption(
  val key: String,
  val displayName: String,
  val arabicPreview: String,
) {
  CAIRO("cairo", "القاهرة (Cairo)", "خط القاهرة — واضح وعصري للترجمة"),
  TAJAWAL("tajawal", "تجوال (Tajawal)", "خط تجوال — مريح للعين في البثوث"),
  ALMARAI("almarai", "المراعي (Almarai)", "خط المراعي — أنيق ومتوازن"),
  AMIRI("amiri", "أميري (Amiri)", "خط أميري — طابع نسخ كلاسيكي"),
  REEM_KUFI("reem_kufi", "ريم كوفي (Reem Kufi)", "خط ريم كوفي — هندسي مميز"),
  NOTO_KUFI_ARABIC("noto_kufi_arabic", "نوتو كوفي (Noto Kufi)", "خط نوتو كوفي — قراءة سريعة");

  companion object {
    fun fromKey(key: String?): ArabicFontOption =
      entries.firstOrNull { it.key == key } ?: CAIRO
  }
}

data class SourceLanguage(
  val code: String,
  val displayName: String,
  val voskModelName: String,
  val voskDownloadUrl: String,
  val mlKitLanguageCode: String,
  val approxSizeMb: Int,
)

object SupportedLanguages {
  val all: List<SourceLanguage> =
    listOf(
      SourceLanguage(
        code = "en",
        displayName = "الإنجليزية",
        voskModelName = "vosk-model-small-en-us-0.15",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        mlKitLanguageCode = TranslateLanguage.ENGLISH,
        approxSizeMb = 40,
      ),
      SourceLanguage(
        code = "tr",
        displayName = "التركية",
        voskModelName = "vosk-model-small-tr-0.3",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
        mlKitLanguageCode = TranslateLanguage.TURKISH,
        approxSizeMb = 35,
      ),
      SourceLanguage(
        code = "es",
        displayName = "الإسبانية",
        voskModelName = "vosk-model-small-es-0.42",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
        mlKitLanguageCode = TranslateLanguage.SPANISH,
        approxSizeMb = 39,
      ),
      SourceLanguage(
        code = "fr",
        displayName = "الفرنسية",
        voskModelName = "vosk-model-small-fr-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.FRENCH,
        approxSizeMb = 41,
      ),
      SourceLanguage(
        code = "de",
        displayName = "الألمانية",
        voskModelName = "vosk-model-small-de-0.15",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
        mlKitLanguageCode = TranslateLanguage.GERMAN,
        approxSizeMb = 45,
      ),
      SourceLanguage(
        code = "ru",
        displayName = "الروسية",
        voskModelName = "vosk-model-small-ru-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.RUSSIAN,
        approxSizeMb = 45,
      ),
      SourceLanguage(
        code = "ja",
        displayName = "اليابانية",
        voskModelName = "vosk-model-small-ja-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.JAPANESE,
        approxSizeMb = 48,
      ),
      SourceLanguage(
        code = "ko",
        displayName = "الكورية",
        voskModelName = "vosk-model-small-ko-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.KOREAN,
        approxSizeMb = 82,
      ),
      SourceLanguage(
        code = "zh",
        displayName = "الصينية",
        voskModelName = "vosk-model-small-cn-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.CHINESE,
        approxSizeMb = 42,
      ),
      SourceLanguage(
        code = "pt",
        displayName = "البرتغالية",
        voskModelName = "vosk-model-small-pt-0.3",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
        mlKitLanguageCode = TranslateLanguage.PORTUGUESE,
        approxSizeMb = 31,
      ),
      SourceLanguage(
        code = "it",
        displayName = "الإيطالية",
        voskModelName = "vosk-model-small-it-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.ITALIAN,
        approxSizeMb = 48,
      ),
      SourceLanguage(
        code = "hi",
        displayName = "الهندية",
        voskModelName = "vosk-model-small-hi-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.HINDI,
        approxSizeMb = 42,
      ),
    )

  fun findByCode(code: String): SourceLanguage =
    all.firstOrNull { it.code == code } ?: all.first()
}

data class UserSettings(
  val onboardingCompleted: Boolean = false,
  val sourceLanguageCode: String = "en",
  val skipArabic: Boolean = true,
  val audioChunkSeconds: Int = 4,
  val autoRestartOnBoot: Boolean = false,
  val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
  val overlayBackground: OverlayBackground = OverlayBackground.GLASS,
  val overlayFontSizeSp: Float = 20f,
  val overlayTextColorArgb: Long = 0xFFF3E8FF,
  val overlayFont: ArabicFontOption = ArabicFontOption.CAIRO,
  val overlayCornerRadiusDp: Float = 18f,
)
