package com.livetranslator.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayFontWeight
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.engine.TranslationSessionManager
import com.livetranslator.app.service.OverlayService
import com.livetranslator.app.ui.theme.AmberAccent
import com.livetranslator.app.ui.theme.AppBrandBadge
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.BoxColorSwatches
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
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.TextColorSwatches
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.OverlayStyleViewModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TranslationStyleScreen(
  viewModel: OverlayStyleViewModel,
  sessionManager: TranslationSessionManager,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val sessionState by sessionManager.state.collectAsStateWithLifecycle()

  var draftPreset by remember { mutableStateOf(settings.overlayPreset) }
  var draftFont by remember { mutableStateOf(settings.overlayFont) }
  var draftWeight by remember { mutableStateOf(settings.overlayFontWeight) }
  var draftFontSizeSp by remember { mutableFloatStateOf(settings.overlayFontSizeSp) }
  var draftTextColorArgb by remember { mutableLongStateOf(settings.overlayTextColorArgb) }
  var draftBoxColorArgb by remember { mutableLongStateOf(settings.overlayBoxColorArgb) }
  var draftBoxOpacity by remember { mutableFloatStateOf(settings.overlayBoxOpacity) }
  var initializedFromStore by remember { mutableStateOf(false) }

  LaunchedEffect(settings) {
    if (!initializedFromStore) {
      draftPreset = settings.overlayPreset
      draftFont = settings.overlayFont
      draftWeight = settings.overlayFontWeight
      draftFontSizeSp = settings.overlayFontSizeSp
      draftTextColorArgb = settings.overlayTextColorArgb
      draftBoxColorArgb = settings.overlayBoxColorArgb
      draftBoxOpacity = settings.overlayBoxOpacity
      initializedFromStore = true
    }
  }

  var colorPickerTarget by remember { mutableStateOf<String?>(null) } // "text" | "box"

  val sizePercent = (((draftFontSizeSp - 14f) / (42f - 14f)) * 80f + 20f).roundToInt().coerceIn(20, 100)
  val opacityPercent = (draftBoxOpacity * 100f).roundToInt().coerceIn(0, 100)

  Box(
    modifier = Modifier.fillMaxSize().testTag("translation_style_screen"),
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
      // Top bar with Back and Brand Badge (matching Screenshot 6 & 7)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularGlassIconButton(
          iconRes = R.drawable.ic_arrow_back,
          contentDescription = "رجوع",
          onClick = onBack,
          modifier = Modifier.testTag("style_back_button"),
        )
        AppBrandBadge()
      }

      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GlowingScreenTitle(text = "شكل الترجمة", fontSizeSp = 30)
        Text(
          text = "شوف التغيير على الصندوق العائم مباشرة.",
          color = LightGrayText,
          style = MaterialTheme.typography.bodyLarge,
        )
      }

      // Live Floating Box Preview (matching Screenshot 6)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(settings.overlayCornerRadiusDp.dp))
            .background(Color(draftBoxColorArgb).copy(alpha = draftBoxOpacity))
            .border(
              width = 1.dp,
              brush =
                Brush.linearGradient(
                  listOf(
                    CrispWhite.copy(alpha = 0.28f),
                    ElectricViolet.copy(alpha = 0.55f),
                    GlassWhiteBorder,
                  )
                ),
              shape = RoundedCornerShape(settings.overlayCornerRadiusDp.dp),
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
            .testTag("style_live_preview"),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = "هذا شكل الترجمة اللي بيظهر فوق الفيديو",
          fontFamily = ArabicFontRegistry.fontFamilyFor(draftFont),
          fontWeight = ArabicFontRegistry.composeWeightFor(draftWeight),
          fontSize = draftFontSizeSp.sp,
          lineHeight = (draftFontSizeSp * 1.4f).sp,
          color = Color(draftTextColorArgb),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      // Overlay activation banner pill matching Screenshot 6
      val overlayActive = sessionState.isOverlayActive
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
              if (overlayActive) EmeraldAccent.copy(alpha = 0.12f) else AmberAccent.copy(alpha = 0.12f)
            )
            .border(
              1.dp,
              if (overlayActive) EmeraldAccent.copy(alpha = 0.4f) else AmberAccent.copy(alpha = 0.45f),
              RoundedCornerShape(18.dp),
            )
            .clickable {
              if (!Settings.canDrawOverlays(context)) {
                context.startActivity(
                  Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                  )
                )
              } else {
                val intent =
                  Intent(context, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_SHOW_ACTIVE
                  }
                ContextCompat.startForegroundService(context, intent)
                sessionManager.setCardExpanded(true)
              }
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          painter =
            painterResource(id = if (overlayActive) R.drawable.ic_check else R.drawable.ic_warning),
          contentDescription = null,
          tint = if (overlayActive) EmeraldAccent else AmberAccent,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text =
            if (overlayActive) "الصندوق العائم مفعّل — اضغط هنا لمعاينته الآن فوق الشاشة"
            else "الصندوق العائم غير مفعّل الآن — اضغط لتشغيله ومعاينة الشكل",
          color = CrispWhite,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      // Presets Row (الأصلي / سينما / هادئ) matching Screenshot 6
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OverlayStylePreset.entries.forEach { preset ->
          val selected = draftPreset == preset
          GlassSurfaceCard(
            modifier =
              Modifier.weight(1f)
                .clickable {
                  draftPreset = preset
                  draftFont = preset.font
                  draftWeight = preset.weight
                  draftFontSizeSp = preset.fontSizeSp
                  draftTextColorArgb = preset.textColorArgb
                  draftBoxColorArgb = preset.boxColorArgb
                  draftBoxOpacity = preset.boxOpacity
                }
                .testTag("preset_${preset.key}"),
            cornerRadius = 20.dp,
            borderBrush =
              Brush.linearGradient(
                if (selected) listOf(ElectricViolet, ElectricViolet)
                else listOf(GlassWhiteBorder, GlassWhiteBorder)
              ),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Box(
                modifier =
                  Modifier.size(44.dp)
                    .clip(CircleShape)
                    .background(Color(preset.boxColorArgb).copy(alpha = 0.9f))
                    .border(
                      1.5.dp,
                      if (selected) ElectricViolet else GlassWhiteBorder,
                      CircleShape,
                    ),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = "ع",
                  fontFamily = ArabicFontRegistry.fontFamilyFor(preset.font),
                  fontWeight = ArabicFontRegistry.composeWeightFor(preset.weight),
                  color = Color(preset.textColorArgb),
                  fontSize = 18.sp,
                )
              }
              Text(
                text = preset.displayName,
                color = if (selected) CrispWhite else LightGrayText,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
              )
            }
          }
        }
      }

      // Fonts Section (2x2 grid) matching Screenshot 6 & 7
      Text("الخط", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
      val fontRows = ArabicFontOption.entries.chunked(2)
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        fontRows.forEach { rowFonts ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            rowFonts.forEach { fontOpt ->
              val selected = draftFont == fontOpt
              GlassSurfaceCard(
                modifier =
                  Modifier.weight(1f)
                    .clickable { draftFont = fontOpt }
                    .testTag("font_${fontOpt.key}"),
                cornerRadius = 18.dp,
                borderBrush =
                  Brush.linearGradient(
                    if (selected) listOf(ElectricViolet, ElectricViolet)
                    else listOf(GlassWhiteBorder, GlassWhiteBorder)
                  ),
              ) {
                Column(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 10.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                  Text(
                    text = fontOpt.shortLabel,
                    fontFamily = ArabicFontRegistry.fontFamilyFor(fontOpt),
                    color = if (selected) CrispWhite else LightGrayText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                  )
                  Text(
                    text = "عربي • نص تجريبي",
                    fontFamily = ArabicFontRegistry.fontFamilyFor(fontOpt),
                    color = SoftLavender.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                  )
                }
              }
            }
          }
        }
      }

      // Weight Section (عادي / عريض) matching Screenshot 7
      Text("سماكة الخط", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OverlayFontWeight.entries.forEach { weightOpt ->
          val selected = draftWeight == weightOpt
          Box(
            modifier =
              Modifier.weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                  if (selected) ElectricViolet.copy(alpha = 0.24f) else GlassCardFillStrong
                )
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) ElectricViolet else GlassWhiteBorder,
                  shape = RoundedCornerShape(16.dp),
                )
                .clickable { draftWeight = weightOpt }
                .testTag("weight_${weightOpt.key}"),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = weightOpt.label,
              color = CrispWhite,
              fontWeight = ArabicFontRegistry.composeWeightFor(weightOpt),
              fontSize = 16.sp,
            )
          }
        }
      }

      // Size Section (الحجم + %) matching Screenshot 7
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("الحجم", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
        Text("$sizePercent%", color = CrispWhite, style = MaterialTheme.typography.titleMedium)
      }
      Slider(
        value = draftFontSizeSp,
        onValueChange = { draftFontSizeSp = it },
        valueRange = 14f..42f,
        modifier = Modifier.fillMaxWidth().testTag("style_size_slider"),
        colors =
          SliderDefaults.colors(
            thumbColor = CrispWhite,
            activeTrackColor = ElectricViolet,
            inactiveTrackColor = VoidSurfaceVariant,
          ),
      )

      // Text Color Section matching Screenshot 7
      Text("لون النص", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextColorSwatches.forEach { (label, argb) ->
          val selected = draftTextColorArgb == argb
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(
                  width = if (selected) 2.5.dp else 1.dp,
                  color = if (selected) ElectricViolet else GlassWhiteBorder,
                  shape = CircleShape,
                )
                .clickable { draftTextColorArgb = argb }
                .testTag("text_swatch_$label")
          )
        }

        // Custom color wheel chip
        CustomWheelChip(
          label = "لون مخصص",
          isSelected = TextColorSwatches.none { it.second == draftTextColorArgb },
          onClick = { colorPickerTarget = "text" },
          modifier = Modifier.testTag("custom_text_color_button"),
        )
      }

      // Box Color & Opacity Section matching Screenshot 7
      Text("لون البوكس", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BoxColorSwatches.forEach { (label, argb) ->
          val selected = draftBoxColorArgb == argb
          Box(
            modifier =
              Modifier.size(44.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(
                  width = if (selected) 2.5.dp else 1.dp,
                  color = if (selected) ElectricViolet else GlassWhiteBorder,
                  shape = CircleShape,
                )
                .clickable { draftBoxColorArgb = argb }
                .testTag("box_swatch_$label")
          )
        }

        CustomWheelChip(
          label = "لون مخصص",
          isSelected = BoxColorSwatches.none { it.second == draftBoxColorArgb },
          onClick = { colorPickerTarget = "box" },
          modifier = Modifier.testTag("custom_box_color_button"),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("شفافية البوكس", style = MaterialTheme.typography.titleMedium, color = LightGrayText)
        Text("$opacityPercent%", style = MaterialTheme.typography.titleMedium, color = SoftLavender)
      }
      Slider(
        value = draftBoxOpacity,
        onValueChange = { draftBoxOpacity = it },
        valueRange = 0f..1f,
        modifier = Modifier.fillMaxWidth().testTag("box_opacity_slider"),
        colors =
          SliderDefaults.colors(
            thumbColor = CrispWhite,
            activeTrackColor = ElectricViolet,
            inactiveTrackColor = VoidSurfaceVariant,
          ),
      )

      // Reset to Original button (رجّع الأصلي) matching Screenshot 7
      TextButton(
        onClick = {
          val orig = OverlayStylePreset.ORIGINAL
          draftPreset = orig
          draftFont = orig.font
          draftWeight = orig.weight
          draftFontSizeSp = orig.fontSizeSp
          draftTextColorArgb = orig.textColorArgb
          draftBoxColorArgb = orig.boxColorArgb
          draftBoxOpacity = orig.boxOpacity
          viewModel.resetToOriginal()
        },
        modifier = Modifier.align(Alignment.CenterHorizontally).testTag("reset_style_button"),
      ) {
        Text(
          text = "رجّع الأصلي",
          color = LightGrayText,
          textDecoration = TextDecoration.Underline,
          fontSize = 15.sp,
        )
      }

      // Save Button (احفظ ←) matching Screenshot 7
      GlassGradientPillButton(
        text = "احفظ",
        onClick = {
          viewModel.saveFullStyle(
            preset = draftPreset,
            font = draftFont,
            weight = draftWeight,
            fontSizeSp = draftFontSizeSp,
            textColorArgb = draftTextColorArgb,
            boxColorArgb = draftBoxColorArgb,
            boxOpacity = draftBoxOpacity,
            position = settings.overlayPosition,
            cornerRadiusDp = settings.overlayCornerRadiusDp,
          )
          Toast.makeText(context, "تم حفظ شكل الترجمة بنجاح", Toast.LENGTH_SHORT).show()
          onBack()
        },
        iconRes = R.drawable.ic_arrow_forward,
        modifier = Modifier.testTag("save_style_button"),
      )

      Spacer(modifier = Modifier.height(20.dp))
    }
  }

  colorPickerTarget?.let { target ->
    val initialArgb = if (target == "text") draftTextColorArgb else draftBoxColorArgb
    HsvColorWheelDialog(
      title = if (target == "text") "اختر لون النص" else "اختر لون البوكس",
      initialColor = Color(initialArgb),
      onDismiss = { colorPickerTarget = null },
      onConfirm = { picked ->
        val argb = picked.toArgb().toLong() and 0xFFFFFFFFL
        if (target == "text") {
          draftTextColorArgb = argb
        } else {
          draftBoxColorArgb = argb
        }
        colorPickerTarget = null
      },
    )
  }
}

