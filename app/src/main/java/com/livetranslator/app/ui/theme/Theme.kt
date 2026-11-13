package com.livetranslator.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LiveTranslatorDarkColorScheme =
  darkColorScheme(
    primary = ElectricViolet,
    onPrimary = LavenderWhite,
    primaryContainer = BrightAmethyst,
    onPrimaryContainer = LavenderWhite,
    secondary = SoftLavender,
    onSecondary = VoidBackground,
    secondaryContainer = VoidSurfaceVariant,
    onSecondaryContainer = LavenderWhite,
    tertiary = SoftLavender,
    onTertiary = VoidBackground,
    background = VoidBackground,
    onBackground = LavenderWhite,
    surface = VoidSurface,
    onSurface = LavenderWhite,
    surfaceVariant = VoidSurfaceVariant,
    onSurfaceVariant = MutedViolet,
    outline = VioletOutline,
    outlineVariant = VioletOutline,
    error = RoseError,
    onError = VoidBackground,
  )

val LiveTranslatorShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
          colors = listOf(HomeRadialTopLeft.copy(alpha = 0.85f), Color.Transparent),
          center = Offset(size.width * 0.88f, size.height * 0.08f),
          radius = size.maxDimension * 0.72f,
        )
    )
    drawRect(
      brush =
        Brush.radialGradient(
          colors = listOf(HomeRadialBottomRight.copy(alpha = 0.65f), Color.Transparent),
          center = Offset(size.width * 0.08f, size.height * 0.90f),
          radius = size.maxDimension * 0.72f,
        )
    )
  }

@Composable
fun GlassSurfaceCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 20.dp,
  containerColor: Color = VoidSurface.copy(alpha = 0.82f),
  borderColor: Color = VioletOutline,
  content: @Composable () -> Unit,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(cornerRadius),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderColor),
  ) {
    content()
  }
}
