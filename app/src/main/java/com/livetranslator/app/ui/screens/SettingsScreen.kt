package com.livetranslator.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.ui.theme.AppBrandBadge
import com.livetranslator.app.ui.theme.CircularGlassIconButton
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.GlassCardFillStrong
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.GlassWhiteBorder
import com.livetranslator.app.ui.theme.GlowingScreenTitle
import com.livetranslator.app.ui.theme.LightGrayText
import com.livetranslator.app.ui.theme.MutedGrayText
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.AccountViewModel

@Composable
fun SettingsScreen(
  viewModel: AccountViewModel,
  onNavigateToTranslationStyle: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val settings by viewModel.settings.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier.fillMaxSize().testTag("settings_screen"),
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularGlassIconButton(
          iconRes = R.drawable.ic_arrow_back,
          contentDescription = "رجوع",
          onClick = onBack,
          modifier = Modifier.testTag("settings_back_button"),
        )
        AppBrandBadge()
      }

      GlowingScreenTitle(text = "الإعدادات", fontSizeSp = 30)

      // Overlay position card
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "موضع صندوق الترجمة",
            style = MaterialTheme.typography.titleLarge,
            color = CrispWhite,
          )
          Text(
            text = "اختر مكان ظهور صندوق الترجمة العائم عند فتحه فوق التطبيقات.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedGrayText,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OverlayPosition.entries.forEach { pos ->
              val selected = settings.overlayPosition == pos
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                      if (selected) ElectricViolet.copy(alpha = 0.25f) else GlassCardFillStrong
                    )
                    .border(
                      1.dp,
                      if (selected) ElectricViolet else GlassWhiteBorder,
                      RoundedCornerShape(14.dp),
                    )
                    .clickable { viewModel.setOverlayPosition(pos) },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = pos.label,
                  color = if (selected) CrispWhite else LightGrayText,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  fontSize = 14.sp,
                )
              }
            }
          }
        }
      }

      // Behavior toggles
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("تخطي الكلام العربي", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
              Text(
                "تجاهل الجمل العربية تلقائياً دون إعادة ترجمتها.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGrayText,
              )
            }
            Switch(
              checked = settings.skipArabic,
              onCheckedChange = { viewModel.setSkipArabic(it) },
              modifier = Modifier.testTag("skip_arabic_switch"),
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = CrispWhite,
                  checkedTrackColor = ElectricViolet,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }

          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "استعادة الفقاعة عند التشغيل",
                style = MaterialTheme.typography.titleMedium,
                color = CrispWhite,
              )
              Text(
                "إظهار الفقاعة العائمة تلقائياً بعد إعادة تشغيل الجهاز.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGrayText,
              )
            }
            Switch(
              checked = settings.autoRestartOnBoot,
              onCheckedChange = { viewModel.setAutoRestartOnBoot(it) },
              modifier = Modifier.testTag("boot_restart_switch"),
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = CrispWhite,
                  checkedTrackColor = ElectricViolet,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }
        }
      }

      // Link to Translation Style
      GlassSurfaceCard(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onNavigateToTranslationStyle)
            .testTag("settings_style_link"),
        cornerRadius = 24.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_brush),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("شكل الترجمة والألوان", style = MaterialTheme.typography.titleMedium, color = CrispWhite)
              Text(
                "تعديل الخط والحجم والشفافية ولون الصندوق.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGrayText,
              )
            }
          }
          Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = LightGrayText,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
