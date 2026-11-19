package com.livetranslator.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.engine.OtaCenterState
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
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.theme.homeRadialBackground
import com.livetranslator.app.ui.viewmodel.CloudAuthViewModel

@Composable
fun GoogleAuthScreen(viewModel: CloudAuthViewModel) {
  val context = LocalContext.current
  val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
  val authError by viewModel.authError.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.attemptAutoSignIn(context)
  }

  Box(
    modifier = Modifier.fillMaxSize().homeRadialBackground().testTag("google_auth_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      AppBrandBadge(modifier = Modifier.size(64.dp))

      GlowingScreenTitle(text = "ترجمة فورية", fontSizeSp = 32)

      Text(
        text = "سجّل دخولك بحساب جوجل لحفظ كودك السري المخفي وتلقي التحديثات الساخنة والإشعارات الفورية داخل التطبيق.",
        color = LightGrayText,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
      )

      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          FeaturePointRow(
            iconRes = R.drawable.ic_translate,
            text = "ترجمة لحظية لأصوات الهاتف والشاشة إلى العربية",
          )
          FeaturePointRow(
            iconRes = R.drawable.ic_update,
            text = "تحديثات ساخنة فورية داخل التطبيق دون إعادة تثبيت APK",
          )
          FeaturePointRow(
            iconRes = R.drawable.ic_person,
            text = "حساب خاص وكود سري مخفي فريد لكل مستخدم",
          )

          if (!authError.isNullOrBlank()) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(14.dp))
                  .background(RoseError.copy(alpha = 0.14f))
                  .border(1.dp, RoseError.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                  .padding(12.dp)
            ) {
              Text(
                text = authError.orEmpty(),
                color = RoseError,
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }

          if (isSigningIn) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CircularProgressIndicator(color = ElectricViolet, modifier = Modifier.size(26.dp))
              Spacer(modifier = Modifier.width(12.dp))
              Text("جاري تسجيل الدخول...", color = SoftLavender, fontWeight = FontWeight.Bold)
            }
          }

          GlassGradientPillButton(
            text = "تسجيل الدخول بحساب جوجل",
            onClick = { viewModel.signInWithGoogle(context) },
            enabled = !isSigningIn,
            iconRes = R.drawable.ic_arrow_forward,
            modifier = Modifier.testTag("google_sign_in_button"),
          )
        }
      }
    }
  }
}

@Composable
fun FirstEntryUserSetupScreen(
  email: String,
  isSubmitting: Boolean,
  errorMessage: String?,
  onSubmit: (username: String, password: String, confirmPassword: String) -> Unit,
  onSignOut: () -> Unit,
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }

  Box(
    modifier = Modifier.fillMaxSize().homeRadialBackground().testTag("first_entry_setup_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 540.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      AppBrandBadge()

      GlowingScreenTitle(text = "إعداد حسابك لأول مرة", fontSizeSp = 28)

      Text(
        text = "أنشئ اسم المستخدم وكلمة المرور الخاصة بك الآن. سيتم توليد كود سري مخفي فريد لك داخل صفحة حسابي.",
        color = LightGrayText,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
      )

      if (email.isNotBlank()) {
        Text(
          text = email,
          color = SoftLavender,
          style = MaterialTheme.typography.labelLarge,
        )
      }

      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            label = { Text("اسم المستخدم (اليوزر)", color = SoftLavender) },
            placeholder = { Text("مثال: ahmed_99", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth().testTag("setup_username_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("كلمة المرور (6 أحرف فأكثر)", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth().testTag("setup_password_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("تأكيد كلمة المرور", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth().testTag("setup_confirm_password_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          if (!errorMessage.isNullOrBlank()) {
            Text(
              text = errorMessage,
              color = RoseError,
              style = MaterialTheme.typography.bodyMedium,
            )
          }

          GlassGradientPillButton(
            text = if (isSubmitting) "جاري الحفظ وتوليد الكود..." else "حفظ وتوليد الكود السري",
            onClick = { onSubmit(username, password, confirmPassword) },
            enabled = !isSubmitting,
            iconRes = R.drawable.ic_check,
            modifier = Modifier.testTag("setup_submit_button"),
          )

          TextButton(
            onClick = onSignOut,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          ) {
            Text("تبديل حساب جوجل", color = MutedGrayText)
          }
        }
      }
    }
  }
}

@Composable
fun MaintenanceOrBlockedScreen(
  title: String,
  message: String,
  isBlockedAccount: Boolean,
  secretCode: String?,
  onSignOut: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize().homeRadialBackground().testTag("maintenance_blocked_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 520.dp)
          .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Box(
        modifier =
          Modifier.size(72.dp)
            .clip(CircleShape)
            .background((if (isBlockedAccount) RoseError else AmberAccent).copy(alpha = 0.16f))
            .border(
              1.5.dp,
              (if (isBlockedAccount) RoseError else AmberAccent).copy(alpha = 0.55f),
              CircleShape,
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_warning),
          contentDescription = title,
          tint = if (isBlockedAccount) RoseError else AmberAccent,
          modifier = Modifier.size(34.dp),
        )
      }

      GlowingScreenTitle(text = title, fontSizeSp = 28)

      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = message,
            color = CrispWhite,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
          if (!secretCode.isNullOrBlank()) {
            Text(
              text = "مرجع الحساب: $secretCode",
              color = SoftLavender,
              style = MaterialTheme.typography.labelLarge,
            )
          }
          Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = VoidSurfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp),
          ) {
            Text("تسجيل الخروج", color = CrispWhite)
          }
        }
      }
    }
  }
}

