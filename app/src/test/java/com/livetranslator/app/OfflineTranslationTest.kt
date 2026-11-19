package com.livetranslator.app

import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.engine.AudioCaptureManager
import com.livetranslator.app.engine.MlKitTranslationEngine
import com.livetranslator.app.engine.VoskSpeechEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineTranslationTest {

  @Test
  fun `parseFinalText extracts recognized text from Vosk JSON`() {
    val json = """{"text": "hello world welcome to the stream"}"""
    assertEquals("hello world welcome to the stream", VoskSpeechEngine.parseFinalText(json))
    assertEquals("", VoskSpeechEngine.parseFinalText("""{"text": ""}"""))
    assertEquals("", VoskSpeechEngine.parseFinalText("invalid json"))
  }

  @Test
  fun `parsePartialText extracts partial text from Vosk JSON`() {
    val json = """{"partial": "hello every"}"""
    assertEquals("hello every", VoskSpeechEngine.parsePartialText(json))
    assertEquals("", VoskSpeechEngine.parsePartialText(null))
  }

  @Test
  fun `isMostlyArabic detects Arabic vs English text`() {
    assertTrue(MlKitTranslationEngine.isMostlyArabic("مرحباً بكم في البث المباشر"))
    assertFalse(MlKitTranslationEngine.isMostlyArabic("Hello everyone welcome to the live stream"))
  }

  @Test
  fun `supported languages starts with flexible auto detection`() {
    assertEquals(SupportedLanguages.AUTO_CODE, SupportedLanguages.all.first().code)
    assertEquals("en", SupportedLanguages.resolveVoskLanguage(SupportedLanguages.AUTO_CODE).code)
  }

  @Test
  fun `arabic fonts include exact four required families and presets`() {
    assertEquals(
      listOf(
        ArabicFontOption.CAIRO,
        ArabicFontOption.IBM_PLEX_SANS_ARABIC,
        ArabicFontOption.NOTO_NASKH_ARABIC,
        ArabicFontOption.TAJAWAL,
      ),
      ArabicFontOption.entries,
    )
    assertEquals(3, OverlayStylePreset.entries.size)
  }

  @Test
  fun `computePcm16Rms returns expected amplitude`() {
    val silent = ByteArray(100)
    assertEquals(0.0, AudioCaptureManager.computePcm16Rms(silent), 0.001)

    val loud =
      ByteArray(4).apply {
        this[0] = 0x00
        this[1] = 0x10
        this[2] = 0x00
        this[3] = 0x10
      }
    assertEquals(4096.0, AudioCaptureManager.computePcm16Rms(loud), 0.5)
  }
}
