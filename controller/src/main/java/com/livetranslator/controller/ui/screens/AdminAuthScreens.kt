package com.livetranslator.controller.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.controller.R
import com.livetranslator.controller.ui.theme.CrispWhite
import com.livetranslator.controller.ui.theme.ElectricViolet
import com.livetranslator.controller.ui.theme.GlassWhiteBorder
import com.livetranslator.controller.ui.theme.LightGrayText
import com.livetranslator.controller.ui.theme.MutedGrayText
import com.livetranslator.controller.ui.theme.RoseError
import com.livetranslator.controller.ui.theme.SoftLavender
import com.livetranslator.controller.ui.theme.VoidSurfaceVariant
import com.livetranslator.controller.ui.theme.ControllerGlassCard
import com.livetranslator.controller.ui.theme.ControllerGradientPillButton
import com.livetranslator.controller.ui.theme.ControllerTitle
import com.livetranslator.controller.ui.theme.controllerRadialBackground
import com.livetranslator.controller.ui.viewmodel.ControllerAuthViewModel

@Composable
fun ControllerGoogleAuthScreen(viewModel: ControllerAuthViewModel) {
  val context = LocalContext.current
  val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()
  val error by viewModel.error.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) {
    viewModel.attemptAutoSignIn(context)
  }

  Box(
    modifier = Modifier.fillMaxSize().controllerRadialBackground().testTag("controller_google_auth"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
      Box(
        modifier =
          Modifier.size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ElectricViolet.copy(alpha = 0.22f))
            .border(1.5.dp, ElectricViolet.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_shield),
          contentDescription = "متحكم ترجمة فورية",
          tint = SoftLavender,
          modifier = Modifier.size(32.dp),
        )
      }

      ControllerTitle("متحكم ترجمة فورية")

      Text(
        text = "لوحة القيادة الكاملة للإدارة: التحكم بالتطبيق الأصلي، نشر التحديثات الساخنة، إرسال الإشعارات، وإدارة أكواد المستخدمين.",
        color = LightGrayText,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
      )

      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          if (!error.isNullOrBlank()) {
            Text(text = error.orEmpty(), color = RoseError, style = MaterialTheme.typography.bodyMedium)
          }

          if (isSigningIn) {
            CircularProgressIndicator(
              color = ElectricViolet,
              modifier = Modifier.align(Alignment.CenterHorizontally),
            )
          }

          ControllerGradientPillButton(
            text = "تسجيل الدخول بحساب المدير (Google)",
            onClick = { viewModel.signInWithGoogle(context) },
            enabled = !isSigningIn,
            iconRes = R.drawable.ic_arrow_forward,
            modifier = Modifier.testTag("controller_google_signin_button"),
          )
        }
      }
    }
  }
}

@Composable
fun OperatorCredentialLockScreen(
  email: String,
  unlockError: String?,
  onUnlock: (username: String, password: String) -> Unit,
  onSignOut: () -> Unit,
) {
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }

  Box(
    modifier = Modifier.fillMaxSize().controllerRadialBackground().testTag("operator_lock_screen"),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Icon(
        painter = painterResource(id = R.drawable.ic_shield),
        contentDescription = null,
        tint = SoftLavender,
        modifier = Modifier.size(48.dp),
      )

      ControllerTitle("تأكيد هوية المدير")

      Text(
        text = "الحساب الموثق: $email",
        color = SoftLavender,
        style = MaterialTheme.typography.labelLarge,
      )

      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            singleLine = true,
            label = { Text("يوزر المدير", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth().testTag("operator_username_input"),
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
            label = { Text("كلمة مرور الإدارة", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth().testTag("operator_password_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          if (!unlockError.isNullOrBlank()) {
            Text(text = unlockError, color = RoseError, style = MaterialTheme.typography.bodyMedium)
          }

          ControllerGradientPillButton(
            text = "فتح لوحة التحكم الكاملة",
            onClick = { onUnlock(username, password) },
            iconRes = R.drawable.ic_check,
            modifier = Modifier.testTag("operator_unlock_button"),
          )

          TextButton(
            onClick = onSignOut,
            modifier = Modifier.align(Alignment.CenterHorizontally),
          ) {
            Text("تبديل الحساب", color = MutedGrayText)
          }
        }
      }
    }
  }
}

@Composable
fun AdminVerifyingScreen() {
  Box(
    modifier = Modifier.fillMaxSize().controllerRadialBackground(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      CircularProgressIndicator(color = ElectricViolet)
      Text("جاري التحقق من صلاحيات المدير...", color = SoftLavender)
    }
  }
}

@Composable
fun AdminDeniedScreen(message: String, onSignOut: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().controllerRadialBackground(),
    contentAlignment = Alignment.Center,
  ) {
    ControllerGlassCard(
      modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp).padding(24.dp),
      cornerRadius = 24.dp,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier =
            Modifier.size(56.dp)
              .clip(CircleShape)
              .background(RoseError.copy(alpha = 0.18f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_shield),
            contentDescription = null,
            tint = RoseError,
            modifier = Modifier.size(28.dp),
          )
        }
        Text("صلاحية غير متوفرة", color = CrispWhite, fontWeight = FontWeight.Bold)
        Text(message, color = LightGrayText, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
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
