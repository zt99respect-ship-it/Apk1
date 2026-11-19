package com.livetranslator.app.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.MainActivity
import com.livetranslator.app.R
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayFontWeight
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.data.model.TranslationMode
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.engine.TranslationSessionState
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.BoxColorSwatches
import com.livetranslator.app.ui.theme.BrightAmethyst
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.DeepViolet
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.EmeraldAccent
import com.livetranslator.app.ui.theme.GlassCardFillStrong
import com.livetranslator.app.ui.theme.GlassWhiteBorder
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.LightGrayText
import com.livetranslator.app.ui.theme.LiveTranslatorTheme
import com.livetranslator.app.ui.theme.MutedGrayText
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.RoseError
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.TextColorSwatches
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OverlayService : LifecycleService(), SavedStateRegistryOwner {

  private val app: LiveTranslatorApp
    get() = application as LiveTranslatorApp

  private val savedStateRegistryController = SavedStateRegistryController.create(this)
  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateRegistryController.savedStateRegistry

  private lateinit var windowManager: WindowManager

  // 1. Floating Watermark / Control Icon (never transforms into translation text)
  private var bubbleContainer: DraggableBubbleFrameLayout? = null
  private var bubbleParams: WindowManager.LayoutParams? = null
  private var bubbleX: Int = 36
  private var bubbleY: Int = 260

  // 2. Independent Subtitle / Translation Box (draggable to any exact spot on screen)
  private var subtitleContainer: DraggableSubtitleFrameLayout? = null
  private var subtitleParams: WindowManager.LayoutParams? = null
  @Volatile private var isSubtitleDragging: Boolean = false

  // 3. Floating Control Panel (opened only when tapping the watermark icon)
  private var controlPanelContainer: FrameLayout? = null
  private var controlPanelParams: WindowManager.LayoutParams? = null

  private var snoozeRestoreJob: Job? = null

  override fun onCreate() {
    savedStateRegistryController.performRestore(null)
    super.onCreate()
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    observeSessionAndSettings()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    super.onStartCommand(intent, flags, startId)
    when (intent?.action) {
      ACTION_STOP_OVERLAY -> {
        removeAllOverlayViews()
        app.sessionManager.updateOverlayActive(false)
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_BOOT_STANDBY, ACTION_SHOW_ACTIVE, null -> {
        startForegroundSafely()
        if (Settings.canDrawOverlays(this)) {
          app.sessionManager.clearSnooze()
          app.sessionManager.updateOverlayActive(true)
          ensureOverlaysAttached()
        } else {
          stopForeground(Service.STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
    return START_STICKY
  }

  private fun startForegroundSafely() {
    val notification = NotificationHelper.buildOverlayStandbyNotification(this)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startForeground(
        NotificationHelper.OVERLAY_NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
      )
    } else {
      startForeground(NotificationHelper.OVERLAY_NOTIFICATION_ID, notification)
    }
  }

  private fun observeSessionAndSettings() {
    lifecycleScope.launch {
      app.sessionManager.state.collectLatest { state ->
        updateWindowsVisibilityAndPosition(state, app.repository.getCurrentSettings())
      }
    }

    lifecycleScope.launch {
      app.repository.settingsFlow.collectLatest { settings ->
        updateWindowsVisibilityAndPosition(app.sessionManager.state.value, settings)
      }
    }

    lifecycleScope.launch {
      app.remoteControlConfig.collectLatest { remote ->
        if (remote.maintenanceEnabled || !remote.allowOverlayBubble) {
          removeAllOverlayViews()
          app.sessionManager.updateOverlayActive(false)
          stopForeground(Service.STOP_FOREGROUND_REMOVE)
          stopSelf()
        }
      }
    }
  }

  private fun snoozeBubbleFiveMinutes() {
    app.sessionManager.snoozeBubbleFor(SNOOZE_DURATION_MS)
    Toast.makeText(this, R.string.overlay_snoozed_toast, Toast.LENGTH_SHORT).show()
    snoozeRestoreJob?.cancel()
    snoozeRestoreJob =
      lifecycleScope.launch {
        delay(SNOOZE_DURATION_MS)
        app.sessionManager.clearSnooze()
      }
  }

  @SuppressLint("RtlHardcoded")
  private fun ensureOverlaysAttached() {
    if (!Settings.canDrawOverlays(this)) return
    val themedContext = ContextThemeWrapper(this, R.style.Theme_LiveTranslator)
    val metrics = resources.displayMetrics

    // 1. Attach Subtitle Box window FIRST so bubble & control panel stay above it cleanly
    if (subtitleContainer == null) {
      val boxWidth = (metrics.widthPixels * 0.90f).toInt().coerceAtMost((520 * metrics.density).toInt())
      val defaultX = ((metrics.widthPixels - boxWidth) / 2).coerceAtLeast(0)
      val defaultY = (metrics.heightPixels * 0.72f).toInt()

      val sParams =
        WindowManager.LayoutParams(
          boxWidth,
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        ).apply {
          gravity = Gravity.TOP or Gravity.LEFT
          x = defaultX
          y = defaultY
        }

      val subtitleComposeView =
        ComposeView(themedContext).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
          setContent {
            LiveTranslatorTheme {
              val sessionState by app.sessionManager.state.collectAsStateWithLifecycle()
              val settings by
                app.repository.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

              IndependentSubtitleBox(
                sessionState = sessionState,
                settings = settings,
                onLockPosition = { app.sessionManager.setRepositioningSubtitle(false) },
              )
            }
          }
        }

      val sContainer =
        DraggableSubtitleFrameLayout(
            context = themedContext,
            layoutParamsProvider = { subtitleParams },
            onDragStateChanged = { dragging -> isSubtitleDragging = dragging },
            onUpdateLayout = { updated ->
              val v = subtitleContainer ?: return@DraggableSubtitleFrameLayout
              runCatching { windowManager.updateViewLayout(v, updated) }
              reportExcludedOverlayRects()
            },
            onDragFinished = { finalX, finalY ->
              isSubtitleDragging = false
              reportExcludedOverlayRects()
              lifecycleScope.launch {
                app.repository.setOverlayCustomPosition(finalX, finalY)
              }
            },
          )
          .apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            visibility = View.GONE
            addView(subtitleComposeView)
          }

      subtitleParams = sParams
      subtitleContainer = sContainer
      runCatching { windowManager.addView(sContainer, sParams) }
    }

    // 2. Attach Floating Control Panel window (shown only when user taps watermark icon)
    if (controlPanelContainer == null) {
      val pParams =
        WindowManager.LayoutParams(
          WindowManager.LayoutParams.MATCH_PARENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        ).apply {
          gravity = Gravity.CENTER
        }

      val panelComposeView =
        ComposeView(themedContext).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
          setContent {
            LiveTranslatorTheme {
              val sessionState by app.sessionManager.state.collectAsStateWithLifecycle()
              val settings by
                app.repository.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

              FloatingControlPanelCard(
                sessionState = sessionState,
                settings = settings,
                onClosePanel = { app.sessionManager.setControlPanelOpen(false) },
                onSelectMode = { mode ->
                  lifecycleScope.launch { app.repository.setTranslationMode(mode) }
                },
                onSnapScreenNow = {
                  val snapIntent =
                    Intent(this@OverlayService, TranslationForegroundService::class.java).apply {
                      action = TranslationForegroundService.ACTION_SNAP_SCREEN_ONCE
                    }
                  runCatching { startService(snapIntent) }
                },
                onToggleTranslation = { handleToggleTranslationFromOverlay(sessionState, settings) },
                onToggleSubtitleVisibility = {
                  app.sessionManager.setSubtitleVisible(!sessionState.isSubtitleVisible)
                },
                onToggleRepositionMode = {
                  val next = !sessionState.isRepositioningSubtitle
                  app.sessionManager.setRepositioningSubtitle(next)
                  if (next) {
                    app.sessionManager.setControlPanelOpen(false)
                    Toast.makeText(
                        this@OverlayService,
                        "اسحب صندوق الترجمة للمكان المطلوب ثم اضغط تثبيت",
                        Toast.LENGTH_SHORT,
                      )
                      .show()
                  }
                },
                onResetCustomPosition = {
                  lifecycleScope.launch { app.repository.resetOverlayCustomPosition() }
                },
                onSelectPreset = { preset ->
                  lifecycleScope.launch { app.repository.applyStylePreset(preset) }
                },
                onSelectFont = { font ->
                  lifecycleScope.launch { app.repository.setOverlayFont(font) }
                },
                onSelectWeight = { weight ->
                  lifecycleScope.launch { app.repository.setOverlayFontWeight(weight) }
                },
                onChangeFontSize = { sizeSp ->
                  lifecycleScope.launch { app.repository.setOverlayFontSizeSp(sizeSp) }
                },
                onSelectTextColor = { argb ->
                  lifecycleScope.launch { app.repository.setOverlayTextColorArgb(argb) }
                },
                onSelectBoxColor = { argb ->
                  lifecycleScope.launch { app.repository.setOverlayBoxColorArgb(argb) }
                },
                onChangeBoxOpacity = { alpha ->
                  lifecycleScope.launch { app.repository.setOverlayBoxOpacity(alpha) }
                },
                onResetStyle = {
                  lifecycleScope.launch { app.repository.resetStyleToDefaults() }
                },
              )
            }
          }
        }

      val pContainer =
        FrameLayout(themedContext).apply {
          setViewTreeLifecycleOwner(this@OverlayService)
          setViewTreeSavedStateRegistryOwner(this@OverlayService)
          visibility = View.GONE
          addView(panelComposeView)
        }

      controlPanelParams = pParams
      controlPanelContainer = pContainer
      runCatching { windowManager.addView(pContainer, pParams) }
    }

    // 3. Attach Floating Watermark Icon LAST so it stays reachable on top
    if (bubbleContainer == null) {
      val bParams =
        WindowManager.LayoutParams(
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT,
        ).apply {
          gravity = Gravity.TOP or Gravity.LEFT
          x = bubbleX
          y = bubbleY
        }

      val bubbleComposeView =
        ComposeView(themedContext).apply {
          setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
          setContent {
            LiveTranslatorTheme {
              val sessionState by app.sessionManager.state.collectAsStateWithLifecycle()
              FloatingControlWatermarkIcon(
                isTranslating = sessionState.isTranslating,
                isRunning = sessionState.isRunning,
                isPanelOpen = sessionState.isControlPanelOpen,
                hasError = sessionState.lastError != null,
                audioLevel = sessionState.audioLevel,
              )
            }
          }
        }

      val bContainer =
        DraggableBubbleFrameLayout(
            context = themedContext,
            layoutParamsProvider = { bubbleParams },
            onBubbleTap = { app.sessionManager.toggleControlPanel() },
            onBubbleLongPress = { snoozeBubbleFiveMinutes() },
            onUpdateLayout = { updated ->
              val v = bubbleContainer ?: return@DraggableBubbleFrameLayout
              bubbleX = updated.x
              bubbleY = updated.y
              runCatching { windowManager.updateViewLayout(v, updated) }
            },
          )
          .apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            addView(bubbleComposeView)
          }

      bubbleParams = bParams
      bubbleContainer = bContainer
      runCatching { windowManager.addView(bContainer, bParams) }
    }
  }

  private fun handleToggleTranslationFromOverlay(
    sessionState: TranslationSessionState,
    settings: UserSettings,
  ) {
    if (sessionState.isRunning) {
      val stopIntent =
        Intent(this, TranslationForegroundService::class.java).apply {
          action = TranslationForegroundService.ACTION_STOP
        }
      startService(stopIntent)
      return
    }

    val hasMic =
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    if (
      settings.translationMode == TranslationMode.AUDIO_LIVE &&
        settings.audioSourcePreference == AudioSourcePreference.MICROPHONE &&
        hasMic
    ) {
      val startMicIntent =
        Intent(this, TranslationForegroundService::class.java).apply {
          action = TranslationForegroundService.ACTION_START_MIC_ONLY
        }
      ContextCompat.startForegroundService(this, startMicIntent)
      app.sessionManager.setControlPanelOpen(false)
      return
    }

    // System audio (MediaProjection) requires OS dialog from an Activity; trigger Activity cleanly
    app.sessionManager.setControlPanelOpen(false)
    app.sessionManager.requestStartFromOverlay()
    val launchIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(MainActivity.EXTRA_AUTO_START_TRANSLATION, true)
      }
    val pending =
      PendingIntent.getActivity(
        this,
        201,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    runCatching { pending.send() }.onFailure {
      runCatching { startActivity(launchIntent) }
    }
  }

  @SuppressLint("RtlHardcoded")
  private fun updateWindowsVisibilityAndPosition(
    state: TranslationSessionState,
    settings: UserSettings,
  ) {
    val metrics = resources.displayMetrics

    // 1. Watermark Bubble visibility
    bubbleContainer?.let { bubble ->
      bubble.visibility = if (state.isBubbleSnoozed) View.GONE else View.VISIBLE
    }

    // 2. Control Panel visibility
    controlPanelContainer?.let { panel ->
      panel.visibility =
        if (!state.isBubbleSnoozed && state.isControlPanelOpen) View.VISIBLE else View.GONE
    }

    // 3. Independent Subtitle Box visibility & position
    subtitleContainer?.let { subView ->
      val shouldShowSubtitle =
        !state.isBubbleSnoozed &&
          state.isSubtitleVisible &&
          (state.isRunning || state.isRepositioningSubtitle || state.isControlPanelOpen)

      subView.visibility = if (shouldShowSubtitle) View.VISIBLE else View.GONE

      val sParams = subtitleParams ?: return@let
      if (!isSubtitleDragging) {
        val boxWidth = (metrics.widthPixels * 0.90f).toInt().coerceAtMost((520 * metrics.density).toInt())
        val targetX: Int
        val targetY: Int
        if (settings.overlayCustomX >= 0 && settings.overlayCustomY >= 0) {
          val maxY = (metrics.heightPixels - subView.height.coerceAtLeast(160)).coerceAtLeast(0)
          targetX = settings.overlayCustomX.coerceIn(0, (metrics.widthPixels - boxWidth).coerceAtLeast(0))
          targetY = settings.overlayCustomY.coerceIn(0, maxY)
        } else {
          targetX = ((metrics.widthPixels - boxWidth) / 2).coerceAtLeast(0)
          targetY =
            when (settings.overlayPosition) {
              OverlayPosition.TOP -> (metrics.heightPixels * 0.10f).toInt()
              OverlayPosition.CENTER -> (metrics.heightPixels * 0.42f).toInt()
              OverlayPosition.BOTTOM -> (metrics.heightPixels * 0.72f).toInt()
            }
        }

        if (sParams.width != boxWidth || sParams.x != targetX || sParams.y != targetY) {
          sParams.width = boxWidth
          sParams.x = targetX
          sParams.y = targetY
          runCatching { windowManager.updateViewLayout(subView, sParams) }
        }
      }
    }
    reportExcludedOverlayRects()
  }

  private fun reportExcludedOverlayRects() {
    val rects = ArrayList<Rect>(3)
    subtitleContainer?.let { v ->
      if (v.visibility == View.VISIBLE) {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val w = v.width.coerceAtLeast(100)
        val h = v.height.coerceAtLeast(80)
        rects.add(Rect(loc[0] - 12, loc[1] - 12, loc[0] + w + 12, loc[1] + h + 12))
      }
    }
    bubbleContainer?.let { v ->
      if (v.visibility == View.VISIBLE) {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        rects.add(Rect(loc[0] - 8, loc[1] - 8, loc[0] + v.width + 8, loc[1] + v.height + 8))
      }
    }
    controlPanelContainer?.let { v ->
      if (v.visibility == View.VISIBLE) {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        rects.add(Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height))
      }
    }
    app.screenOcrEngine.updateExcludedOverlayRects(rects)
  }

  private fun removeAllOverlayViews() {
    snoozeRestoreJob?.cancel()
    bubbleContainer?.let { runCatching { windowManager.removeView(it) } }
    controlPanelContainer?.let { runCatching { windowManager.removeView(it) } }
    subtitleContainer?.let { runCatching { windowManager.removeView(it) } }
    bubbleContainer = null
    bubbleParams = null
    controlPanelContainer = null
    controlPanelParams = null
    subtitleContainer = null
    subtitleParams = null
  }

  override fun onDestroy() {
    removeAllOverlayViews()
    app.sessionManager.updateOverlayActive(false)
    super.onDestroy()
  }

  companion object {
    const val ACTION_SHOW_ACTIVE = "com.livetranslator.app.action.SHOW_ACTIVE_OVERLAY"
    const val ACTION_BOOT_STANDBY = "com.livetranslator.app.action.BOOT_STANDBY_OVERLAY"
    const val ACTION_STOP_OVERLAY = "com.livetranslator.app.action.STOP_OVERLAY"
    private const val SNOOZE_DURATION_MS = 5 * 60 * 1000L
  }
}

