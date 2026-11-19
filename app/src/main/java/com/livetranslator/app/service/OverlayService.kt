package com.livetranslator.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.UserSettings
import com.livetranslator.app.engine.TranslationSessionState
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.BrightAmethyst
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.DeepViolet
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.EmeraldAccent
import com.livetranslator.app.ui.theme.GlassCardFillStrong
import com.livetranslator.app.ui.theme.GlassWhiteBorder
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.LiveTranslatorTheme
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.RoseError
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class OverlayService : LifecycleService(), SavedStateRegistryOwner {

  private val app: LiveTranslatorApp
    get() = application as LiveTranslatorApp

  private val savedStateRegistryController = SavedStateRegistryController.create(this)
  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateRegistryController.savedStateRegistry

  private lateinit var windowManager: WindowManager
  private var overlayContainer: DraggableOverlayFrameLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private var autoCollapseJob: Job? = null
  private var snoozeRestoreJob: Job? = null

  private var bubbleX: Int = 36
  private var bubbleY: Int = 260

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
        removeOverlayView()
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
          ensureOverlayAttached()
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
        updateWindowLayoutForState(state, app.repository.getCurrentSettings())
      }
    }

    lifecycleScope.launch {
      app.sessionManager.state
        .map { Triple(it.isCardExpanded, it.isBubbleSnoozed, it.lastTimestampMs) }
        .distinctUntilChanged()
        .collectLatest { (expanded, snoozed, _) ->
          if (expanded && !snoozed) {
            scheduleAutoCollapse()
          } else {
            autoCollapseJob?.cancel()
          }
        }
    }

    lifecycleScope.launch {
      app.repository.settingsFlow.collectLatest { settings ->
        updateWindowLayoutForState(app.sessionManager.state.value, settings)
      }
    }
  }

  private fun scheduleAutoCollapse() {
    autoCollapseJob?.cancel()
    autoCollapseJob =
      lifecycleScope.launch {
        delay(AUTO_HIDE_CARD_MS)
        app.sessionManager.setCardExpanded(false)
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
  private fun ensureOverlayAttached() {
    if (overlayContainer != null || !Settings.canDrawOverlays(this)) return

    val params =
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

    val themedContext = ContextThemeWrapper(this, R.style.Theme_LiveTranslator)
    val composeView =
      ComposeView(themedContext).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
          LiveTranslatorTheme {
            val sessionState by app.sessionManager.state.collectAsStateWithLifecycle()
            val settings by
              app.repository.settingsFlow.collectAsStateWithLifecycle(initialValue = UserSettings())

            OverlayRootContent(
              sessionState = sessionState,
              settings = settings,
              onCollapseCard = { app.sessionManager.setCardExpanded(false) },
            )
          }
        }
      }

    val container =
      DraggableOverlayFrameLayout(
          context = themedContext,
          layoutParamsProvider = { layoutParams },
          isExpandedProvider = { app.sessionManager.state.value.isCardExpanded },
          onBubbleTap = {
            val current = app.sessionManager.state.value
            app.sessionManager.setCardExpanded(!current.isCardExpanded)
          },
          onBubbleLongPress = { snoozeBubbleFiveMinutes() },
          onUpdateLayout = { updatedParams ->
            val targetView = overlayContainer ?: return@DraggableOverlayFrameLayout
            if (!app.sessionManager.state.value.isCardExpanded) {
              bubbleX = updatedParams.x
              bubbleY = updatedParams.y
            }
            runCatching { windowManager.updateViewLayout(targetView, updatedParams) }
          },
        )
        .apply {
          setViewTreeLifecycleOwner(this@OverlayService)
          setViewTreeSavedStateRegistryOwner(this@OverlayService)
          addView(composeView)
        }

    layoutParams = params
    overlayContainer = container
    runCatching { windowManager.addView(container, params) }
  }

  @SuppressLint("RtlHardcoded")
  private fun updateWindowLayoutForState(state: TranslationSessionState, settings: UserSettings) {
    val container = overlayContainer ?: return
    val params = layoutParams ?: return

    if (state.isBubbleSnoozed) {
      container.visibility = View.GONE
      return
    }
    container.visibility = View.VISIBLE

    val targetWidth: Int
    val targetGravity: Int
    val targetX: Int
    val targetY: Int

    if (state.isCardExpanded) {
      targetWidth = WindowManager.LayoutParams.MATCH_PARENT
      targetGravity =
        when (settings.overlayPosition) {
          OverlayPosition.TOP -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
          OverlayPosition.CENTER -> Gravity.CENTER
          OverlayPosition.BOTTOM -> Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
      targetX = 0
      targetY =
        when (settings.overlayPosition) {
          OverlayPosition.TOP -> 96
          OverlayPosition.CENTER -> 0
          OverlayPosition.BOTTOM -> 96
        }
    } else {
      targetWidth = WindowManager.LayoutParams.WRAP_CONTENT
      targetGravity = Gravity.TOP or Gravity.LEFT
      targetX = bubbleX
      targetY = bubbleY
    }

    val changed =
      params.width != targetWidth ||
        params.gravity != targetGravity ||
        params.x != targetX ||
        params.y != targetY

    if (changed) {
      params.width = targetWidth
      params.height = WindowManager.LayoutParams.WRAP_CONTENT
      params.gravity = targetGravity
      params.x = targetX
      params.y = targetY
      runCatching { windowManager.updateViewLayout(container, params) }
    }
  }

  private fun removeOverlayView() {
    autoCollapseJob?.cancel()
    snoozeRestoreJob?.cancel()
    overlayContainer?.let { container -> runCatching { windowManager.removeView(container) } }
    overlayContainer = null
    layoutParams = null
  }

  override fun onDestroy() {
    removeOverlayView()
    app.sessionManager.updateOverlayActive(false)
    super.onDestroy()
  }

  companion object {
    const val ACTION_SHOW_ACTIVE = "com.livetranslator.app.action.SHOW_ACTIVE_OVERLAY"
    const val ACTION_BOOT_STANDBY = "com.livetranslator.app.action.BOOT_STANDBY_OVERLAY"
    const val ACTION_STOP_OVERLAY = "com.livetranslator.app.action.STOP_OVERLAY"
    private const val AUTO_HIDE_CARD_MS = 6_000L
    private const val SNOOZE_DURATION_MS = 5 * 60 * 1000L
  }
}