@Composable
fun CloudLoadingProfileScreen() {
  Box(
    modifier = Modifier.fillMaxSize().homeRadialBackground(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      CircularProgressIndicator(color = ElectricViolet)
      Text(
        text = "جاري مزامنة ملفك السحابي والتحديثات...",
        color = SoftLavender,
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}

@Composable
fun InAppHotfixPromptDialog(
  otaState: OtaCenterState,
  onApplyNow: () -> Unit,
  onDismiss: () -> Unit,
) {
  val manifest = otaState.availableManifest ?: return
  AlertDialog(
    onDismissRequest = { if (!otaState.isMandatoryHotfix) onDismiss() },
    containerColor = VoidSurface,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painter = painterResource(id = R.drawable.ic_update),
          contentDescription = null,
          tint = EmeraldAccent,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "تحديث تصحيح ساخن v${manifest.patchVersion}",
          color = CrispWhite,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text =
            manifest.patchNotes.ifBlank {
              "تتوفر تحسينات وتعديلات ساخنة جديدة جاهزة للتطبيق المباشر داخل التطبيق دون إعادة تثبيت ملف APK."
            },
          color = LightGrayText,
          lineHeight = 22.sp,
        )
        Text(
          text =
            "تشمل: ${manifest.glossaryAdditions.size} مصطلح • ${manifest.noiseFilterAdditions.size} قاعدة تصفية",
          color = SoftLavender,
          style = MaterialTheme.typography.labelLarge,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onApplyNow,
        enabled = !otaState.isApplyingPatch,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
      ) {
        Text(
          text = if (otaState.isApplyingPatch) "جاري التطبيق..." else "تطبيق التحديث الساخن الآن",
          color = CrispWhite,
          fontWeight = FontWeight.Bold,
        )
      }
    },
    dismissButton =
      if (otaState.isMandatoryHotfix) null
      else {
        {
          TextButton(onClick = onDismiss) {
            Text("لاحقاً", color = MutedGrayText)
          }
        }
      },
  )
}

@Composable
fun GlobalAdminBannerStrip(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  if (title.isBlank() && message.isBlank()) return
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(GlassCardFillStrong)
        .border(1.dp, ElectricViolet.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      if (title.isNotBlank()) {
        Text(text = title, color = SoftLavender, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      }
      if (message.isNotBlank()) {
        Text(text = message, color = CrispWhite, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun FeaturePointRow(iconRes: Int, text: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(36.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(ElectricViolet.copy(alpha = 0.2f))
          .border(1.dp, ElectricViolet.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = SoftLavender,
        modifier = Modifier.size(18.dp),
      )
    }
    Spacer(modifier = Modifier.width(12.dp))
    Text(
      text = text,
      color = CrispWhite,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}
