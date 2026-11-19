package com.livetranslator.controller.ui.theme

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val VoidBackground = Color(0xFF09090B)
val VoidSurface = Color(0xFF14121D)
val GlassCardFill = Color(0xA6161422)
val GlassCardFillStrong = Color(0xCC181526)
val VoidSurfaceVariant = Color(0xFF201C30)

val ElectricViolet = Color(0xFF8B5CF6)
val DeepViolet = Color(0xFF6D28D9)
val BrightAmethyst = Color(0xFFA855F7)
val SoftLavender = Color(0xFFC4B5FD)

val CrispWhite = Color(0xFFFFFFFF)
val LightGrayText = Color(0xFFD4D4D8)
val MutedGrayText = Color(0xFFA1A1AA)
val GlassWhiteBorder = Color(0x28FFFFFF)
val VioletOutline = Color(0x528B5CF6)

val AmbientGlowTop = Color(0xFF5B21B6)
val AmbientGlowBottom = Color(0xFF4C1D95)

val EmeraldAccent = Color(0xFF34D399)
val AmberAccent = Color(0xFFFBBF24)
val RoseError = Color(0xFFFB7185)

private val ControllerColorScheme =
  darkColorScheme(
    primary = ElectricViolet,
    onPrimary = CrispWhite,
    primaryContainer = DeepViolet,
    onPrimaryContainer = CrispWhite,
    secondary = SoftLavender,
    onSecondary = VoidBackground,
    surface = VoidSurface,
    onSurface = CrispWhite,
    background = VoidBackground,
    onBackground = CrispWhite,
    surfaceVariant = VoidSurfaceVariant,
    onSurfaceVariant = LightGrayText,
    outline = VioletOutline,
    error = RoseError,
  )

private val ControllerTypography =
  Typography(
    headlineMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
      ),
  )

private val ControllerShapes =
  Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun ControllerTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    MaterialTheme(
      colorScheme = ControllerColorScheme,
      typography = ControllerTypography,
      shapes = ControllerShapes,
      content = content,
    )
  }
}

fun Modifier.controllerRadialBackground(): Modifier =
  this.background(VoidBackground).drawBehind {
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(AmbientGlowTop.copy(alpha = 0.30f), Color.Transparent),
          center = Offset(size.width * 0.82f, size.height * 0.08f),
          radius = size.maxDimension * 0.72f,
        )
    )
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(AmbientGlowBottom.copy(alpha = 0.24f), Color.Transparent),
          center = Offset(size.width * 0.14f, size.height * 0.88f),
          radius = size.maxDimension * 0.72f,
        )
    )
  }

@Composable
fun ControllerGlassCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 24.dp,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(cornerRadius),
    colors = CardDefaults.cardColors(containerColor = GlassCardFill),
    border =
      BorderStroke(
        1.dp,
        Brush.linearGradient(
          listOf(
            CrispWhite.copy(alpha = 0.22f),
            ElectricViolet.copy(alpha = 0.38f),
            CrispWhite.copy(alpha = 0.10f),
          )
        ),
      ),
  ) {
    Column(content = content)
  }
}

@Composable
fun ControllerGradientPillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  iconRes: Int? = null,
) {
  val colors =
    if (enabled) listOf(BrightAmethyst, ElectricViolet, DeepViolet)
    else listOf(Color(0xFF27272A), Color(0xFF1E1E24))

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(54.dp)
        .shadow(
          elevation = if (enabled) 14.dp else 0.dp,
          shape = RoundedCornerShape(27.dp),
          ambientColor = ElectricViolet,
          spotColor = ElectricViolet,
        )
        .clip(RoundedCornerShape(27.dp))
        .background(Brush.horizontalGradient(colors))
        .border(1.dp, SoftLavender.copy(alpha = if (enabled) 0.6f else 0.2f), RoundedCornerShape(27.dp))
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
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
      )
      if (iconRes != null) {
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          painter = painterResource(id = iconRes),
          contentDescription = null,
          tint = if (enabled) CrispWhite else MutedGrayText,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
fun ControllerTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    modifier = modifier,
    style =
      TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        color = CrispWhite,
        shadow = Shadow(color = ElectricViolet.copy(alpha = 0.7f), blurRadius = 24f),
      ),
  )
}
