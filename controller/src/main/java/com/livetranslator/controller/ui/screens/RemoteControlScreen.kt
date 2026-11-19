package com.livetranslator.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.controller.R
import com.livetranslator.controller.data.AppControlConfigDoc
import com.livetranslator.controller.ui.theme.AmberAccent
import com.livetranslator.controller.ui.theme.ControllerGlassCard
import com.livetranslator.controller.ui.theme.ControllerGradientPillButton
import com.livetranslator.controller.ui.theme.ControllerTitle
import com.livetranslator.controller.ui.theme.CrispWhite
import com.livetranslator.controller.ui.theme.ElectricViolet
import com.livetranslator.controller.ui.theme.EmeraldAccent
import com.livetranslator.controller.ui.theme.GlassWhiteBorder
import com.livetranslator.controller.ui.theme.LightGrayText
import com.livetranslator.controller.ui.theme.MutedGrayText
import com.livetranslator.controller.ui.theme.SoftLavender
import com.livetranslator.controller.ui.theme.VoidSurfaceVariant
import com.livetranslator.controller.ui.viewmodel.ControllerConsoleViewModel
import kotlin.math.roundToInt

@Composable
fun RemoteControlScreen(
  viewModel: ControllerConsoleViewModel,
  onSignOut: () -> Unit,
) {
  val remote by viewModel.appControl.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedback by viewModel.statusFeedback.collectAsStateWithLifecycle()

  var maintenanceEnabled by remember { mutableStateOf(remote.maintenanceEnabled) }
  var maintenanceMessage by remember { mutableStateOf(remote.maintenanceMessage) }
  var allowAudioLive by remember { mutableStateOf(remote.allowAudioLive) }
  var allowScreenOcr by remember { mutableStateOf(remote.allowScreenOcr) }
  var allowOverlayBubble by remember { mutableStateOf(remote.allowOverlayBubble) }
  var forceSkipArabic by remember { mutableStateOf(remote.forceSkipArabic) }
  var forcedSpeechEngine by remember { mutableStateOf(remote.forcedSpeechEngine) }
  var forcedSourceLanguage by remember { mutableStateOf(remote.forcedSourceLanguage) }
  var chunkSeconds by remember { mutableFloatStateOf(remote.remoteAudioChunkSeconds.toFloat()) }
  var ocrIntervalMs by remember { mutableFloatStateOf(remote.remoteScreenScanIntervalMs.toFloat()) }
  var showGlobalBanner by remember { mutableStateOf(remote.showGlobalBanner) }
  var bannerTitle by remember { mutableStateOf(remote.globalBannerTitle) }
  var bannerMessage by remember { mutableStateOf(remote.globalBannerMessage) }
  var githubRepoSlug by remember { mutableStateOf(remote.githubRepoSlug) }
  var otaFeedUrl by remember { mutableStateOf(remote.otaFeedUrl) }

  LaunchedEffect(remote) {
    maintenanceEnabled = remote.maintenanceEnabled
    maintenanceMessage = remote.maintenanceMessage
    allowAudioLive = remote.allowAudioLive
    allowScreenOcr = remote.allowScreenOcr
    allowOverlayBubble = remote.allowOverlayBubble
    forceSkipArabic = remote.forceSkipArabic
    forcedSpeechEngine = remote.forcedSpeechEngine
    forcedSourceLanguage = remote.forcedSourceLanguage
    chunkSeconds = remote.remoteAudioChunkSeconds.toFloat().coerceIn(2f, 15f)
    ocrIntervalMs = remote.remoteScreenScanIntervalMs.toFloat().coerceIn(450f, 3000f)
    showGlobalBanner = remote.showGlobalBanner
    bannerTitle = remote.globalBannerTitle
    bannerMessage = remote.globalBannerMessage
    githubRepoSlug = remote.githubRepoSlug
    otaFeedUrl = remote.otaFeedUrl
  }

  Box(
    modifier = Modifier.fillMaxSize().testTag("remote_control_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 620.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ControllerTitle("التحكم الشامل بالتطبيق")
        OutlinedButton(onClick = onSignOut, shape = RoundedCornerShape(12.dp)) {
          Text("خروج", color = SoftLavender, fontSize = 12.sp)
        }
      }

      if (!feedback.isNullOrBlank()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(EmeraldAccent.copy(alpha = 0.14f))
              .border(1.dp, EmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
              .padding(12.dp)
        ) {
          Text(text = feedback.orEmpty(), color = EmeraldAccent, fontWeight = FontWeight.Bold)
        }
      }

      // 1. Maintenance & Features Lock Card
      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("الميزات ووضع الصيانة", style = MaterialTheme.typography.titleLarge, color = CrispWhite)

          ControlToggleRow(
            title = "إيقاف التطبيق مؤقتاً (وضع الصيانة)",
            subtitle = "يغلق التطبيق الأصلي لدى جميع المستخدمين برسالة الصيانة",
            checked = maintenanceEnabled,
            onCheckedChange = { maintenanceEnabled = it },
          )

          if (maintenanceEnabled) {
            OutlinedTextField(
              value = maintenanceMessage,
              onValueChange = { maintenanceMessage = it },
              minLines = 2,
              label = { Text("رسالة الصيانة للمستخدمين", color = AmberAccent) },
              modifier = Modifier.fillMaxWidth(),
              colors = adminFieldColors(),
            )
          }

          HorizontalDivider(color = GlassWhiteBorder)

          ControlToggleRow(
            title = "السماح بترجمة الصوت الفورية",
            subtitle = "تفعيل أو تعطيل التقاط صوت الهاتف والميكروفون عن بُعد",
            checked = allowAudioLive,
            onCheckedChange = { allowAudioLive = it },
          )

          ControlToggleRow(
            title = "السماح بترجمة الشاشة (OCR)",
            subtitle = "تفعيل أو تعطيل وضع ترجمة الشاشة والألعاب",
            checked = allowScreenOcr,
            onCheckedChange = { allowScreenOcr = it },
          )

          ControlToggleRow(
            title = "السماح بالعلامة المائية العائمة",
            subtitle = "إظهار أو إيقاف الصندوق العائم فوق التطبيقات فوراً",
            checked = allowOverlayBubble,
            onCheckedChange = { allowOverlayBubble = it },
          )

          ControlToggleRow(
            title = "إجبار تخطي الكلام العربي",
            subtitle = "يفرض تجاهل الكلام العربي ومنع تحوله لنصوص مشوشة",
            checked = forceSkipArabic,
            onCheckedChange = { forceSkipArabic = it },
          )
        }
      }

      // 2. Engine & Performance Tuning Card
      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("فرض المحرك والسرعة عن بُعد", style = MaterialTheme.typography.titleLarge, color = CrispWhite)

          Text("المحرك المفروض:", color = SoftLavender, style = MaterialTheme.typography.labelLarge)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              "" to "اختيار المستخدم",
              "flexible_auto" to "مرن لحظي",
              "vosk_local" to "Vosk محلي",
            ).forEach { (key, label) ->
              SelectChip(
                text = label,
                selected = forcedSpeechEngine == key,
                onClick = { forcedSpeechEngine = key },
                modifier = Modifier.weight(1f),
              )
            }
          }

          Text("اللغة المصدر المفروضة:", color = SoftLavender, style = MaterialTheme.typography.labelLarge)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              "" to "حر",
              "auto" to "تلقائي",
              "en" to "إنجليزي",
              "tr" to "تركي",
            ).forEach { (key, label) ->
              SelectChip(
                text = label,
                selected = forcedSourceLanguage == key,
                onClick = { forcedSourceLanguage = key },
                modifier = Modifier.weight(1f),
              )
            }
          }

          Text(
            text = "نافذة التقطيع الصوتي: ${chunkSeconds.roundToInt()} ثوانٍ",
            color = CrispWhite,
            fontWeight = FontWeight.Bold,
          )
          Slider(
            value = chunkSeconds,
            onValueChange = { chunkSeconds = it },
            valueRange = 2f..15f,
            steps = 12,
            colors = SliderDefaults.colors(thumbColor = ElectricViolet, activeTrackColor = ElectricViolet),
          )

          Text(
            text = "سرعة فحص الشاشة (OCR): ${ocrIntervalMs.roundToInt()} مللي ثانية",
            color = CrispWhite,
            fontWeight = FontWeight.Bold,
          )
          Slider(
            value = ocrIntervalMs,
            onValueChange = { ocrIntervalMs = it },
            valueRange = 450f..3000f,
            colors = SliderDefaults.colors(thumbColor = ElectricViolet, activeTrackColor = ElectricViolet),
          )
        }
      }

      // 3. Global Banner & GitHub Binding Card
      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("البانر العام والربط مع GitHub", style = MaterialTheme.typography.titleLarge, color = CrispWhite)

          ControlToggleRow(
            title = "إظهار بانر إعلاني أعلى التطبيق الأصلي",
            subtitle = "يظهر مباشرة فوق الشاشات الرئيسية لجميع المستخدمين",
            checked = showGlobalBanner,
            onCheckedChange = { showGlobalBanner = it },
          )

          if (showGlobalBanner) {
            OutlinedTextField(
              value = bannerTitle,
              onValueChange = { bannerTitle = it },
              singleLine = true,
              label = { Text("عنوان البانر", color = SoftLavender) },
              modifier = Modifier.fillMaxWidth(),
              colors = adminFieldColors(),
            )
            OutlinedTextField(
              value = bannerMessage,
              onValueChange = { bannerMessage = it },
              minLines = 2,
              label = { Text("نص البانر", color = SoftLavender) },
              modifier = Modifier.fillMaxWidth(),
              colors = adminFieldColors(),
            )
          }

          OutlinedTextField(
            value = githubRepoSlug,
            onValueChange = { githubRepoSlug = it },
            singleLine = true,
            label = { Text("مستودع GitHub (owner/repo)", color = SoftLavender) },
            placeholder = { Text("username/live-translator", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth().testTag("github_repo_input"),
            colors = adminFieldColors(),
          )

          OutlinedTextField(
            value = otaFeedUrl,
            onValueChange = { otaFeedUrl = it },
            singleLine = true,
            label = { Text("رابط JSON للتحديث التلقائي (اختياري)", color = SoftLavender) },
            placeholder = { Text("https://raw.githubusercontent.com/.../updates.json", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth(),
            colors = adminFieldColors(),
          )
        }
      }

      ControllerGradientPillButton(
        text = if (isBusy) "جاري الحفظ..." else "حفظ وتطبيق التحكم على جميع المستخدمين",
        onClick = {
          viewModel.saveAppControl(
            AppControlConfigDoc(
              maintenanceEnabled = maintenanceEnabled,
              maintenanceMessage = maintenanceMessage,
              allowAudioLive = allowAudioLive,
              allowScreenOcr = allowScreenOcr,
              allowOverlayBubble = allowOverlayBubble,
              forceSkipArabic = forceSkipArabic,
              forcedSpeechEngine = forcedSpeechEngine,
              forcedSourceLanguage = forcedSourceLanguage,
              remoteAudioChunkSeconds = chunkSeconds.roundToInt().coerceIn(2, 15),
              remoteScreenScanIntervalMs = ocrIntervalMs.roundToInt().coerceIn(450, 3000),
              showGlobalBanner = showGlobalBanner,
              globalBannerTitle = bannerTitle,
              globalBannerMessage = bannerMessage,
              githubRepoSlug = githubRepoSlug,
              otaFeedUrl = otaFeedUrl,
            )
          )
        },
        enabled = !isBusy,
        iconRes = R.drawable.ic_check,
        modifier = Modifier.testTag("save_app_control_button"),
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun ControlToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
      Text(title, color = CrispWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
      Text(subtitle, color = MutedGrayText, fontSize = 12.sp)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = CrispWhite,
          checkedTrackColor = ElectricViolet,
          uncheckedTrackColor = VoidSurfaceVariant,
        ),
    )
  }
}

@Composable
private fun SelectChip(
  text: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .height(42.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(if (selected) ElectricViolet.copy(alpha = 0.28f) else VoidSurfaceVariant)
        .border(
          width = if (selected) 1.5.dp else 1.dp,
          color = if (selected) ElectricViolet else GlassWhiteBorder,
          shape = RoundedCornerShape(12.dp),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = if (selected) CrispWhite else LightGrayText,
      fontSize = 11.sp,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      maxLines = 1,
    )
  }
}

@Composable
internal fun adminFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricViolet,
    unfocusedBorderColor = GlassWhiteBorder,
    focusedTextColor = CrispWhite,
    unfocusedTextColor = CrispWhite,
  )