private class DraggableOverlayFrameLayout(
  context: Context,
  private val layoutParamsProvider: () -> WindowManager.LayoutParams?,
  private val isExpandedProvider: () -> Boolean,
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
    if (!isDragging && !isExpandedProvider()) {
      longPressTriggered = true
      onBubbleLongPress()
    }
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (isExpandedProvider()) {
      return super.dispatchTouchEvent(event)
    }

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

@Composable
private fun OverlayRootContent(
  sessionState: TranslationSessionState,
  settings: UserSettings,
  onCollapseCard: () -> Unit,
) {
  Box(modifier = Modifier.padding(8.dp)) {
    AnimatedVisibility(
      visible = !sessionState.isCardExpanded,
      enter = fadeIn(tween(180)),
      exit = fadeOut(tween(150)),
    ) {
      FloatingBubble(
        isTranslating = sessionState.isTranslating,
        isRunning = sessionState.isRunning,
        hasError = sessionState.lastError != null,
        audioLevel = sessionState.audioLevel,
      )
    }

    AnimatedVisibility(
      visible = sessionState.isCardExpanded,
      enter = fadeIn(tween(220)),
      exit = fadeOut(tween(180)),
    ) {
      FloatingTranslationCard(
        sessionState = sessionState,
        settings = settings,
        onCollapse = onCollapseCard,
      )
    }
  }
}

@Composable
private fun FloatingBubble(
  isTranslating: Boolean,
  isRunning: Boolean,
  hasError: Boolean,
  audioLevel: Float,
) {
  val infiniteTransition = rememberInfiniteTransition(label = "bubblePulse")
  val pulseScale by
    infiniteTransition.animateFloat(
      initialValue = 1f,
      targetValue = if (isTranslating || audioLevel > 0.08f) 1.09f else 1.02f,
      animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
      label = "pulseScale",
    )

  val badgeColor =
    when {
      hasError -> RoseError
      isRunning -> EmeraldAccent
      else -> SoftLavender
    }

  Box(
    modifier =
      Modifier.size(60.dp)
        .scale(pulseScale)
        .shadow(14.dp, CircleShape, ambientColor = ElectricViolet, spotColor = BrightAmethyst)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(
            colors =
              if (isRunning) {
                listOf(BrightAmethyst, ElectricViolet, DeepViolet)
              } else {
                listOf(GlassCardFillStrong, DeepViolet)
              }
          )
        )
        .border(1.5.dp, SoftLavender.copy(alpha = 0.85f), CircleShape),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_translate),
      contentDescription = stringResource(R.string.overlay_bubble_desc),
      tint = CrispWhite,
      modifier = Modifier.size(28.dp),
    )

    Box(
      modifier =
        Modifier.align(Alignment.TopEnd)
          .padding(6.dp)
          .size(11.dp)
          .clip(CircleShape)
          .background(badgeColor)
          .border(1.5.dp, VoidSurface, CircleShape)
    )
  }
}

