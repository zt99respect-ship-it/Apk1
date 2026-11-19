package com.livetranslator.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetranslator.app.R

private val LiveTranslatorDarkColorScheme =
  darkColorScheme(
    primary = ElectricViolet,
    onPrimary = CrispWhite,
    primaryContainer = DeepViolet,
    onPrimaryContainer = CrispWhite,
    secondary = SoftLavender,
    onSecondary = VoidBackground,
    secondaryContainer = VoidSurfaceVariant,
    onSecondaryContainer = CrispWhite,
    tertiary = BrightAmethyst,
    onTertiary = CrispWhite,
    background = VoidBackground,
    onBackground = CrispWhite,
    surface = VoidSurface,
    onSurface = CrispWhite,
    surfaceVariant = VoidSurfaceVariant,
    onSurfaceVariant = LightGrayText,
    outline = VioletOutline,
    outlineVariant = GlassWhiteBorder,
    error = RoseError,
    onError = VoidBackground,
  )

val LiveTranslatorShapes =
  Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
  )

@Composable
fun LiveTranslatorTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    MaterialTheme(
      colorScheme = LiveTranslatorDarkColorScheme,
      typography = LiveTranslatorTypography,
      shapes = LiveTranslatorShapes,
      content = content,
    )
  }
}

fun Modifier.homeRadialBackground(): Modifier =
  this.background(VoidBackground).drawBehind {
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(AmbientGlowTop.copy(alpha = 0.28f), Color.Transparent),
          center = Offset(size.width * 0.85f, size.height * 0.08f),
          radius = size.maxDimension * 0.72f,
        )
    )
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(ElectricViolet.copy(alpha = 0.16f), Color.Transparent),
          center = Offset(size.width * 0.50f, size.height * 0.44f),
          radius = size.maxDimension * 0.55f,
        )
    )
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(AmbientGlowBottom.copy(alpha = 0.24f), Color.Transparent),
          center = Offset(size.width * 0.12f, size.height * 0.88f),
          radius = size.maxDimension * 0.72f,
        )
    )
  }

@Composable
fun GlassSurfaceCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 24.dp,
  containerColor: Color = GlassCardFill,
  borderBrush: Brush =
    Brush.linearGradient(
      colors =
        listOf(
          CrispWhite.copy(alpha = 0.22f),
          ElectricViolet.copy(alpha = 0.34f),
          CrispWhite.copy(alpha = 0.09f),
        )
    ),
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier =
      modifier.drawBehind {
        drawCircle(
          brush =
            Brush.radialGradient(
              colors = listOf(ElectricViolet.copy(alpha = 0.09f), Color.Transparent),
              center = Offset(size.width * 0.5f, 0f),
              radius = size.maxDimension * 0.75f,
            )
        )
      },
    shape = RoundedCornerShape(cornerRadius),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderBrush),
  ) {
    Column(content = content)
  }
}

@Composable
fun GlassGradientPillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isActiveStop: Boolean = false,
  iconRes: Int? = R.drawable.ic_arrow_forward,
) {
  val gradientColors =
    when {
      !enabled -> listOf(Color(0xFF27272A), Color(0xFF1E1E24))
      isActiveStop -> listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
      else -> listOf(BrightAmethyst, ElectricViolet, DeepViolet)
    }

  val borderColor =
    when {
      !enabled -> GlassWhiteBorder
      isActiveStop -> RoseError.copy(alpha = 0.7f)
      else -> SoftLavender.copy(alpha = 0.65f)
    }

  val glowColor =
    when {
      !enabled -> Color.Transparent
      isActiveStop -> RoseError
      else -> ElectricViolet
    }

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(56.dp)
        .shadow(
          elevation = if (enabled) 16.dp else 0.dp,
          shape = RoundedCornerShape(28.dp),
          ambientColor = glowColor,
          spotColor = glowColor,
        )
        .clip(RoundedCornerShape(28.dp))
        .background(Brush.horizontalGradient(gradientColors))
        .border(1.dp, borderColor, RoundedCornerShape(28.dp))
        .clickable(enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 20.dp),
    ) {
      Text(
        text = text,
        color = if (enabled) CrispWhite else MutedGrayText,
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
      )
      if (iconRes != null) {
        Spacer(modifier = Modifier.width(10.dp))
        Icon(
          painter = painterResource(id = iconRes),
          contentDescription = null,
          tint = if (enabled) CrispWhite else MutedGrayText,
          modifier = Modifier.size(20.dp),
        )
      }
    }
  }
}

@Composable
fun GlowingScreenTitle(
  text: String,
  modifier: Modifier = Modifier,
  fontSizeSp: Int = 30,
) {
  Text(
    text = text,
    modifier = modifier,
    style =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSizeSp.sp,
        color = CrispWhite,
        shadow =
          Shadow(
            color = ElectricViolet.copy(alpha = 0.75f),
            offset = Offset(0f, 0f),
            blurRadius = 26f,
          ),
      ),
  )
}

@Composable
fun AppBrandBadge(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .size(44.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(GlassCardFillStrong)
        .border(1.2.dp, ElectricViolet.copy(alpha = 0.75f), RoundedCornerShape(12.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(id = R.drawable.ic_brand_emblem),
      contentDescription = "ترجمة فورية",
      tint = SoftLavender,
      modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
fun CircularGlassIconButton(
  iconRes: Int,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  tint: Color = CrispWhite,
) {
  Box(
    modifier =
      modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(GlassCardFillStrong)
        .border(1.dp, GlassWhiteBorder, CircleShape)
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(id = iconRes),
      contentDescription = contentDescription,
      tint = tint,
      modifier = Modifier.size(22.dp),
    )
  }
}
