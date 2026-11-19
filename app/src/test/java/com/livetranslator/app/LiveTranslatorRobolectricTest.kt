package com.livetranslator.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.livetranslator.app.data.local.db.LiveTranslatorDatabase
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.data.local.prefs.SettingsDataStore
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.repository.TranslationRepository
import com.livetranslator.app.engine.DownloadHelper
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveTranslatorRobolectricTest {

  private lateinit var context: Context
  private lateinit var database: LiveTranslatorDatabase
  private lateinit var repository: TranslationRepository

  @Before
  fun setup() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, LiveTranslatorDatabase::class.java).build()
    repository =
      TranslationRepository(
        historyDao = database.translationHistoryDao(),
        sessionDao = database.translationSessionDao(),
        settingsDataStore = SettingsDataStore(context),
      )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun `app name resource matches instant translator in Arabic`() {
    assertEquals("ترجمة فورية", context.getString(R.string.app_name))
  }

  @Test
  fun `fresh install defaults to flexible auto source language and Cairo font`() = runTest {
    val settings = repository.getCurrentSettings()
    assertEquals(SupportedLanguages.AUTO_CODE, settings.sourceLanguageCode)
    assertEquals(4, settings.audioChunkSeconds)
    assertEquals(ArabicFontOption.CAIRO, settings.overlayFont)
    assertEquals(OverlayStylePreset.ORIGINAL, settings.overlayPreset)
  }

  @Test
  fun `sessions can be created updated and cleared`() = runTest {
    val sid =
      repository.createSession(
        startTimestamp = 1_000_000L,
        sourceLang = "auto",
        audioMode = "صوت النظام (الهاتف)",
      )
    repository.updateSessionProgress(
      sessionId = sid,
      endTimestamp = 1_060_000L,
      durationSeconds = 60L,
      translatedCount = 5,
      sourceLang = "en",
    )
    val latest = repository.latestSessionFlow.first()
    assertNotNull(latest)
    assertEquals(60L, latest?.durationSeconds)
    assertEquals(5, latest?.translatedCount)
    assertEquals("en", latest?.sourceLang)

    repository.clearAllSessionsAndHistory()
    assertEquals(0, repository.allSessionsFlow.first().size)
  }

  @Test
  fun `downloadHelper unzips and resolves Vosk model directory`() {
    val helper = DownloadHelper(context)
    val tempZip = File(context.cacheDir, "test_vosk_model.zip")
    val outDir = File(context.filesDir, "vosk_models/en_test").apply {
      deleteRecursively()
      mkdirs()
    }

    ZipOutputStream(FileOutputStream(tempZip)).use { zos ->
      zos.putNextEntry(ZipEntry("vosk-model-small-en-us-0.15/am/final.mdl"))
      zos.write(byteArrayOf(1, 2, 3))
      zos.closeEntry()
      zos.putNextEntry(ZipEntry("vosk-model-small-en-us-0.15/conf/mfcc.conf"))
      zos.write(byteArrayOf(4, 5, 6))
      zos.closeEntry()
    }

    helper.unzipSafely(tempZip, outDir)
    val resolved = helper.findValidVoskRoot(outDir)
    assertNotNull(resolved)
    assertEquals("vosk-model-small-en-us-0.15", resolved?.name)

    tempZip.delete()
    outDir.deleteRecursively()
  }

  @Test
  fun `purgeExpiredHistory deletes entries older than 30 days`() = runTest {
    val now = 1_800_000_000_000L
    val fortyDaysMs = 40L * 24L * 60L * 60L * 1000L
    val fiveDaysMs = 5L * 24L * 60L * 60L * 1000L

    database.translationHistoryDao().insert(
      TranslationHistoryEntity(
        timestamp = now - fortyDaysMs,
        sourceText = "Old stream line",
        translatedText = "سطر قديم",
        sourceLang = "en",
      )
    )
    database.translationHistoryDao().insert(
      TranslationHistoryEntity(
        timestamp = now - fiveDaysMs,
        sourceText = "Recent stream line",
        translatedText = "سطر حديث",
        sourceLang = "en",
      )
    )

    repository.purgeExpiredHistory(nowMs = now)
    val remaining = repository.observeHistory("").first()
    assertEquals(1, remaining.size)
    assertEquals("Recent stream line", remaining.first().sourceText)
  }

  @Test
  fun `overlay custom position saves and resets cleanly`() = runTest {
    val initial = repository.getCurrentSettings()
    assertEquals(-1, initial.overlayCustomX)
    assertEquals(-1, initial.overlayCustomY)

    repository.setOverlayCustomPosition(120, 480)
    val updated = repository.getCurrentSettings()
    assertEquals(120, updated.overlayCustomX)
    assertEquals(480, updated.overlayCustomY)

    repository.resetOverlayCustomPosition()
    val reset = repository.getCurrentSettings()
    assertEquals(-1, reset.overlayCustomX)
    assertEquals(-1, reset.overlayCustomY)
  }

  @Test
  fun `session manager keeps watermark panel, subtitle visibility, and repositioning decoupled`() {
    val manager = com.livetranslator.app.engine.TranslationSessionManager()
    val initial = manager.state.value
    assertEquals(false, initial.isControlPanelOpen)
    assertEquals(true, initial.isSubtitleVisible)
    assertEquals(false, initial.isRepositioningSubtitle)

    manager.toggleControlPanel()
    assertEquals(true, manager.state.value.isControlPanelOpen)
    assertEquals(false, manager.state.value.isRepositioningSubtitle)

    manager.setSubtitleVisible(false)
    assertEquals(false, manager.state.value.isSubtitleVisible)

    // Enabling repositioning automatically makes subtitle visible so user can drag it
    manager.setRepositioningSubtitle(true)
    assertEquals(true, manager.state.value.isRepositioningSubtitle)
    assertEquals(true, manager.state.value.isSubtitleVisible)

    manager.setRepositioningSubtitle(false)
    assertEquals(false, manager.state.value.isRepositioningSubtitle)

    manager.publishLivePartialTranslation(
      partialSource = "hello guys",
      liveArabicText = "أهلاً يا رفاق",
      sourceLang = "en",
    )
    assertEquals(true, manager.state.value.isLivePartial)
    assertEquals("أهلاً يا رفاق", manager.state.value.lastTranslatedText)
    assertEquals(0, manager.state.value.segmentsTranslated)
  }

  @Test
  fun `audio conditioning removes DC offset and boosts quiet PCM16 safely`() {
    // 100 samples of quiet speech (~200 amplitude) with +500 DC bias
    val sampleCount = 100
    val pcm = ByteArray(sampleCount * 2)
    for (i in 0 until sampleCount) {
      val s = (if (i % 2 == 0) 200 else -200) + 500
      pcm[i * 2] = (s and 0xFF).toByte()
      pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    val rawRms = com.livetranslator.app.engine.AudioCaptureManager.computePcm16Rms(pcm)
    val nextGain = com.livetranslator.app.engine.AudioCaptureManager.conditionPcm16InPlace(pcm, pcm.size, rawRms, 1.0)
    val conditionedRms = com.livetranslator.app.engine.AudioCaptureManager.computePcm16Rms(pcm)
    assertEquals(true, nextGain > 1.0)
    assertEquals(true, conditionedRms > 220.0)
  }

  @Test
  fun `mlkit fast script detection resolves unambiguous scripts in zero latency`() {
    assertEquals("ru", com.livetranslator.app.engine.MlKitTranslationEngine.detectScriptFast("Привет как дела"))
    assertEquals("ja", com.livetranslator.app.engine.MlKitTranslationEngine.detectScriptFast("こんにちは世界"))
    assertEquals("ko", com.livetranslator.app.engine.MlKitTranslationEngine.detectScriptFast("안녕하세요 여러분"))
    assertEquals("ar", com.livetranslator.app.engine.MlKitTranslationEngine.detectScriptFast("مرحبا بكم جميعا"))
    assertEquals(null, com.livetranslator.app.engine.MlKitTranslationEngine.detectScriptFast("Hello world"))
  }

  @Test
  fun `vosk reliability filter rejects filler noise and low confidence arabic guesses`() {
    assertEquals(
      false,
      com.livetranslator.app.engine.VoskSpeechEngine.isReliableSpeech(
        com.livetranslator.app.engine.VoskRecognitionOutput("uh", isFinal = true, avgConfidence = 0.95f)
      ),
    )
    assertEquals(
      false,
      com.livetranslator.app.engine.VoskSpeechEngine.isReliableSpeech(
        com.livetranslator.app.engine.VoskRecognitionOutput("sell him allah", isFinal = true, avgConfidence = 0.42f)
      ),
    )
    assertEquals(
      true,
      com.livetranslator.app.engine.VoskSpeechEngine.isReliableSpeech(
        com.livetranslator.app.engine.VoskRecognitionOutput("welcome back to the stream", isFinal = true, avgConfidence = 0.88f)
      ),
    )
  }

  @Test
  fun `mlkit noise and phonetic arabic filter catches transliterated arabic and ota rules`() {
    assertEquals(
      true,
      com.livetranslator.app.engine.MlKitTranslationEngine.isFilteredNoiseOrPhoneticArabic(
        text = "wallah ya shabab",
        skipArabic = true,
      ),
    )
    assertEquals(
      true,
      com.livetranslator.app.engine.MlKitTranslationEngine.isFilteredNoiseOrPhoneticArabic(
        text = "eh wallah",
        skipArabic = true,
        extraNoiseFilters = setOf("eh wallah"),
      ),
    )
    assertEquals(
      false,
      com.livetranslator.app.engine.MlKitTranslationEngine.isFilteredNoiseOrPhoneticArabic(
        text = "defeat the boss before timer ends",
        skipArabic = true,
      ),
    )
  }

  @Test
  fun `screen ocr meaningful word filter ignores pure symbols and numbers`() {
    assertEquals(false, com.livetranslator.app.engine.ScreenCaptureOcrEngine.containsMeaningfulWord("1234 : / +"))
    assertEquals(true, com.livetranslator.app.engine.ScreenCaptureOcrEngine.containsMeaningfulWord("Start Match"))
  }

  @Test
  fun `ota update manager parses valid delta manifest json and ignores invalid input`() {
    assertEquals(null, com.livetranslator.app.engine.OtaUpdateManager.parseManifestJson(""))
    assertEquals(null, com.livetranslator.app.engine.OtaUpdateManager.parseManifestJson("{not-json"))

    val parsed =
      com.livetranslator.app.engine.OtaUpdateManager.parseManifestJson(
        com.livetranslator.app.engine.OtaUpdateManager.sampleManifestJsonTemplate()
      )
    assertNotNull(parsed)
    assertEquals(2, parsed?.patchVersion)
    assertEquals("لعب رائع", parsed?.glossaryAdditions?.get("gg"))
    assertEquals(true, parsed?.noiseFilterAdditions?.contains("eh wallah"))
    assertEquals("تحديث جديد متاح لـ ترجمة فورية", parsed?.notificationTitle)
  }
}
