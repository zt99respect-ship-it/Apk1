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

enum class OverlayFontWeight(val key: String, val label: String) {
  NORMAL("normal", "عادي"),
  BOLD("bold", "عريض");

  companion object {
    fun fromKey(key: String?): OverlayFontWeight =
      entries.firstOrNull { it.key == key } ?: BOLD
  }
}

enum class OverlayStylePreset(
  val key: String,
  val displayName: String,
  val font: ArabicFontOption,
  val weight: OverlayFontWeight,
  val fontSizeSp: Float,
  val textColorArgb: Long,
  val boxColorArgb: Long,
  val boxOpacity: Float,
) {
  ORIGINAL(
    key = "original",
    displayName = "الأصلي",
    font = ArabicFontOption.CAIRO,
    weight = OverlayFontWeight.BOLD,
    fontSizeSp = 24f,
    textColorArgb = 0xFFFFFFFF,
    boxColorArgb = 0xFF09090B,
    boxOpacity = 0.86f,
  ),
  CINEMA(
    key = "cinema",
    displayName = "سينما",
    font = ArabicFontOption.IBM_PLEX_SANS_ARABIC,
    weight = OverlayFontWeight.BOLD,
    fontSizeSp = 28f,
    textColorArgb = 0xFFFACC15,
    boxColorArgb = 0xFF140F24,
    boxOpacity = 0.72f,
  ),
  CALM(
    key = "calm",
    displayName = "هادئ",
    font = ArabicFontOption.NOTO_NASKH_ARABIC,
    weight = OverlayFontWeight.NORMAL,
    fontSizeSp = 21f,
    textColorArgb = 0xFFE4E4E7,
    boxColorArgb = 0xFF181826,
    boxOpacity = 0.60f,
  );

  companion object {
    fun fromKey(key: String?): OverlayStylePreset =
      entries.firstOrNull { it.key == key } ?: ORIGINAL
  }
}

enum class AudioSourcePreference(val key: String, val displayName: String, val subtitle: String) {
  SYSTEM_PLAYBACK(
    key = "system_playback",
    displayName = "صوت النظام (يوتيوب / تطبيقات)",
    subtitle = "يلتقط صوت الفيديو والبثوث من داخل الهاتف مباشرة.",
  ),
  MICROPHONE(
    key = "microphone",
    displayName = "الميكروفون",
    subtitle = "يلتقط الكلام الخارجي أو السماعات عبر ميكروفون الهاتف.",
  );

  companion object {
    fun fromKey(key: String?): AudioSourcePreference =
      entries.firstOrNull { it.key == key } ?: SYSTEM_PLAYBACK
  }
}

enum class SpeechEnginePreference(val key: String, val displayName: String, val subtitle: String) {
  FLEXIBLE_AUTO(
    key = "flexible_auto",
    displayName = "مرن (التعرّف التلقائي + Vosk)",
    subtitle = "يدمج محرك أندرويد السحابي/المرن وكشف اللغة التلقائي مع Vosk كبديل فوري.",
  ),
  VOSK_LOCAL(
    key = "vosk_local",
    displayName = "محلي مباشر (Vosk)",
    subtitle = "يعالج الصوت محلياً بالكامل بدون إنترنت لنموذج اللغة المختار.",
  );

  companion object {
    fun fromKey(key: String?): SpeechEnginePreference =
      entries.firstOrNull { it.key == key } ?: FLEXIBLE_AUTO
  }
}

enum class ArabicFontOption(
  val key: String,
  val displayName: String,
  val shortLabel: String,
  val arabicPreview: String,
) {
  CAIRO(
    key = "cairo",
    displayName = "Cairo",
    shortLabel = "Cairo",
    arabicPreview = "خط القاهرة — واضح وعصري للترجمة الفورية",
  ),
  IBM_PLEX_SANS_ARABIC(
    key = "ibm_plex_sans_arabic",
    displayName = "IBM Plex Sans Arabic",
    shortLabel = "IBM Plex Sans",
    arabicPreview = "خط IBM Plex — تباين عالٍ ومريح للعين",
  ),
  NOTO_NASKH_ARABIC(
    key = "noto_naskh_arabic",
    displayName = "Noto Naskh Arabic",
    shortLabel = "Noto Naskh",
    arabicPreview = "خط النسخ العربي — قراءة سلسة وواضحة",
  ),
  TAJAWAL(
    key = "tajawal",
    displayName = "Tajawal",
    shortLabel = "Tajawal",
    arabicPreview = "خط تجوال — خفيف وأنيق للفيديوهات",
  );

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
  val speechLocaleTag: String = code,
) {
  val isAuto: Boolean
    get() = code == SupportedLanguages.AUTO_CODE
}

object SupportedLanguages {
  const val AUTO_CODE = "auto"

  val autoLanguage =
    SourceLanguage(
      code = AUTO_CODE,
      displayName = "تلقائي / مرن (حسب الصوت)",
      voskModelName = "vosk-model-small-en-us-0.15",
      voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
      mlKitLanguageCode = TranslateLanguage.ENGLISH,
      approxSizeMb = 40,
      speechLocaleTag = "en-US",
    )

