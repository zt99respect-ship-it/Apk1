package com.livetranslator.controller

import com.livetranslator.controller.data.AdminOperatorVerifier
import com.livetranslator.controller.data.AppControlConfigDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerAdminTest {

  @Test
  fun `operator credentials accept exact RT user and password while rejecting others`() {
    assertTrue(AdminOperatorVerifier.verifyOperatorCredentials("RT", "mkjbmkjb"))
    assertTrue(AdminOperatorVerifier.verifyOperatorCredentials(" RT ", "mkjbmkjb"))
    assertFalse(AdminOperatorVerifier.verifyOperatorCredentials("rt", "mkjbmkjb"))
    assertFalse(AdminOperatorVerifier.verifyOperatorCredentials("RT", "wrong"))
    assertFalse(AdminOperatorVerifier.verifyOperatorCredentials("admin", "mkjbmkjb"))
  }

  @Test
  fun `default AppControlConfigDoc keeps all live modes unlocked`() {
    val cfg = AppControlConfigDoc()
    assertFalse(cfg.maintenanceEnabled)
    assertTrue(cfg.allowAudioLive)
    assertTrue(cfg.allowScreenOcr)
    assertTrue(cfg.allowOverlayBubble)
  }

  @Test
  fun `bootstrap admin email matches verified owner account`() {
    assertEquals("mfr7h.carectar@gmail.com", AdminOperatorVerifier.ADMIN_BOOTSTRAP_EMAIL)
  }
}