@Composable
private fun CustomWheelChip(
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .height(44.dp)
        .clip(RoundedCornerShape(50))
        .background(GlassCardFillStrong)
        .border(
          1.dp,
          if (isSelected) ElectricViolet else GlassWhiteBorder,
          RoundedCornerShape(50),
        )
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Canvas(modifier = Modifier.size(20.dp)) {
      drawCircle(
        brush =
          Brush.sweepGradient(
            listOf(
              Color.Red,
              Color.Yellow,
              Color.Green,
              Color.Cyan,
              Color.Blue,
              Color.Magenta,
              Color.Red,
            )
          )
      )
      drawCircle(color = CrispWhite.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
    }
    Text(text = label, color = LightGrayText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
  }
}

@Composable
private fun HsvColorWheelDialog(
  title: String,
  initialColor: Color,
  onDismiss: () -> Unit,
  onConfirm: (Color) -> Unit,
) {
  val initialHsv = remember(initialColor) {
    FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
  }
  var hue by remember { mutableFloatStateOf(initialHsv[0]) }
  var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceAtLeast(0.4f)) }
  var value by remember { mutableFloatStateOf(initialHsv[2].coerceAtLeast(0.6f)) }

  val selectedColor = Color.hsv(hue, saturation, value)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = VoidSurface,
    title = { Text(title, color = CrispWhite, fontWeight = FontWeight.Bold) },
    text = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(190.dp)
              .pointerInput(Unit) {
                detectTapGestures { offset ->
                  val cx = size.width / 2f
                  val cy = size.height / 2f
                  val dx = offset.x - cx
                  val dy = offset.y - cy
                  val radius = size.width.coerceAtMost(size.height) / 2f
                  val dist = hypot(dx, dy).coerceAtMost(radius)
                  val angle = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
                  hue = angle.toFloat()
                  saturation = (dist / radius).coerceIn(0f, 1f)
                }
              }
              .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                  change.consume()
                  val cx = size.width / 2f
                  val cy = size.height / 2f
                  val dx = change.position.x - cx
                  val dy = change.position.y - cy
                  val radius = size.width.coerceAtMost(size.height) / 2f
                  val dist = hypot(dx, dy).coerceAtMost(radius)
                  val angle = ((atan2(dy, dx) * 180.0 / PI) + 360.0) % 360.0
                  hue = angle.toFloat()
                  saturation = (dist / radius).coerceIn(0f, 1f)
                }
              }
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(
              brush =
                Brush.sweepGradient(
                  listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                  )
                ),
              radius = radius,
            )
            drawCircle(
              brush =
                Brush.radialGradient(
                  colors = listOf(Color.White, Color.Transparent),
                  radius = radius,
                ),
              radius = radius,
            )
            val rad = Math.toRadians(hue.toDouble())
            val px = center.x + (cos(rad) * radius * saturation).toFloat()
            val py = center.y + (sin(rad) * radius * saturation).toFloat()
            drawCircle(
              color = CrispWhite,
              radius = 10.dp.toPx(),
              center = Offset(px, py),
              style = Stroke(width = 3.dp.toPx()),
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("الإضاءة", color = LightGrayText, fontSize = 13.sp)
          Box(
            modifier =
              Modifier.size(28.dp)
                .clip(CircleShape)
                .background(selectedColor)
                .border(1.5.dp, CrispWhite, CircleShape)
          )
        }
        Slider(
          value = value,
          onValueChange = { value = it },
          valueRange = 0.05f..1f,
          colors =
            SliderDefaults.colors(
              thumbColor = CrispWhite,
              activeTrackColor = ElectricViolet,
              inactiveTrackColor = VoidSurfaceVariant,
            ),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(selectedColor) }) {
        Text("اختيار", color = SoftLavender, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("إلغاء", color = MutedGrayText)
      }
    },
  )
}
