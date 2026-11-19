package com.livetranslator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.R
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.model.TranslationMode
import com.livetranslator.app.service.OverlayService
import com.livetranslator.app.service.TranslationForegroundService
import com.livetranslator.app.ui.theme.AmberAccent
import com.livetranslator.app.ui.theme.AppBrandBadge
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.CircularGlassIconButton
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.EmeraldAccent
import com.livetranslator.app.ui.theme.GlassCardFillStrong
import com.livetranslator.app.ui.theme.GlassGradientPillButton
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.GlassWhiteBorder
import com.livetranslator.app.ui.theme.GlowingScreenTitle
import com.livetranslator.app.ui.theme.LightGrayText
import com.livetranslator.app.ui.theme.MutedGrayText
import com.livetranslator.app.ui.theme.RoseError
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
  viewModel: MainViewModel,
  onNavigateToDevices: () -> Unit,
  onNavigateToAccount: () -> Unit,
  onNavigateToStyle: () -> Unit,
  onNavigateToUpdates: () -> Unit,
) {
  val context = LocalContext.current
  val app = context.applicationContext as LiveTranslatorApp

  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
  val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()
  val otaState by viewModel.otaState.collectAsStateWithLifecycle()
  val remoteControl by app.remoteControlConfig.collectAsStateWithLifecycle()
  val latestHistory by viewModel.latestTranslation.collectAsStateWithLifecycle()
  val latestSession by viewModel.latestSession.collectAsStateWithLifecycle()

  var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    hasOverlayPermission = Settings.canDrawOverlays(context)
    hasMicPermission =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
  }

  val overlayPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      hasOverlayPermission = Settings.canDrawOverlays(context)
    }

  val projectionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        app.sessionManager.setPendingMediaProjection(result.resultCode, result.data)
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      } else {
        app.sessionManager.publishError("تم إلغاء إذن التقاط صوت الهاتف", expandOverlay = false)
      }
    }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted) {
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START_MIC_ONLY
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      } else {
        app.sessionManager.publishError("يرجى منح إذن الميكروفون للالتقاط", expandOverlay = false)
      }
    }

  fun startOrStopTranslation() {
    if (sessionState.isRunning) {
      val stopIntent =
        Intent(context, TranslationForegroundService::class.java).apply {
          action = TranslationForegroundService.ACTION_STOP
        }
      context.startService(stopIntent)
      return
    }
    if (settings.translationMode == TranslationMode.AUDIO_LIVE && !remoteControl.allowAudioLive) {
      app.sessionManager.publishError("تم إيقاف وضع ترجمة الصوت مؤقتاً من لوحة التحكم.", expandOverlay = false)
      return
    }
    if (settings.translationMode == TranslationMode.SCREEN_OCR && !remoteControl.allowScreenOcr) {
      app.sessionManager.publishError("تم إيقاف وضع ترجمة الشاشة مؤقتاً من لوحة التحكم.", expandOverlay = false)
      return
    }
    if (!Settings.canDrawOverlays(context)) {
      overlayPermissionLauncher.launch(
        Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}"),
        )
      )
      return
    }
    if (settings.translationMode == TranslationMode.AUDIO_LIVE &&
      settings.audioSourcePreference == AudioSourcePreference.MICROPHONE
    ) {
      if (hasMicPermission) {
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START_MIC_ONLY
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      } else {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    } else {
      val mgr =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }
  }

  val statusDotColor =
    when {
      sessionState.lastError != null || modelsState.errorMessage != null -> RoseError
      sessionState.isRunning -> EmeraldAccent
      modelsState.isDownloading -> AmberAccent
      else -> SoftLavender
    }

  val statusPillText =
    when {
      sessionState.lastError != null -> sessionState.lastError ?: "تنبيه في الخدمة"
      modelsState.isDownloading -> modelsState.stageMessage
      sessionState.isRunning -> "${sessionState.statusMessage} • ${sessionState.engineMode}"
      else -> "الخدمة الفورية جاهزة • بدون قيود"
    }

  val currentSourceLang = SupportedLanguages.findByCode(settings.sourceLanguageCode)
  val activeArabicText =
    sessionState.lastTranslatedText.ifBlank { latestHistory?.translatedText.orEmpty() }

  Box(
    modifier = Modifier.fillMaxSize().testTag("home_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.Start,
    ) {
      // Top Bar matching screenshot hierarchy
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularGlassIconButton(
            iconRes = R.drawable.ic_person,
            contentDescription = "حسابي",
            onClick = onNavigateToAccount,
            modifier = Modifier.testTag("home_account_button"),
          )
          CircularGlassIconButton(
            iconRes = R.drawable.ic_brush,
            contentDescription = "تخصيص شكل الترجمة",
            onClick = onNavigateToStyle,
            tint = if (sessionState.isOverlayActive) SoftLavender else CrispWhite,
            modifier = Modifier.testTag("home_style_button"),
          )
          Box {
            CircularGlassIconButton(
              iconRes = R.drawable.ic_bell,
              contentDescription = "التحديثات والإشعارات",
              onClick = onNavigateToUpdates,
              tint = if (otaState.hasPendingPatchUpdate) AmberAccent else CrispWhite,
              modifier = Modifier.testTag("home_updates_button"),
            )
            if (otaState.hasPendingPatchUpdate || otaState.hasNewBinaryRelease) {
              Box(
                modifier =
                  Modifier.align(Alignment.TopEnd)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(EmeraldAccent)
              )
            }
          }
        }

        AppBrandBadge()
      }

      Spacer(modifier = Modifier.height(18.dp))

      GlowingScreenTitle(
        text = stringResource(R.string.home_headline),
        fontSizeSp = 30,
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Status Pill linking to Devices / Engine details
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .background(GlassCardFillStrong)
            .border(1.dp, statusDotColor.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onNavigateToDevices)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("status_chip"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusDotColor))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = statusPillText,
          color = CrispWhite,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 1,
          modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
          painter = painterResource(id = R.drawable.ic_chevron_right),
          contentDescription = null,
          tint = LightGrayText,
          modifier = Modifier.size(14.dp),
        )
      }

      if (modelsState.isDownloading) {
        Spacer(modifier = Modifier.height(12.dp))
        GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(modelsState.stageMessage, color = CrispWhite, style = MaterialTheme.typography.labelLarge)
              Text("${modelsState.progressPercent}%", color = SoftLavender, style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
              progress = { (modelsState.progressPercent / 100f).coerceIn(0.03f, 1f) },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
              color = ElectricViolet,
              trackColor = VoidSurfaceVariant,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Card 1: Last Translation Session (matching Screenshot 3)
      GlassSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("last_session_card"),
        cornerRadius = 24.dp,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_clock),
                contentDescription = null,
                tint = SoftLavender,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.home_last_session),
                style = MaterialTheme.typography.titleLarge,
                color = CrispWhite,
              )
            }

            Text(
              text = "${currentSourceLang.displayName.substringBefore(" ")} ← عربي",
              color = SoftLavender,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier =
                Modifier.clip(RoundedCornerShape(10.dp))
                  .background(ElectricViolet.copy(alpha = 0.22f))
                  .padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (sessionState.isRunning) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text =
                  when {
                    sessionState.isArabicSpeechSkipped -> "كلام عربي (تم التخطي بذكاء)"
                    sessionState.activeTranslationMode == TranslationMode.SCREEN_OCR -> "يقرأ نصوص الشاشة الآن"
                    sessionState.audioLevel > 0.05f -> "يلتقط صوت الهاتف الآن"
                    else -> "في انتظار صوت مسموع..."
                  },
                color = if (sessionState.isArabicSpeechSkipped) SoftLavender else EmeraldAccent,
                style = MaterialTheme.typography.labelLarge,
              )
              Text(
                text = "جمل: ${sessionState.segmentsTranslated}",
                color = SoftLavender,
                style = MaterialTheme.typography.labelLarge,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(6.dp)
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
            if (activeArabicText.isNotBlank()) {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = activeArabicText,
                fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont),
                fontWeight = ArabicFontRegistry.composeWeightFor(settings.overlayFontWeight),
                fontSize = 18.sp,
                lineHeight = 27.sp,
                color = CrispWhite,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          } else if (latestSession == null && activeArabicText.isBlank()) {
            Text(
              text = stringResource(R.string.home_no_sessions),
              style = MaterialTheme.typography.bodyLarge,
              color = LightGrayText,
            )
          } else {
            val session = latestSession
            if (session != null) {
              val dateText =
                remember(session.startTimestamp) {
                  SimpleDateFormat("dd MMM • HH:mm", Locale("ar")).format(Date(session.startTimestamp))
                }
              val durationLabel = formatDurationArabic(session.durationSeconds)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "$dateText • $durationLabel",
                  color = LightGrayText,
                  style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                  text = "${session.translatedCount} جملة",
                  color = SoftLavender,
                  style = MaterialTheme.typography.labelLarge,
                )
              }
            }
            if (activeArabicText.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = activeArabicText,
                fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont),
                fontWeight = ArabicFontRegistry.composeWeightFor(settings.overlayFontWeight),
                fontSize = 18.sp,
                lineHeight = 26.sp,
                color = CrispWhite,
                maxLines = 2,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(22.dp))

      // Section Title matching Screenshot 3
      Text(
        text = "الأذونات والخدمة",
        style = MaterialTheme.typography.titleLarge,
        color = CrispWhite,
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Card 2: Audio Capture & Overlay Permission Rows
      GlassSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("permissions_service_card"),
        cornerRadius = 24.dp,
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          PermissionServiceRow(
            iconRes = R.drawable.ic_device_audio,
            title = "التقاط صوت الهاتف",
            subtitle = settings.audioSourcePreference.displayName,
            isReady =
              if (settings.audioSourcePreference == AudioSourcePreference.MICROPHONE) hasMicPermission
              else true,
            badgeText =
              if (sessionState.isRunning) "نشط"
              else if (settings.audioSourcePreference == AudioSourcePreference.MICROPHONE && !hasMicPermission) "منح الإذن"
              else "جاهز",
            onClick = {
              if (settings.audioSourcePreference == AudioSourcePreference.MICROPHONE && !hasMicPermission) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              } else {
                onNavigateToDevices()
              }
            },
            testTag = "row_audio_permission",
          )

          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)

          PermissionServiceRow(
            iconRes = R.drawable.ic_bubble,
            title = "العرض فوق التطبيقات",
            subtitle =
              if (hasOverlayPermission) {
                if (sessionState.isOverlayActive) "الصندوق العائم ظاهر حالياً" else "اضغط لإظهار الصندوق العائم"
              } else {
                "مطلوب لإظهار الترجمة فوق الفيديو"
              },
            isReady = hasOverlayPermission,
            badgeText =
              when {
                !hasOverlayPermission -> "تفعيل"
                sessionState.isOverlayActive -> "ظاهر"
                else -> "تشغيل"
              },
            onClick = {
              if (!remoteControl.allowOverlayBubble) {
                app.sessionManager.publishError("تم إيقاف الصندوق العائم مؤقتاً من لوحة التحكم.", expandOverlay = false)
              } else if (!Settings.canDrawOverlays(context)) {
                overlayPermissionLauncher.launch(
                  Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                  )
                )
              } else {
                val intent =
                  Intent(context, OverlayService::class.java).apply {
                    action =
                      if (sessionState.isOverlayActive) OverlayService.ACTION_STOP_OVERLAY
                      else OverlayService.ACTION_SHOW_ACTIVE
                  }
                if (sessionState.isOverlayActive) {
                  context.startService(intent)
                } else {
                  ContextCompat.startForegroundService(context, intent)
                }
              }
            },
            testTag = "row_overlay_permission",
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text =
          if (settings.translationMode == TranslationMode.SCREEN_OCR) {
            "في وضع ترجمة الشاشة، تُقرأ النصوص والألعاب والصفحات مباشرة على الشاشة دون أن تقرأ الترجمة العائمة نفسها."
          } else {
            "تأكد أنك سمحت بالعرض فوق التطبيقات ثم اضغط «ابدأ الترجمة» لترجمة يوتيوب وأي صوت داخل الهاتف مباشرة."
          },
        style = MaterialTheme.typography.bodyMedium,
        color = MutedGrayText,
        lineHeight = 20.sp,
      )

      Spacer(modifier = Modifier.height(14.dp))

      // 2-Tab Mode Selector: Live Audio vs Live Screen OCR
      Row(
        modifier = Modifier.fillMaxWidth().testTag("home_mode_selector"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        TranslationMode.entries.forEach { mode ->
          val selected = settings.translationMode == mode
          val iconRes =
            if (mode == TranslationMode.AUDIO_LIVE) R.drawable.ic_device_audio
            else R.drawable.ic_screen_scan
          Row(
            modifier =
              Modifier.weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                  if (selected) ElectricViolet.copy(alpha = 0.28f) else GlassCardFillStrong
                )
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) ElectricViolet else GlassWhiteBorder,
                  shape = RoundedCornerShape(16.dp),
                )
                .clickable { viewModel.setTranslationMode(mode) }
                .padding(horizontal = 12.dp)
                .testTag("home_mode_${mode.key}"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(id = iconRes),
              contentDescription = mode.displayName,
              tint = if (selected) SoftLavender else LightGrayText,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = mode.displayName,
              color = if (selected) CrispWhite else LightGrayText,
              fontSize = 13.sp,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              maxLines = 1,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      GlassGradientPillButton(
        text =
          if (sessionState.isRunning) stringResource(R.string.home_stop_cta)
          else if (settings.translationMode == TranslationMode.SCREEN_OCR) "ابدأ ترجمة الشاشة"
          else stringResource(R.string.home_start_cta),
        onClick = { startOrStopTranslation() },
        isActiveStop = sessionState.isRunning,
        iconRes = if (sessionState.isRunning) R.drawable.ic_stop else R.drawable.ic_arrow_forward,
        modifier = Modifier.testTag("start_stop_cta"),
      )

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
private fun PermissionServiceRow(
  iconRes: Int,
  title: String,
  subtitle: String,
  isReady: Boolean,
  badgeText: String,
  onClick: () -> Unit,
  testTag: String,
) {
  val dotColor = if (isReady) EmeraldAccent else AmberAccent
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 16.dp)
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VoidSurfaceVariant)
            .border(1.dp, GlassWhiteBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = iconRes),
          contentDescription = title,
          tint = SoftLavender,
          modifier = Modifier.size(20.dp),
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = CrispWhite,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MutedGrayText,
          maxLines = 1,
        )
      }
    }

    Spacer(modifier = Modifier.width(10.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.clip(RoundedCornerShape(50))
          .background(dotColor.copy(alpha = 0.14f))
          .border(1.dp, dotColor.copy(alpha = 0.45f), RoundedCornerShape(50))
          .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
      Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = badgeText,
        color = if (isReady) EmeraldAccent else AmberAccent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

fun formatDurationArabic(durationSeconds: Long): String {
  if (durationSeconds < 60L) {
    return "${durationSeconds.coerceAtLeast(1L)} ثانية"
  }
  val minutes = durationSeconds / 60L
  val seconds = durationSeconds % 60L
  return if (seconds > 0L) "$minutes د $seconds ث" else "$minutes دقيقة"
}