private class DraggableBubbleFrameLayout(
  context: Context,
  private val layoutParamsProvider: () -> WindowManager.LayoutParams?,
  private val onBubbleTap: () -> Unit,
  private val onBubbleLongPress: () -> Unit,
  private val onUpdateLayout: (WindowManager.LayoutParams) -> Unit,
) : FrameLayout(context) {

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
  private val mainHandler = Handler(Looper.getMainLooper())
  private var initialX = 0
  private var initialY = 0
  private var touchStartX = 0f
  private var touchStartY = 0f
  private var isDragging = false
  private var longPressTriggered = false

  private val longPressRunnable = Runnable {
    if (!isDragging) {
      longPressTriggered = true
      onBubbleLongPress()
    }
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    val params = layoutParamsProvider() ?: return super.dispatchTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        initialX = params.x
        initialY = params.y
        touchStartX = event.rawX
        touchStartY = event.rawY
        isDragging = false
        longPressTriggered = false
        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.postDelayed(longPressRunnable, 550L)
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - touchStartX
        val dy = event.rawY - touchStartY
        if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
          isDragging = true
          mainHandler.removeCallbacks(longPressRunnable)
        }
        if (isDragging) {
          val metrics = resources.displayMetrics
          val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
          val maxY = (metrics.heightPixels - height).coerceAtLeast(0)
          params.x = (initialX + dx.toInt()).coerceIn(0, maxX)
          params.y = (initialY + dy.toInt()).coerceIn(0, maxY)
          onUpdateLayout(params)
        }
        return true
      }
      MotionEvent.ACTION_UP -> {
        mainHandler.removeCallbacks(longPressRunnable)
        if (!isDragging && !longPressTriggered) {
          onBubbleTap()
        }
        isDragging = false
        longPressTriggered = false
        return true
      }
      MotionEvent.ACTION_CANCEL -> {
        mainHandler.removeCallbacks(longPressRunnable)
        isDragging = false
        longPressTriggered = false
        return true
      }
    }
    return super.dispatchTouchEvent(event)
  }
}

