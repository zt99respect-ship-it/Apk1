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
  fun `app name resource matches LiveTranslator`() {
    assertEquals("LiveTranslator", context.getString(R.string.app_name))
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
}
