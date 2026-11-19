package com.livetranslator.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.livetranslator.app.R
import com.livetranslator.app.ui.theme.BrightAmethyst
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.DeepViolet
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.GlowingScreenTitle
import com.livetranslator.app.ui.theme.LightGrayText
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.homeRadialBackground
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
  val logoScale = remember { Animatable(0.72f) }
  val logoAlpha = remember { Animatable(0f) }

  val infinite = rememberInfiniteTransition(label = "splashWaves")
  val ringScale by
    infinite.animateFloat(
      initialValue = 1f,
      targetValue = 1.42f,
      animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
      label = "ringScale",
    )

  LaunchedEffect(Unit) {
    logoScale.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
  }
  LaunchedEffect(Unit) {
    logoAlpha.animateTo(1f, animationSpec = tween(450))
  }
  LaunchedEffect(Unit) {
    delay(1200L)
    onFinished()
  }

  Box(
    modifier = Modifier.fillMaxSize().homeRadialBackground().testTag("splash_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(32.dp).alpha(logoAlpha.value),
    ) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
        Canvas(modifier = Modifier.size(168.dp)) {
          drawCircle(
            color = ElectricViolet.copy(alpha = 0.28f),
            radius = (size.minDimension / 2f) * (ringScale * 0.86f),
            style = Stroke(width = 2.dp.toPx()),
          )
          drawCircle(
            color = SoftLavender.copy(alpha = 0.16f),
            radius = (size.minDimension / 2f) * ringScale,
            style = Stroke(width = 1.5.dp.toPx()),
          )
        }

        Box(
          modifier =
            Modifier.size(104.dp)
              .scale(logoScale.value)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(BrightAmethyst, ElectricViolet, DeepViolet)))
              .border(1.5.dp, SoftLavender.copy(alpha = 0.8f), CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_translate),
            contentDescription = stringResource(R.string.app_name),
            tint = CrispWhite,
            modifier = Modifier.size(48.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      GlowingScreenTitle(text = stringResource(R.string.app_name), fontSizeSp = 32)

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = stringResource(R.string.app_subtitle),
        style = MaterialTheme.typography.titleMedium,
        color = LightGrayText,
      )
    }
  }
}