/**
 * Intercepts drag gestures on the independent subtitle box once movement exceeds touchSlop,
 * while still allowing taps on child buttons (like "تثبيت المكان").
 */
private class DraggableSubtitleFrameLayout(
  context: Context,
  private val layoutParamsProvider: () -> WindowManager.LayoutParams?,
  private val onDragStateChanged: (Boolean) -> Unit,
  private val onUpdateLayout: (WindowManager.LayoutParams) -> Unit,
  private val onDragFinished: (Int, Int) -> Unit,
) : FrameLayout(context) {

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
  private var initialX = 0
  private var initialY = 0
  private var touchStartX = 0f
  private var touchStartY = 0f
  private var isDragging = false

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    val params = layoutParamsProvider() ?: return false
    when (ev.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        initialX = params.x
        initialY = params.y
        touchStartX = ev.rawX
        touchStartY = ev.rawY
        isDragging = false
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        val dx = ev.rawX - touchStartX
        val dy = ev.rawY - touchStartY
        if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
          isDragging = true
          onDragStateChanged(true)
          return true
        }
      }
    }
    return false
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    val params = layoutParamsProvider() ?: return super.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_MOVE -> {
        val dx = event.rawX - touchStartX
        val dy = event.rawY - touchStartY
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - height).coerceAtLeast(0)
        params.x = (initialX + dx.toInt()).coerceIn(0, maxX)
        params.y = (initialY + dy.toInt()).coerceIn(0, maxY)
        onUpdateLayout(params)
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (isDragging) {
          onDragFinished(params.x, params.y)
        } else {
          onDragStateChanged(false)
        }
        isDragging = false
        return true
      }
    }
    return super.onTouchEvent(event)
  }
}

