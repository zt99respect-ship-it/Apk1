package com.livetranslator.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.OverlaySwatchColors
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidBackground
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.OverlaySettingsViewModel
import kotlin.math.roundToInt

@Composable
fun OverlaySettingsScreen(viewModel: OverlaySettingsViewModel) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()

  Box(
    modifier = Modifier.fillMaxSize().background(VoidBackground).testTag("overlay_settings_screen"),
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
      Text(
        text = "تخصيص النافذة العائمة",
        style = MaterialTheme.typography.headlineMedium,
        color = LavenderWhite,
      )

      val previewContainer =
        when (settings.overlayBackground) {
          OverlayBackground.NONE -> Color.Transparent
          OverlayBackground.GLASS -> VoidSurface.copy(alpha = 0.78f)
          OverlayBackground.SOLID -> VoidSurface
        }
      val previewBorder =
        when (settings.overlayBackground) {
          OverlayBackground.NONE -> BorderStroke(1.dp, VioletOutline.copy(alpha = 0.25f))
          OverlayBackground.GLASS -> BorderStroke(1.dp, VioletOutline)
          OverlayBackground.SOLID -> BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.65f))
        }

      Surface(
        modifier = Modifier.fillMaxWidth().testTag("overlay_live_preview"),
        shape = RoundedCornerShape(settings.overlayCornerRadiusDp.dp),
        color = previewContainer,
        border = previewBorder,
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "معاينة مباشرة • ${settings.overlayPosition.label}",
              style = MaterialTheme.typography.labelSmall,
              color = MutedViolet,
            )
            Icon(
              painter = painterResource(id = R.drawable.ic_bubble),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(16.dp),
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "مرحباً بكم في البث المباشر — الترجمة تظهر بهذا الشكل",
            fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont),
            fontSize = settings.overlayFontSizeSp.sp,
            lineHeight = (settings.overlayFontSizeSp * 1.35f).sp,
            color = Color(settings.overlayTextColorArgb),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }

      // Position selector
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("موضع البطاقة", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OverlayPosition.entries.forEach { position ->
              val selected = settings.overlayPosition == position
              SegmentPill(
                label = position.label,
                selected = selected,
                modifier = Modifier.weight(1f).testTag("pos_${position.name.lowercase()}"),
                onClick = { viewModel.setPosition(position) },
              )
            }
          }
        }
      }

      // Background selector
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("نمط الخلفية", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OverlayBackground.entries.forEach { bg ->
              val selected = settings.overlayBackground == bg
              SegmentPill(
                label = bg.label,
                selected = selected,
                modifier = Modifier.weight(1f).testTag("bg_${bg.name.lowercase()}"),
                onClick = { viewModel.setBackground(bg) },
              )
            }
          }
        }
      }

      // Font size slider 14-60
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text("حجم الخط", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
            Text(
              text = "${settings.overlayFontSizeSp.roundToInt()}",
              style = MaterialTheme.typography.labelLarge,
              color = SoftLavender,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                viewModel.setFontSizeSp((settings.overlayFontSizeSp - 2f).coerceIn(14f, 60f))
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_minus),
                contentDescription = "تصغير الخط",
                tint = SoftLavender,
              )
            }
            Slider(
              value = settings.overlayFontSizeSp,
              onValueChange = { viewModel.setFontSizeSp(it) },
              valueRange = 14f..60f,
              modifier = Modifier.weight(1f).testTag("font_size_slider"),
              colors =
                SliderDefaults.colors(
                  thumbColor = ElectricViolet,
                  activeTrackColor = ElectricViolet,
                  inactiveTrackColor = VoidSurfaceVariant,
                ),
            )
            IconButton(
              onClick = {
                viewModel.setFontSizeSp((settings.overlayFontSizeSp + 2f).coerceIn(14f, 60f))
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = "تكبير الخط",
                tint = SoftLavender,
              )
            }
          }
        }
      }

      // Text color picker
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("لون النص", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            OverlaySwatchColors.forEach { (name, argb) ->
              val selected = settings.overlayTextColorArgb == argb
              Box(
                modifier =
                  Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                      width = if (selected) 3.dp else 1.dp,
                      color = if (selected) ElectricViolet else VioletOutline,
                      shape = CircleShape,
                    )
                    .clickable { viewModel.setTextColorArgb(argb) }
                    .testTag("color_swatch_$name"),
                contentAlignment = Alignment.Center,
              ) {
                if (selected) {
                  Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = "تم اختيار $name",
                    tint = VoidBackground,
                    modifier = Modifier.size(20.dp),
                  )
                }
              }
            }
          }
        }
      }

      // 6 Arabic fonts
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("الخط العربي", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
          ArabicFontOption.entries.forEach { fontOption ->
            val selected = settings.overlayFont == fontOption
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(if (selected) ElectricViolet.copy(alpha = 0.22f) else VoidSurfaceVariant)
                  .border(
                    1.dp,
                    if (selected) ElectricViolet else VioletOutline,
                    RoundedCornerShape(14.dp),
                  )
                  .clickable { viewModel.setFont(fontOption) }
                  .padding(horizontal = 14.dp, vertical = 12.dp)
                  .testTag("font_option_${fontOption.key}"),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = fontOption.displayName,
                  style = MaterialTheme.typography.labelLarge,
                  color = LavenderWhite,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = fontOption.arabicPreview,
                  fontFamily = ArabicFontRegistry.fontFamilyFor(fontOption),
                  fontSize = 15.sp,
                  color = SoftLavender,
                )
              }
              if (selected) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_check),
                  contentDescription = "محدد",
                  tint = ElectricViolet,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
      }

      // Corner radius slider
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("استدارة الحواف", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
            Text(
              text = "${settings.overlayCornerRadiusDp.roundToInt()} dp",
              style = MaterialTheme.typography.labelLarge,
              color = SoftLavender,
            )
          }
          Slider(
            value = settings.overlayCornerRadiusDp,
            onValueChange = { viewModel.setCornerRadiusDp(it) },
            valueRange = 0f..32f,
            modifier = Modifier.fillMaxWidth().testTag("corner_radius_slider"),
            colors =
              SliderDefaults.colors(
                thumbColor = ElectricViolet,
                activeTrackColor = ElectricViolet,
                inactiveTrackColor = VoidSurfaceVariant,
              ),
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun SegmentPill(
  label: String,
  selected: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      modifier
        .height(48.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(if (selected) ElectricViolet else VoidSurfaceVariant)
        .border(1.dp, if (selected) SoftLavender else VioletOutline, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = LavenderWhite,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
    )
  }
}
