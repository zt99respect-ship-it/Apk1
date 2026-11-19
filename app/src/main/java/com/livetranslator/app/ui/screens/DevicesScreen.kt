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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.R
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.SpeechEnginePreference
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.model.TranslationMode
import com.livetranslator.app.service.TranslationForegroundService
import com.livetranslator.app.ui.theme.AmberAccent
import com.livetranslator.app.ui.theme.AppBrandBadge
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
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.DevicesViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
  val context = LocalContext.current
  val app = context.applicationContext as LiveTranslatorApp
  val focusManager = LocalFocusManager.current
  val textToolbar = LocalTextToolbar.current

  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
  val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()

  var langMenuExpanded by remember { mutableStateOf(false) }
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

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
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
      }
    }

  fun startOrStopCapture() {
    if (sessionState.isRunning) {
      val stopIntent =
        Intent(context, TranslationForegroundService::class.java).apply {
          action = TranslationForegroundService.ACTION_STOP
        }
      context.startService(stopIntent)
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

  val selectedLanguage = SupportedLanguages.findByCode(settings.sourceLanguageCode)
  val resolvedOfflineLang = SupportedLanguages.resolveVoskLanguage(settings.sourceLanguageCode)

  Box(
    modifier = Modifier.fillMaxSize().testTag("devices_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Top Header matching Screenshot 4
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        AppBrandBadge()
      }

      GlowingScreenTitle(text = "أجهزتي", fontSizeSp = 30)

      // Glass Card 0: Translation Mode Selector (Live Audio vs Screen OCR)
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("translation_mode_card"), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "وضع الترجمة الفورية",
            style = MaterialTheme.typography.titleLarge,
            color = CrispWhite,
          )
          Text(
            text = "بدّل بين ترجمة أصوات الفيديوهات والبثوث أو قراءة نصوص الشاشة والألعاب مباشرة.",
            style = MaterialTheme.typography.bodyMedium,
            color = LightGrayText,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            TranslationMode.entries.forEach { mode ->
              val selected = settings.translationMode == mode
              val iconRes =
                if (mode == TranslationMode.AUDIO_LIVE) R.drawable.ic_device_audio
                else R.drawable.ic_screen_scan
              Column(
                modifier =
                  Modifier.weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                      if (selected) ElectricViolet.copy(alpha = 0.22f)
                      else VoidSurfaceVariant.copy(alpha = 0.55f)
                    )
                    .border(
                      width = if (selected) 1.5.dp else 1.dp,
                      color = if (selected) ElectricViolet else GlassWhiteBorder,
                      shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { viewModel.setTranslationMode(mode) }
                    .padding(12.dp)
                    .testTag("devices_mode_${mode.key}"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  painter = painterResource(id = iconRes),
                  contentDescription = mode.displayName,
                  tint = if (selected) SoftLavender else LightGrayText,
                  modifier = Modifier.size(22.dp),
                )
                Text(
                  text = mode.displayName,
                  color = CrispWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 14.sp,
                )
                Text(
                  text = mode.subtitle,
                  color = MutedGrayText,
                  fontSize = 11.sp,
                  maxLines = 2,
                )
              }
            }
          }
        }
      }

      // Glass Card 1: Phone Audio Capture Mode & Live Status
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("phone_audio_card"), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_device_audio),
                contentDescription = null,
                tint = SoftLavender,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "صوت الهاتف والالتقاط",
                style = MaterialTheme.typography.titleLarge,
                color = CrispWhite,
              )
            }

            val badgeColor = if (sessionState.isRunning) EmeraldAccent else SoftLavender
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .background(badgeColor.copy(alpha = 0.14f))
                  .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(badgeColor))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (sessionState.isRunning) "يعمل الآن" else "جاهز",
                color = badgeColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Text(
            text = "اختر مصدر الصوت الذي تريد ترجمته مباشرة إلى اللغة العربية:",
            style = MaterialTheme.typography.bodyMedium,
            color = LightGrayText,
          )

          AudioSourcePreference.entries.forEach { option ->
            val selected = settings.audioSourcePreference == option
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(
                    if (selected) ElectricViolet.copy(alpha = 0.18f) else VoidSurfaceVariant.copy(alpha = 0.55f)
                  )
                  .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) ElectricViolet else GlassWhiteBorder,
                    shape = RoundedCornerShape(16.dp),
                  )
                  .clickable { viewModel.setAudioSourcePreference(option) }
                  .padding(14.dp)
                  .testTag("audio_source_${option.key}"),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Box(
                modifier =
                  Modifier.size(18.dp)
                    .clip(CircleShape)
                    .background(if (selected) ElectricViolet else VoidSurface)
                    .border(1.5.dp, if (selected) SoftLavender else MutedGrayText, CircleShape),
                contentAlignment = Alignment.Center,
              ) {
                if (selected) {
                  Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(CrispWhite))
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = option.displayName,
                  color = CrispWhite,
                  style = MaterialTheme.typography.titleMedium,
                )
                Text(
                  text = option.subtitle,
                  color = MutedGrayText,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }

          if (sessionState.isRunning) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                Text("مستوى الصوت الملتقط", color = SoftLavender, style = MaterialTheme.typography.labelLarge)
                Text(sessionState.engineMode, color = LightGrayText, style = MaterialTheme.typography.labelSmall)
              }
              Box(
                modifier =
                  Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(VoidSurfaceVariant)
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
          }

          // Quick Permission Pills
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                if (!hasOverlayPermission) {
                  overlayPermissionLauncher.launch(
                    Intent(
                      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                      Uri.parse("package:${context.packageName}"),
                    )
                  )
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).height(44.dp),
            ) {
              Text(
                text = if (hasOverlayPermission) "العرض العائم ✓" else "تفعيل العائم",
                color = if (hasOverlayPermission) EmeraldAccent else AmberAccent,
                fontSize = 12.sp,
              )
            }

            OutlinedButton(
              onClick = {
                if (!hasMicPermission) {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f).height(44.dp),
            ) {
              Text(
                text = if (hasMicPermission) "الميكروفون ✓" else "إذن الميكروفون",
                color = if (hasMicPermission) EmeraldAccent else AmberAccent,
                fontSize = 12.sp,
              )
            }
          }
        }
      }

      // Glass Card 2: Flexible Language & Recognition Engine
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("language_engine_card"), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "لغة الصوت والمحرك المرن",
            style = MaterialTheme.typography.titleLarge,
            color = CrispWhite,
          )
          Text(
            text = "افتراضياً يكتشف التطبيق اللغة تلقائياً، أو يمكنك اختيار لغة محددة عند الحاجة.",
            style = MaterialTheme.typography.bodyMedium,
            color = LightGrayText,
          )

          ExposedDropdownMenuBox(
            expanded = langMenuExpanded,
            onExpandedChange = {
              focusManager.clearFocus(force = true)
              textToolbar.hide()
              langMenuExpanded = !langMenuExpanded
            },
          ) {
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                  .clip(RoundedCornerShape(16.dp))
                  .border(
                    1.dp,
                    if (langMenuExpanded) ElectricViolet else VioletOutline,
                    RoundedCornerShape(16.dp),
                  )
                  .testTag("language_dropdown"),
              color = VoidSurfaceVariant,
              shape = RoundedCornerShape(16.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = selectedLanguage.displayName,
                  color = CrispWhite,
                  style = MaterialTheme.typography.bodyLarge,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = langMenuExpanded)
              }
            }

            ExposedDropdownMenu(
              expanded = langMenuExpanded,
              onDismissRequest = { langMenuExpanded = false },
              modifier = Modifier.background(VoidSurface),
            ) {
              SupportedLanguages.all.forEach { lang ->
                DropdownMenuItem(
                  text = {
                    val label =
                      if (lang.isAuto) lang.displayName
                      else "${lang.displayName} (${lang.code.uppercase()})"
                    Text(text = label, color = CrispWhite)
                  },
                  onClick = {
                    viewModel.setSourceLanguageCode(lang.code)
                    langMenuExpanded = false
                  },
                )
              }
            }
          }

          // Engine mode selector
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            SpeechEnginePreference.entries.forEach { engineOpt ->
              val selected = settings.speechEnginePreference == engineOpt
              Box(
                modifier =
                  Modifier.weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                      if (selected) ElectricViolet.copy(alpha = 0.24f) else GlassCardFillStrong
                    )
                    .border(
                      1.dp,
                      if (selected) ElectricViolet else GlassWhiteBorder,
                      RoundedCornerShape(14.dp),
                    )
                    .clickable { viewModel.setSpeechEnginePreference(engineOpt) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text =
                    if (engineOpt == SpeechEnginePreference.FLEXIBLE_AUTO) "محرك مرن (موصى به)"
                    else "محلي (Vosk)",
                  color = if (selected) CrispWhite else LightGrayText,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                )
              }
            }
          }

          // Audio chunk slider
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("سرعة الاستجابة (طول المقطع)", color = CrispWhite, style = MaterialTheme.typography.titleMedium)
            Text("${settings.audioChunkSeconds} ث", color = SoftLavender, style = MaterialTheme.typography.titleMedium)
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                viewModel.setAudioChunkSeconds((settings.audioChunkSeconds - 1).coerceIn(2, 15))
              },
              modifier = Modifier.size(44.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_minus),
                contentDescription = "تقليل",
                tint = SoftLavender,
              )
            }
            Slider(
              value = settings.audioChunkSeconds.toFloat(),
              onValueChange = { viewModel.setAudioChunkSeconds(it.roundToInt()) },
              valueRange = 2f..15f,
              steps = 12,
              modifier = Modifier.weight(1f).testTag("chunk_size_slider"),
              colors =
                SliderDefaults.colors(
                  thumbColor = ElectricViolet,
                  activeTrackColor = ElectricViolet,
                  inactiveTrackColor = VoidSurfaceVariant,
                ),
            )
            IconButton(
              onClick = {
                viewModel.setAudioChunkSeconds((settings.audioChunkSeconds + 1).coerceIn(2, 15))
              },
              modifier = Modifier.size(44.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = "زيادة",
                tint = SoftLavender,
              )
            }
          }

          // Optional offline backup model section
          Text(
            text = modelsState.errorMessage ?: modelsState.stageMessage,
            color = if (modelsState.errorMessage != null) RoseError else MutedGrayText,
            style = MaterialTheme.typography.bodyMedium,
          )
          if (modelsState.isDownloading) {
            LinearProgressIndicator(
              progress = { (modelsState.progressPercent / 100f).coerceIn(0.02f, 1f) },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
              color = ElectricViolet,
              trackColor = VoidSurfaceVariant,
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = { viewModel.downloadModelsFor(settings.sourceLanguageCode) },
              enabled = !modelsState.isDownloading,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
              modifier = Modifier.weight(1f).height(46.dp),
            ) {
              Text(
                text =
                  if (modelsState.allReady) "الحزمة الاحتياطية جاهزة ✓"
                  else "تجهيز حزمة ${resolvedOfflineLang.displayName}",
                color = CrispWhite,
                fontSize = 12.sp,
              )
            }
            if (modelsState.voskModelReady && !modelsState.isDownloading) {
              OutlinedButton(
                onClick = { viewModel.deleteVoskModel(settings.sourceLanguageCode) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(46.dp),
              ) {
                Text("حذف", color = RoseError, fontSize = 12.sp)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      GlassGradientPillButton(
        text =
          if (sessionState.isRunning) stringResource(R.string.home_stop_cta)
          else stringResource(R.string.home_start_cta),
        onClick = { startOrStopCapture() },
        isActiveStop = sessionState.isRunning,
        iconRes = if (sessionState.isRunning) R.drawable.ic_stop else R.drawable.ic_arrow_forward,
        modifier = Modifier.testTag("devices_start_cta"),
      )

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