@Composable
private fun FloatingControlWatermarkIcon(
  isTranslating: Boolean,
  isRunning: Boolean,
  isPanelOpen: Boolean,
  hasError: Boolean,
  audioLevel: Float,
) {
  val badgeColor =
    when {
      hasError -> RoseError
      isRunning -> EmeraldAccent
      else -> SoftLavender
    }

  Box(modifier = Modifier.padding(6.dp)) {
    Box(
      modifier =
        Modifier.size(56.dp)
          .shadow(12.dp, CircleShape, ambientColor = ElectricViolet, spotColor = BrightAmethyst)
          .clip(CircleShape)
          .background(
            Brush.linearGradient(
              colors =
                when {
                  isPanelOpen -> listOf(BrightAmethyst, ElectricViolet)
                  isRunning -> listOf(ElectricViolet, DeepViolet)
                  else -> listOf(GlassCardFillStrong, DeepViolet.copy(alpha = 0.85f))
                }
            )
          )
          .border(
            width = if (isPanelOpen || audioLevel > 0.08f || isTranslating) 2.dp else 1.2.dp,
            color = if (isPanelOpen) CrispWhite else SoftLavender.copy(alpha = 0.78f),
            shape = CircleShape,
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_translate),
        contentDescription = stringResource(R.string.overlay_bubble_desc),
        tint = CrispWhite.copy(alpha = if (isRunning || isPanelOpen) 1f else 0.85f),
        modifier = Modifier.size(26.dp),
      )

      Box(
        modifier =
          Modifier.align(Alignment.TopEnd)
            .padding(5.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(badgeColor)
            .border(1.5.dp, VoidSurface, CircleShape)
      )
    }
  }
}