@Composable
fun FloatingTranslationCard(
  sessionState: TranslationSessionState,
  settings: UserSettings,
  onCollapse: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val cornerShape = RoundedCornerShape(settings.overlayCornerRadiusDp.dp)
  val effectiveAlpha =
    if (settings.overlayBackground == OverlayBackground.NONE) 0f
    else settings.overlayBoxOpacity.coerceIn(0f, 1f)
  val containerColor = Color(settings.overlayBoxColorArgb).copy(alpha = effectiveAlpha)

  val borderStroke =
    if (effectiveAlpha <= 0.05f) {
      null
    } else {
      BorderStroke(
        width = 1.dp,
        brush =
          Brush.linearGradient(
            listOf(
              CrispWhite.copy(alpha = 0.25f),
              ElectricViolet.copy(alpha = 0.48f),
              GlassWhiteBorder,
            )
          ),
      )
    }

  val fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont)
  val fontWeight = ArabicFontRegistry.composeWeightFor(settings.overlayFontWeight)
  val textColor = Color(settings.overlayTextColorArgb)

  val statusDotColor =
    when {
      sessionState.lastError != null -> RoseError
      sessionState.isRunning -> EmeraldAccent
      else -> SoftLavender
    }

  val arabicDisplayText =
    when {
      sessionState.lastError != null -> "تنبيه: ${sessionState.lastError}"
      sessionState.lastTranslatedText.isNotBlank() -> sessionState.lastTranslatedText
      sessionState.isRunning ->
        "يتم التقاط صوت الهاتف وترجمته فورياً إلى العربية..."
      else ->
        "صندوق الترجمة الفورية جاهز.\nافتح التطبيق واضغط «ابدأ الترجمة» لالتقاط صوت الفيديوهات والبثوث."
    }

  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).widthIn(max = 540.dp),
    shape = cornerShape,
    color = containerColor,
    border = borderStroke,
    shadowElevation = if (effectiveAlpha <= 0.05f) 0.dp else 12.dp,
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(statusDotColor))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (sessionState.isRunning) {
                "${sessionState.statusMessage} • جمل: ${sessionState.segmentsTranslated}"
              } else {
                sessionState.statusMessage
              },
            color = if (sessionState.lastError != null) RoseError else MutedViolet,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
          )
        }

        IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
          Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = stringResource(R.string.overlay_close_desc),
            tint = LavenderWhite,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      if (sessionState.isRunning) {
        Spacer(modifier = Modifier.height(6.dp))
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(4.dp)
              .clip(CircleShape)
              .background(VoidSurfaceVariant)
        ) {
          Box(
            modifier =
              Modifier.fillMaxWidth(sessionState.audioLevel.coerceAtLeast(0.04f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(ElectricViolet, EmeraldAccent)))
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = arabicDisplayText,
        color = if (sessionState.lastError != null) RoseError else textColor,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize =
          if (sessionState.lastTranslatedText.isNotBlank() && sessionState.lastError == null) {
            settings.overlayFontSizeSp.sp
          } else {
            15.sp
          },
        lineHeight =
          if (sessionState.lastTranslatedText.isNotBlank() && sessionState.lastError == null) {
            (settings.overlayFontSizeSp * 1.38f).sp
          } else {
            22.sp
          },
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
      )

      val secondarySource =
        sessionState.lastPartialSource.ifBlank { sessionState.lastSourceText }
      if (secondarySource.isNotBlank() && sessionState.lastError == null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = secondarySource,
          color = MutedViolet.copy(alpha = 0.85f),
          fontSize = 12.sp,
          lineHeight = 16.sp,
          maxLines = 2,
        )
      }

      if (!sessionState.isRunning) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
          onClick = {
            onCollapse()
            val pending =
              PendingIntent.getActivity(
                context,
                100,
                Intent(context, MainActivity::class.java).apply {
                  flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
              )
            runCatching { pending.send() }
          },
          colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.height(36.dp),
        ) {
          Text("فتح التطبيق", color = CrispWhite, fontSize = 12.sp)
        }
      }
    }
  }
}
