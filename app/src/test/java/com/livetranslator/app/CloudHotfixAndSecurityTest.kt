package com.livetranslator.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.livetranslator.app.data.remote.CryptoPasswordHelper
import com.livetranslator.app.data.remote.HotfixProfileDoc
import com.livetranslator.app.engine.MlKitTranslationEngine
import com.livetranslator.app.engine.OtaUpdateManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudHotfixAndSecurityTest {

  @Test
  fun `salted password hashing verifies exact match and rejects mismatch`() {
    val salt = CryptoPasswordHelper.generateSaltHex()
    val hash = CryptoPasswordHelper.hashPassword("mySecret123", salt)
    assertEquals(64, hash.length)
    assertTrue(CryptoPasswordHelper.verifyPassword("mySecret123", salt, hash))
    assertFalse(CryptoPasswordHelper.verifyPassword("wrongPassword", salt, hash))
  }

  @Test
  fun `secret user code is unique masked by default and has expected format`() {
    val c1 = CryptoPasswordHelper.generateUniqueUserCode()
    val c2 = CryptoPasswordHelper.generateUniqueUserCode()
    assertTrue(c1.startsWith("TR-"))
    assertEquals(12, c1.length)
    assertNotEquals(c1, c2)

    val masked = CryptoPasswordHelper.maskSecretCode(c1)
    assertTrue(masked.contains("••••"))
    assertTrue(masked.endsWith(c1.takeLast(2)))
  }

  @Test
  fun `stageCloudHotfix marks incremental patch ready and applies without APK reinstall`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val app = context.applicationContext as LiveTranslatorApp
    val manager = OtaUpdateManager(context, app.repository, MlKitTranslationEngine())

    manager.stageCloudHotfix(
      HotfixProfileDoc(
        patchVersion = 5,
        patchNotes = "تصحيح ساخن لاختبار الترجمة",
        mandatoryHotfix = true,
        glossaryAdditions = mapOf("clutch" to "لقطة حاسمة"),
        noiseFilterAdditions = listOf("eh wallah"),
      ),
      notifyUser = false,
    )

    assertTrue(manager.state.value.hasPendingPatchUpdate)
    assertTrue(manager.state.value.isMandatoryHotfix)
    assertEquals(5, manager.state.value.availableManifest?.patchVersion)

    val appliedResult = manager.applyAvailableManifest()
    assertTrue(appliedResult.isSuccess)
    assertEquals(5, appliedResult.getOrNull())
    assertFalse(manager.state.value.hasPendingPatchUpdate)
    assertEquals(5, manager.state.value.currentPatchVersion)
  }
}
