package com.livetranslator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.livetranslator.app.R
import com.livetranslator.app.ui.theme.BrightAmethyst
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.EmeraldAccent
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.theme.homeRadialBackground
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pagerState = rememberPagerState(pageCount = { 3 })

  var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
  var hasScreenCaptureChecked by remember { mutableStateOf(false) }
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var hasNotificationPermission by remember {
    mutableStateOf(
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
    )
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    hasOverlayPermission = Settings.canDrawOverlays(context)
    hasMicPermission =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    hasNotificationPermission =
      Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
  }

  val overlayLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      hasOverlayPermission = Settings.canDrawOverlays(context)
    }

  val screenCaptureLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        hasScreenCaptureChecked = true
      }
    }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
    }

  val notificationLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasNotificationPermission = granted
    }

  Box(
    modifier =
      Modifier.fillMaxSize().homeRadialBackground().safeDrawingPadding().testTag("onboarding_screen")
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).align(Alignment.Center).padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "الإعداد الأولي (${pagerState.currentPage + 1}/3)",
          style = MaterialTheme.typography.labelLarge,
          color = MutedViolet,
        )
        TextButton(
          onClick = onComplete,
          modifier = Modifier.testTag("onboarding_skip_button"),
        ) {
          Text(text = stringResource(R.string.onboarding_skip), color = SoftLavender)
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      HorizontalPager(
        state = pagerState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      ) { page ->
        when (page) {
          0 ->
            OnboardingPageCard(
              iconRes = R.drawable.ic_bubble,
              title = "النافذة العائمة فوق التطبيقات",
              subtitle =
                "اسمح للتطبيق بعرض الفقاعة البنفسجية القابلة للسحب وبطاقة الترجمة العربية فوق YouTube و Kick وأي تطبيق بث.",
              isGranted = hasOverlayPermission,
              buttonLabel =
                if (hasOverlayPermission) "تم تفعيل الظهور العائم" else "منح إذن الظهور فوق التطبيقات",
              buttonTestTag = "grant_overlay_button",
              onAction = {
                val intent =
                  Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                  )
                overlayLauncher.launch(intent)
              },
            )
          1 ->
            OnboardingPageCard(
              iconRes = R.drawable.ic_mic,
              title = "التقاط صوت البث والشاشة",
              subtitle =
                "اسمح بالتقاط صوت النظام (أندرويد 10+) لترجمة البثوث مباشرة، ويمكنك أيضاً منح إذن الميكروفون كبديل احتياطي.",
              isGranted = hasScreenCaptureChecked && hasMicPermission,
              buttonLabel =
                if (hasScreenCaptureChecked) "التقاط الشاشة جاهز"
                else "منح إذن التقاط الصوت والشاشة",
              buttonTestTag = "grant_capture_button",
              secondaryLabel =
                if (hasMicPermission) "تم منح إذن الميكروفون" else "منح إذن الميكروفون (اختياري)",
              onSecondaryAction = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              },
              onAction = {
                val mgr =
                  context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
              },
            )
          else ->
            OnboardingPageCard(
              iconRes = R.drawable.ic_info,
              title = "إشعار التحكم الدائم",
              subtitle =
                "اسمح بالإشعارات لإبقاء خدمة الترجمة الفورية مستقرة في الخلفية مع زر إيقاف سريع بضغطة واحدة.",
              isGranted = hasNotificationPermission,
              buttonLabel =
                if (hasNotificationPermission) "تم تفعيل الإشعارات" else "منح إذن الإشعارات",
              buttonTestTag = "grant_notification_button",
              onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                  hasNotificationPermission = true
                }
              },
            )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        repeat(3) { index ->
          val active = pagerState.currentPage == index
          Box(
            modifier =
              Modifier.height(8.dp)
                .width(if (active) 28.dp else 8.dp)
                .clip(CircleShape)
                .background(if (active) ElectricViolet else VoidSurfaceVariant)
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Button(
        onClick = {
          if (pagerState.currentPage < 2) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
          } else {
            onComplete()
          }
        },
        modifier =
          Modifier.fillMaxWidth()
            .height(54.dp)
            .testTag("onboarding_next_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
      ) {
        Text(
          text =
            if (pagerState.currentPage < 2) stringResource(R.string.onboarding_next)
            else stringResource(R.string.onboarding_finish),
          style = MaterialTheme.typography.titleMedium,
          color = LavenderWhite,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
private fun OnboardingPageCard(
  iconRes: Int,
  title: String,
  subtitle: String,
  isGranted: Boolean,
  buttonLabel: String,
  buttonTestTag: String,
  onAction: () -> Unit,
  secondaryLabel: String? = null,
  onSecondaryAction: (() -> Unit)? = null,
) {
  GlassSurfaceCard(
    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
    cornerRadius = 20.dp,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Box(
        modifier =
          Modifier.size(88.dp)
            .clip(CircleShape)
            .background(
              Brush.linearGradient(
                listOf(ElectricViolet.copy(alpha = 0.35f), BrightAmethyst.copy(alpha = 0.25f))
              )
            )
            .border(1.dp, SoftLavender.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = iconRes),
          contentDescription = title,
          tint = LavenderWhite,
          modifier = Modifier.size(40.dp),
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = LavenderWhite,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MutedViolet,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(24.dp))

      if (isGranted) {
        Row(
          modifier =
            Modifier.clip(RoundedCornerShape(12.dp))
              .background(EmeraldAccent.copy(alpha = 0.14f))
              .border(1.dp, EmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
              .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_check),
            contentDescription = stringResource(R.string.onboarding_granted),
            tint = EmeraldAccent,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.onboarding_granted),
            color = EmeraldAccent,
            style = MaterialTheme.typography.labelLarge,
          )
        }
        Spacer(modifier = Modifier.height(14.dp))
      }

      Button(
        onClick = onAction,
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag(buttonTestTag),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = if (isGranted) VoidSurfaceVariant else ElectricViolet
          ),
      ) {
        Text(
          text = buttonLabel,
          color = LavenderWhite,
          style = MaterialTheme.typography.labelLarge,
        )
      }

      if (secondaryLabel != null && onSecondaryAction != null) {
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
          onClick = onSecondaryAction,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Text(text = secondaryLabel, color = SoftLavender)
        }
      }
    }
  }
}
