package com.livetranslator.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ActionMode
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import com.livetranslator.app.ui.navigation.AppNavHost
import com.livetranslator.app.ui.theme.LiveTranslatorTheme

class MainActivity : ComponentActivity() {

  private var activeActionMode: ActionMode? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    val app = application as LiveTranslatorApp
    handleAutoStartIntent(intent, app)

    setContent {
      LiveTranslatorTheme {
        val platformToolbar = LocalTextToolbar.current
        val safeToolbar =
          remember(platformToolbar) {
            SafeTextToolbar(
              delegate = platformToolbar,
              hasActiveActionMode = { activeActionMode != null },
              clearTrackedActionMode = { activeActionMode = null },
            )
          }
        CompositionLocalProvider(LocalTextToolbar provides safeToolbar) {
          AppNavHost(app = app)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleAutoStartIntent(intent, application as LiveTranslatorApp)
  }

  private fun handleAutoStartIntent(intent: Intent?, app: LiveTranslatorApp) {
    if (intent?.getBooleanExtra(EXTRA_AUTO_START_TRANSLATION, false) == true) {
      intent.removeExtra(EXTRA_AUTO_START_TRANSLATION)
      app.sessionManager.requestStartFromOverlay()
    }
  }

  override fun onActionModeStarted(mode: ActionMode?) {
    super.onActionModeStarted(mode)
    activeActionMode = mode
  }

  override fun onActionModeFinished(mode: ActionMode?) {
    if (activeActionMode === mode) {
      activeActionMode = null
    }
    super.onActionModeFinished(mode)
  }

  override fun onPause() {
    activeActionMode?.let { mode ->
      activeActionMode = null
      runCatching { mode.finish() }
    }
    super.onPause()
  }

  companion object {
    const val EXTRA_AUTO_START_TRANSLATION = "extra_auto_start_translation"
  }
}

private class SafeTextToolbar(
  private val delegate: TextToolbar,
  private val hasActiveActionMode: () -> Boolean,
  private val clearTrackedActionMode: () -> Unit,
) : TextToolbar {
  override val status: TextToolbarStatus
    get() = if (hasActiveActionMode()) delegate.status else TextToolbarStatus.Hidden

  override fun showMenu(
    rect: Rect,
    onCopyRequested: (() -> Unit)?,
    onPasteRequested: (() -> Unit)?,
    onCutRequested: (() -> Unit)?,
    onSelectAllRequested: (() -> Unit)?,
  ) {
    if (hasActiveActionMode()) {
      runCatching { delegate.hide() }
      clearTrackedActionMode()
    }
    runCatching {
      delegate.showMenu(
        rect = rect,
        onCopyRequested = onCopyRequested,
        onPasteRequested = onPasteRequested,
        onCutRequested = onCutRequested,
        onSelectAllRequested = onSelectAllRequested,
      )
    }
  }

  override fun hide() {
    if (hasActiveActionMode()) {
      clearTrackedActionMode()
      runCatching { delegate.hide() }
    }
  }
}
