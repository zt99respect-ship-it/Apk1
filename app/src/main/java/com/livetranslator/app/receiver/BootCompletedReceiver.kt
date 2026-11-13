package com.livetranslator.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.service.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
      return
    }

    val pendingResult = goAsync()
    val app = context.applicationContext as? LiveTranslatorApp
    if (app == null) {
      pendingResult.finish()
      return
    }

    CoroutineScope(Dispatchers.IO).launch {
      try {
        val settings = app.repository.getCurrentSettings()
        if (settings.autoRestartOnBoot && Settings.canDrawOverlays(context)) {
          // Android 14+ restricts starting mediaProjection without a fresh user prompt after boot;
          // start the floating OverlayService in standby mode so one tap resumes capture.
          // REQUIRES_USER_SETUP: User taps the standby bubble after reboot to re-approve MediaProjection.
          val overlayIntent =
            Intent(context, OverlayService::class.java).apply {
              this.action = OverlayService.ACTION_BOOT_STANDBY
            }
          runCatching { ContextCompat.startForegroundService(context, overlayIntent) }
        }
      } finally {
        pendingResult.finish()
      }
    }
  }
}
