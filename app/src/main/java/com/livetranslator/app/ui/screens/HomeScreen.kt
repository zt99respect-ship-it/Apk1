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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.service.OverlayService
import com.livetranslator.app.service.TranslationForegroundService
import com.livetranslator.app.ui.theme.AmberAccent
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.BrightAmethyst
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.EmeraldAccent
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.HomeRadialBottomRight
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.RoseError
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.theme.homeRadialBackground
import com.livetranslator.app.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
  viewModel: MainViewModel,
  onNavigateToSettings: () -> Unit,
  onNavigateToAbout: () -> Unit,
) {
  val context = LocalContext.current
  val app = context.applicationContext as LiveTranslatorApp

  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
  val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()
  val latestHistory by viewModel.latestTranslation.collectAsStateWithLifecycle()

  var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    hasOverlayPermission = Settings.canDrawOverlays(context)
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
        app.sessionManager.publishError("تم إلغاء إذن التقاط الشاشة", expandOverlay = false)
      }
    }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START_MIC_ONLY
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      } else {
        app.sessionManager.publishError("يجب منح إذن الميكروفون للتجربة السريعة", expandOverlay = false)
      }
    }

  val infinite = rememberInfiniteTransition(label = "fabPulse")
  val pulseScale by
    infinite.animateFloat(
      initialValue = 1f,
      targetValue = if (sessionState.isRunning) 1.18f else 1.05f,
      animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
      label = "fabRing",
    )

  val activeArabicText =
    sessionState.lastTranslatedText.ifBlank { latestHistory?.translatedText.orEmpty() }
  val activeSourceText =
    sessionState.lastPartialSource.ifBlank {
      sessionState.lastSourceText.ifBlank { latestHistory?.sourceText.orEmpty() }
    }

  val statusChipColor =
    when {
      sessionState.lastError != null || modelsState.errorMessage != null -> RoseError
      modelsState.isDownloading -> AmberAccent
      sessionState.isRunning -> EmeraldAccent
      modelsState.allReady -> SoftLavender
      else -> AmberAccent
    }

  val statusChipLabel =
    when {
      sessionState.lastError != null -> sessionState.lastError ?: "حدث خطأ"
      modelsState.isDownloading -> modelsState.stageMessage
      modelsState.errorMessage != null -> modelsState.errorMessage ?: "تعذر تنزيل النماذج"
      sessionState.isBubbleSnoozed -> "الفقاعة مخفية مؤقتاً (5 د)"
      sessionState.isRunning -> "${sessionState.statusMessage} • ${sessionState.engineMode}"
      modelsState.allReady -> "جاهز للعمل 100% بدون إنترنت"
      else -> "يلزم تنزيل النماذج المحلية لأول مرة"
    }

  Box(
    modifier = Modifier.fillMaxSize().homeRadialBackground().testTag("home_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Top header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = LavenderWhite,
          )
          Text(
            text = stringResource(R.string.app_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedViolet,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IconButton(
            onClick = {
              if (!Settings.canDrawOverlays(context)) {
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
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(VoidSurfaceVariant)
                .border(1.dp, VioletOutline, CircleShape)
                .testTag("toggle_bubble_button"),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_bubble),
              contentDescription = "إظهار أو إخفاء الفقاعة العائمة",
              tint = if (sessionState.isOverlayActive) ElectricViolet else MutedViolet,
            )
          }

          IconButton(
            onClick = onNavigateToAbout,
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(VoidSurfaceVariant)
                .border(1.dp, VioletOutline, CircleShape)
                .testTag("about_button"),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_info),
              contentDescription = "حول التطبيق",
              tint = MutedViolet,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Status chip
      Row(
        modifier =
          Modifier.clip(RoundedCornerShape(50))
            .background(statusChipColor.copy(alpha = 0.14f))
            .border(1.dp, statusChipColor.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("status_chip"),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusChipColor))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = statusChipLabel,
          color = LavenderWhite,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 2,
        )
      }

      // Offline Model Download / Readiness Card when not ready or downloading
      if (modelsState.isDownloading || !modelsState.allReady) {
        Spacer(modifier = Modifier.height(12.dp))
        GlassSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("offline_models_card"), cornerRadius = 16.dp) {
          Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "النماذج المحلية (Vosk + ML Kit)",
                style = MaterialTheme.typography.titleMedium,
                color = LavenderWhite,
              )
              if (modelsState.isDownloading) {
                Text(
                  text = "${modelsState.progressPercent}%",
                  style = MaterialTheme.typography.labelLarge,
                  color = SoftLavender,
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = modelsState.errorMessage ?: modelsState.stageMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = if (modelsState.errorMessage != null) RoseError else MutedViolet,
            )
            if (modelsState.isDownloading) {
              Spacer(modifier = Modifier.height(10.dp))
              LinearProgressIndicator(
                progress = { (modelsState.progressPercent / 100f).coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = ElectricViolet,
                trackColor = VoidSurfaceVariant,
              )
            } else {
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = { viewModel.prepareModels(settings.sourceLanguageCode) },
                  colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).height(44.dp).testTag("download_models_button"),
                ) {
                  Text("تنزيل النماذج الآن", color = LavenderWhite)
                }
                TextButton(onClick = onNavigateToSettings, modifier = Modifier.height(44.dp)) {
                  Text("اللغة", color = SoftLavender)
                }
              }
            }
          }
        }
      }

      // Live audio level telemetry when running
      if (sessionState.isRunning) {
        Spacer(modifier = Modifier.height(12.dp))
        GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
          Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (sessionState.audioLevel > 0.05f) "يلتقط صوتاً مسموعاً الآن"
                  else "في انتظار الصوت...",
                style = MaterialTheme.typography.labelLarge,
                color = if (sessionState.audioLevel > 0.05f) EmeraldAccent else MutedViolet,
              )
              Text(
                text = "الجمل المترجمة: ${sessionState.segmentsTranslated}",
                style = MaterialTheme.typography.labelSmall,
                color = SoftLavender,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(8.dp)
                  .clip(CircleShape)
                  .background(VoidSurfaceVariant)
            ) {
              Box(
                modifier =
                  Modifier.fillMaxWidth(sessionState.audioLevel.coerceAtLeast(0.03f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(ElectricViolet, EmeraldAccent)))
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Big Circular Start / Stop FAB
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(204.dp)) {
        Canvas(modifier = Modifier.size(204.dp)) {
          drawCircle(
            color =
              (if (sessionState.isRunning) RoseError else ElectricViolet).copy(
                alpha = if (sessionState.isRunning) 0.28f else 0.18f
              ),
            radius = (size.minDimension / 2f) * 0.88f * pulseScale,
            style = Stroke(width = 2.dp.toPx()),
          )
          drawCircle(
            color = SoftLavender.copy(alpha = 0.12f),
            radius = (size.minDimension / 2f) * 0.74f * pulseScale,
            style = Stroke(width = 1.5.dp.toPx()),
          )
        }

        Box(
          modifier =
            Modifier.size(144.dp)
              .scale(if (sessionState.isTranslating) 1.04f else 1f)
              .shadow(
                elevation = 20.dp,
                shape = CircleShape,
                ambientColor = ElectricViolet,
                spotColor = BrightAmethyst,
              )
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  colors =
                    if (sessionState.isRunning) {
                      listOf(RoseError, BrightAmethyst)
                    } else {
                      listOf(ElectricViolet, BrightAmethyst, HomeRadialBottomRight)
                    }
                )
              )
              .border(2.dp, LavenderWhite.copy(alpha = 0.35f), CircleShape)
              .clickable {
                if (sessionState.isRunning) {
                  val stopIntent =
                    Intent(context, TranslationForegroundService::class.java).apply {
                      action = TranslationForegroundService.ACTION_STOP
                    }
                  context.startService(stopIntent)
                } else {
                  if (!Settings.canDrawOverlays(context)) {
                    overlayPermissionLauncher.launch(
                      Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}"),
                      )
                    )
                    return@clickable
                  }
                  val mgr =
                    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                      as MediaProjectionManager
                  projectionLauncher.launch(mgr.createScreenCaptureIntent())
                }
              }
              .testTag("start_stop_fab"),
          contentAlignment = Alignment.Center,
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              painter =
                painterResource(
                  id = if (sessionState.isRunning) R.drawable.ic_stop else R.drawable.ic_play
                ),
              contentDescription =
                stringResource(
                  if (sessionState.isRunning) R.string.home_stop_capture
                  else R.string.home_start_capture
                ),
              tint = LavenderWhite,
              modifier = Modifier.size(40.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = if (sessionState.isRunning) "إيقاف" else "بدء صوت الشاشة",
              style = MaterialTheme.typography.labelLarge,
              color = LavenderWhite,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      if (!sessionState.isRunning) {
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
          onClick = {
            if (!Settings.canDrawOverlays(context)) {
              overlayPermissionLauncher.launch(
                Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:${context.packageName}"),
                )
              )
              return@OutlinedButton
            }
            val hasMic =
              ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (hasMic) {
              val serviceIntent =
                Intent(context, TranslationForegroundService::class.java).apply {
                  action = TranslationForegroundService.ACTION_START_MIC_ONLY
                }
              ContextCompat.startForegroundService(context, serviceIntent)
            } else {
              micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.height(44.dp).testTag("start_mic_button"),
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_mic),
            contentDescription = null,
            tint = SoftLavender,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("تجربة سريعة عبر الميكروفون", color = SoftLavender)
        }
      }

      if (!hasOverlayPermission) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = {
            overlayPermissionLauncher.launch(
              Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
              )
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text("منح إذن النافذة العائمة", color = SoftLavender)
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Mini live preview card
      GlassSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("mini_preview_card"),
        cornerRadius = 20.dp,
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.home_last_translation),
              style = MaterialTheme.typography.titleMedium,
              color = LavenderWhite,
            )
            val langName = SupportedLanguages.findByCode(settings.sourceLanguageCode).displayName
            Text(
              text = "$langName ← العربية",
              style = MaterialTheme.typography.labelSmall,
              color = SoftLavender,
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(ElectricViolet.copy(alpha = 0.2f))
                  .padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (activeArabicText.isBlank() && activeSourceText.isBlank()) {
            Text(
              text = stringResource(R.string.home_empty_preview),
              style = MaterialTheme.typography.bodyMedium,
              color = MutedViolet,
            )
          } else {
            if (activeArabicText.isNotBlank()) {
              Text(
                text = activeArabicText,
                fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont),
                fontSize = 20.sp,
                lineHeight = 30.sp,
                color = Color(settings.overlayTextColorArgb),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
              )
            }
            if (activeSourceText.isNotBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = activeSourceText,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedViolet,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Clear guide on offline operation & floating bubble
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = "ترجمة محلية 100% بدون إنترنت ولا مفاتيح",
            style = MaterialTheme.typography.labelLarge,
            color = LavenderWhite,
          )
          Text(
            text = "• تفريغ الصوت يتم محلياً عبر Vosk، والترجمة للعربية عبر Google ML Kit مجاناً وبلا حدود.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedViolet,
          )
          Text(
            text = "• اسحب الفقاعة العائمة لأي مكان • ضغطة لفتح البطاقة (تُخفى بعد 6 ثوانٍ) • ضغطة مطولة للإخفاء 5 دقائق.",
            style = MaterialTheme.typography.bodyMedium,
            color = SoftLavender,
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