  val explicitLanguages: List<SourceLanguage> =
    listOf(
      SourceLanguage(
        code = "en",
        displayName = "الإنجليزية",
        voskModelName = "vosk-model-small-en-us-0.15",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
        mlKitLanguageCode = TranslateLanguage.ENGLISH,
        approxSizeMb = 40,
        speechLocaleTag = "en-US",
      ),
      SourceLanguage(
        code = "tr",
        displayName = "التركية",
        voskModelName = "vosk-model-small-tr-0.3",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip",
        mlKitLanguageCode = TranslateLanguage.TURKISH,
        approxSizeMb = 35,
        speechLocaleTag = "tr-TR",
      ),
      SourceLanguage(
        code = "es",
        displayName = "الإسبانية",
        voskModelName = "vosk-model-small-es-0.42",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",
        mlKitLanguageCode = TranslateLanguage.SPANISH,
        approxSizeMb = 39,
        speechLocaleTag = "es-ES",
      ),
      SourceLanguage(
        code = "fr",
        displayName = "الفرنسية",
        voskModelName = "vosk-model-small-fr-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.FRENCH,
        approxSizeMb = 41,
        speechLocaleTag = "fr-FR",
      ),
      SourceLanguage(
        code = "de",
        displayName = "الألمانية",
        voskModelName = "vosk-model-small-de-0.15",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",
        mlKitLanguageCode = TranslateLanguage.GERMAN,
        approxSizeMb = 45,
        speechLocaleTag = "de-DE",
      ),
      SourceLanguage(
        code = "ru",
        displayName = "الروسية",
        voskModelName = "vosk-model-small-ru-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.RUSSIAN,
        approxSizeMb = 45,
        speechLocaleTag = "ru-RU",
      ),
      SourceLanguage(
        code = "ja",
        displayName = "اليابانية",
        voskModelName = "vosk-model-small-ja-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.JAPANESE,
        approxSizeMb = 48,
        speechLocaleTag = "ja-JP",
      ),
      SourceLanguage(
        code = "ko",
        displayName = "الكورية",
        voskModelName = "vosk-model-small-ko-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-ko-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.KOREAN,
        approxSizeMb = 82,
        speechLocaleTag = "ko-KR",
      ),
      SourceLanguage(
        code = "zh",
        displayName = "الصينية",
        voskModelName = "vosk-model-small-cn-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.CHINESE,
        approxSizeMb = 42,
        speechLocaleTag = "zh-CN",
      ),
      SourceLanguage(
        code = "pt",
        displayName = "البرتغالية",
        voskModelName = "vosk-model-small-pt-0.3",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",
        mlKitLanguageCode = TranslateLanguage.PORTUGUESE,
        approxSizeMb = 31,
        speechLocaleTag = "pt-PT",
      ),
      SourceLanguage(
        code = "it",
        displayName = "الإيطالية",
        voskModelName = "vosk-model-small-it-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.ITALIAN,
        approxSizeMb = 48,
        speechLocaleTag = "it-IT",
      ),
      SourceLanguage(
        code = "hi",
        displayName = "الهندية",
        voskModelName = "vosk-model-small-hi-0.22",
        voskDownloadUrl = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
        mlKitLanguageCode = TranslateLanguage.HINDI,
        approxSizeMb = 42,
        speechLocaleTag = "hi-IN",
      ),
    )

  val all: List<SourceLanguage> = listOf(autoLanguage) + explicitLanguages

  fun findByCode(code: String): SourceLanguage =
    all.firstOrNull { it.code == code } ?: autoLanguage

  fun resolveVoskLanguage(code: String): SourceLanguage =
    if (code == AUTO_CODE) explicitLanguages.first() else explicitLanguages.firstOrNull { it.code == code }
      ?: explicitLanguages.first()
}

data class UserSettings(
  val onboardingCompleted: Boolean = false,
  val accountLabel: String = "مستخدم LiveTranslator",
  val sourceLanguageCode: String = SupportedLanguages.AUTO_CODE,
  val skipArabic: Boolean = true,
  val audioChunkSeconds: Int = 4,
  val autoRestartOnBoot: Boolean = false,
  val audioSourcePreference: AudioSourcePreference = AudioSourcePreference.SYSTEM_PLAYBACK,
  val speechEnginePreference: SpeechEnginePreference = SpeechEnginePreference.FLEXIBLE_AUTO,
  val overlayPosition: OverlayPosition = OverlayPosition.BOTTOM,
  val overlayBackground: OverlayBackground = OverlayBackground.GLASS,
  val overlayPreset: OverlayStylePreset = OverlayStylePreset.ORIGINAL,
  val overlayFont: ArabicFontOption = ArabicFontOption.CAIRO,
  val overlayFontWeight: OverlayFontWeight = OverlayFontWeight.BOLD,
  val overlayFontSizeSp: Float = 24f,
  val overlayTextColorArgb: Long = 0xFFFFFFFF,
  val overlayBoxColorArgb: Long = 0xFF09090B,
  val overlayBoxOpacity: Float = 0.86f,
  val overlayCornerRadiusDp: Float = 22f,
)