@Composable
private fun IndependentSubtitleBox(
  sessionState: TranslationSessionState,
  settings: UserSettings,
  onLockPosition: () -> Unit,
) {
  val cornerShape = RoundedCornerShape(settings.overlayCornerRadiusDp.dp)
  val effectiveAlpha =
    if (settings.overlayBackground == OverlayBackground.NONE) 0f
    else settings.overlayBoxOpacity.coerceIn(0f, 1f)
  val containerColor = Color(settings.overlayBoxColorArgb).copy(alpha = effectiveAlpha)

  val borderStroke =
    when {
      sessionState.isRepositioningSubtitle -> BorderStroke(2.dp, ElectricViolet)
      effectiveAlpha <= 0.05f -> null
      else ->
        BorderStroke(
          width = 1.dp,
          brush =
            Brush.linearGradient(
              listOf(
                CrispWhite.copy(alpha = 0.25f),
                ElectricViolet.copy(alpha = 0.45f),
                GlassWhiteBorder,
              )
            ),
        )
    }

  val fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont)
  val fontWeight = ArabicFontRegistry.composeWeightFor(settings.overlayFontWeight)
  val textColor = Color(settings.overlayTextColorArgb)

  val displayArabicText =
    when {
      sessionState.isRepositioningSubtitle -> "اسحب هذا الصندوق للمكان اللي تبيه بالشاشة"
      sessionState.lastError != null -> "تنبيه: ${sessionState.lastError}"
      sessionState.lastTranslatedText.isNotBlank() -> sessionState.lastTranslatedText
      sessionState.isControlPanelOpen && !sessionState.isRunning ->
        "معاينة شكل ومكان الترجمة الفورية فوق الفيديو"
      sessionState.isRunning && sessionState.activeTranslationMode == TranslationMode.SCREEN_OCR ->
        "يراقب نصوص الشاشة لترجمتها للعربية..."
      sessionState.isRunning -> "يستمع لصوت الهاتف الآن..."
      else -> "معاينة صندوق الترجمة"
    }

  Surface(
    modifier = Modifier.fillMaxWidth().padding(4.dp),
    shape = cornerShape,
    color = if (sessionState.isRepositioningSubtitle && effectiveAlpha < 0.35f) VoidSurface.copy(alpha = 0.72f) else containerColor,
    border = borderStroke,
    shadowElevation = if (effectiveAlpha <= 0.05f && !sessionState.isRepositioningSubtitle) 0.dp else 10.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (sessionState.isRepositioningSubtitle) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_drag_handle),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "وضع تحديد المكان",
              color = SoftLavender,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
          }
          Button(
            onClick = onLockPosition,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(34.dp),
          ) {
            Text("تثبيت المكان ✓", color = CrispWhite, fontSize = 12.sp)
          }
        }
      }

      Text(
        text = displayArabicText,
        color = if (sessionState.lastError != null && !sessionState.isRepositioningSubtitle) RoseError else textColor,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = settings.overlayFontSizeSp.sp,
        lineHeight = (settings.overlayFontSizeSp * 1.38f).sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )

      if (sessionState.isRunning && sessionState.lastPartialSource.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = sessionState.lastPartialSource,
          color = MutedViolet.copy(alpha = 0.78f),
          fontSize = 11.sp,
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun FloatingControlPanelCard(
  sessionState: TranslationSessionState,
  settings: UserSettings,
  onClosePanel: () -> Unit,
  onSelectMode: (TranslationMode) -> Unit,
  onSnapScreenNow: () -> Unit,
  onToggleTranslation: () -> Unit,
  onToggleSubtitleVisibility: () -> Unit,
  onToggleRepositionMode: () -> Unit,
  onResetCustomPosition: () -> Unit,
  onSelectPreset: (OverlayStylePreset) -> Unit,
  onSelectFont: (ArabicFontOption) -> Unit,
  onSelectWeight: (OverlayFontWeight) -> Unit,
  onChangeFontSize: (Float) -> Unit,
  onSelectTextColor: (Long) -> Unit,
  onSelectBoxColor: (Long) -> Unit,
  onChangeBoxOpacity: (Float) -> Unit,
  onResetStyle: () -> Unit,
) {
  val sizePercent =
    (((settings.overlayFontSizeSp - 14f) / (42f - 14f)) * 80f + 20f).roundToInt().coerceIn(20, 100)
  val opacityPercent = (settings.overlayBoxOpacity * 100f).roundToInt().coerceIn(0, 100)

  Box(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = 540.dp),
      shape = RoundedCornerShape(24.dp),
      color = VoidSurface.copy(alpha = 0.94f),
      border =
        BorderStroke(
          1.dp,
          Brush.linearGradient(
            listOf(
              CrispWhite.copy(alpha = 0.28f),
              ElectricViolet.copy(alpha = 0.55f),
              GlassWhiteBorder,
            )
          ),
        ),
      shadowElevation = 18.dp,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier =
                Modifier.size(9.dp)
                  .clip(CircleShape)
                  .background(if (sessionState.isRunning) EmeraldAccent else SoftLavender)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "التحكم بالترجمة",
              color = CrispWhite,
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
            )
          }

          IconButton(onClick = onClosePanel, modifier = Modifier.size(36.dp)) {
            Icon(
              painter = painterResource(id = R.drawable.ic_close),
              contentDescription = "إغلاق لوحة التحكم",
              tint = LavenderWhite,
              modifier = Modifier.size(18.dp),
            )
          }
        }

        // Mode switcher: Audio vs Screen OCR
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TranslationMode.entries.forEach { mode ->
            val selected = settings.translationMode == mode
            Box(
              modifier =
                Modifier.weight(1f)
                  .height(40.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    if (selected) ElectricViolet.copy(alpha = 0.28f) else VoidSurfaceVariant
                  )
                  .border(
                    1.dp,
                    if (selected) ElectricViolet else GlassWhiteBorder,
                    RoundedCornerShape(12.dp),
                  )
                  .clickable { onSelectMode(mode) },
              contentAlignment = Alignment.Center,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  painter =
                    painterResource(
                      id =
                        if (mode == TranslationMode.SCREEN_OCR) R.drawable.ic_screen_scan
                        else R.drawable.ic_device_audio
                    ),
                  contentDescription = null,
                  tint = if (selected) CrispWhite else SoftLavender,
                  modifier = Modifier.size(15.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = mode.displayName,
                  color = if (selected) CrispWhite else LightGrayText,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }

        // Primary Start/Stop + Show/Hide Subtitle Box
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Button(
            onClick = onToggleTranslation,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = if (sessionState.isRunning) RoseError else ElectricViolet
              ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1.2f).height(46.dp),
          ) {
            Icon(
              painter =
                painterResource(
                  id = if (sessionState.isRunning) R.drawable.ic_stop else R.drawable.ic_play
                ),
              contentDescription = null,
              tint = CrispWhite,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (sessionState.isRunning) "إيقاف الترجمة" else "تشغيل الترجمة",
              color = CrispWhite,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
            )
          }

          OutlinedButton(
            onClick = onToggleSubtitleVisibility,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(46.dp),
          ) {
            Text(
              text = if (sessionState.isSubtitleVisible) "إخفاء الصندوق" else "إظهار الصندوق",
              color = if (sessionState.isSubtitleVisible) SoftLavender else MutedGrayText,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        if (sessionState.isRunning && settings.translationMode == TranslationMode.SCREEN_OCR) {
          OutlinedButton(
            onClick = onSnapScreenNow,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(40.dp),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_screen_scan),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("لقطة فورية لترجمة الشاشة الآن", color = SoftLavender, fontSize = 12.sp)
          }
        }

        // Position controls
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier =
              Modifier.weight(1.3f)
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                  if (sessionState.isRepositioningSubtitle) ElectricViolet.copy(alpha = 0.28f)
                  else VoidSurfaceVariant
                )
                .border(
                  1.dp,
                  if (sessionState.isRepositioningSubtitle) ElectricViolet else GlassWhiteBorder,
                  RoundedCornerShape(12.dp),
                )
                .clickable(onClick = onToggleRepositionMode),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_drag_handle),
                contentDescription = null,
                tint = SoftLavender,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "تحديد مكان الترجمة",
                color = CrispWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Box(
            modifier =
              Modifier.weight(0.7f)
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VoidSurfaceVariant)
                .border(1.dp, GlassWhiteBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onResetCustomPosition),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "توسيط",
              color = LightGrayText,
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
            )
          }
        }

        HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)

        Text(
          text = "تعديل شكل الترجمة مباشرة",
          color = SoftLavender,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
        )

        // Presets
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OverlayStylePreset.entries.forEach { preset ->
            val selected = settings.overlayPreset == preset
            Box(
              modifier =
                Modifier.weight(1f)
                  .height(38.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    if (selected) ElectricViolet.copy(alpha = 0.26f) else VoidSurfaceVariant
                  )
                  .border(
                    1.dp,
                    if (selected) ElectricViolet else GlassWhiteBorder,
                    RoundedCornerShape(12.dp),
                  )
                  .clickable { onSelectPreset(preset) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = preset.displayName,
                color = if (selected) CrispWhite else LightGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        // Fonts 2x2
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ArabicFontOption.entries.chunked(2).forEach { pair ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              pair.forEach { fontOpt ->
                val selected = settings.overlayFont == fontOpt
                Box(
                  modifier =
                    Modifier.weight(1f)
                      .height(38.dp)
                      .clip(RoundedCornerShape(12.dp))
                      .background(
                        if (selected) ElectricViolet.copy(alpha = 0.26f) else VoidSurfaceVariant
                      )
                      .border(
                        1.dp,
                        if (selected) ElectricViolet else GlassWhiteBorder,
                        RoundedCornerShape(12.dp),
                      )
                      .clickable { onSelectFont(fontOpt) },
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = fontOpt.shortLabel,
                    fontFamily = ArabicFontRegistry.fontFamilyFor(fontOpt),
                    color = if (selected) CrispWhite else LightGrayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                  )
                }
              }
            }
          }
        }

        // Weight
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OverlayFontWeight.entries.forEach { weightOpt ->
            val selected = settings.overlayFontWeight == weightOpt
            Box(
              modifier =
                Modifier.weight(1f)
                  .height(36.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    if (selected) ElectricViolet.copy(alpha = 0.26f) else VoidSurfaceVariant
                  )
                  .border(
                    1.dp,
                    if (selected) ElectricViolet else GlassWhiteBorder,
                    RoundedCornerShape(12.dp),
                  )
                  .clickable { onSelectWeight(weightOpt) },
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = weightOpt.label,
                color = CrispWhite,
                fontWeight = ArabicFontRegistry.composeWeightFor(weightOpt),
                fontSize = 12.sp,
              )
            }
          }
        }

        // Font size slider
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("حجم الخط", color = LightGrayText, fontSize = 12.sp)
            Text("$sizePercent%", color = SoftLavender, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          Slider(
            value = settings.overlayFontSizeSp,
            onValueChange = onChangeFontSize,
            valueRange = 14f..42f,
            colors =
              SliderDefaults.colors(
                thumbColor = CrispWhite,
                activeTrackColor = ElectricViolet,
                inactiveTrackColor = VoidSurfaceVariant,
              ),
          )
        }

        // Text color swatches
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("لون النص", color = LightGrayText, fontSize = 12.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextColorSwatches.forEach { (_, argb) ->
              val selected = settings.overlayTextColorArgb == argb
              Box(
                modifier =
                  Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                      width = if (selected) 2.5.dp else 1.dp,
                      color = if (selected) ElectricViolet else GlassWhiteBorder,
                      shape = CircleShape,
                    )
                    .clickable { onSelectTextColor(argb) }
              )
            }
          }
        }

        // Box color swatches
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("لون البوكس", color = LightGrayText, fontSize = 12.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxColorSwatches.forEach { (_, argb) ->
              val selected = settings.overlayBoxColorArgb == argb
              Box(
                modifier =
                  Modifier.size(28.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                      width = if (selected) 2.5.dp else 1.dp,
                      color = if (selected) ElectricViolet else GlassWhiteBorder,
                      shape = CircleShape,
                    )
                    .clickable { onSelectBoxColor(argb) }
              )
            }
          }
        }

        // Box opacity slider
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("شفافية البوكس", color = LightGrayText, fontSize = 12.sp)
            Text("$opacityPercent%", color = SoftLavender, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          Slider(
            value = settings.overlayBoxOpacity,
            onValueChange = onChangeBoxOpacity,
            valueRange = 0f..1f,
            colors =
              SliderDefaults.colors(
                thumbColor = CrispWhite,
                activeTrackColor = ElectricViolet,
                inactiveTrackColor = VoidSurfaceVariant,
              ),
          )
        }

        // Reset button
        OutlinedButton(
          onClick = onResetStyle,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(38.dp),
        ) {
          Text("رجّع الشكل والمكان الأصلي", color = LightGrayText, fontSize = 12.sp)
        }
      }
    }
  }
}
