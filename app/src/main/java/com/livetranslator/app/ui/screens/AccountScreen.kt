package com.livetranslator.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.remote.CryptoPasswordHelper
import com.livetranslator.app.data.remote.UserProfileDoc
import com.livetranslator.app.ui.theme.AppBrandBadge
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.ElectricViolet
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
import com.livetranslator.app.ui.viewmodel.AccountViewModel

@Composable
fun AccountScreen(
  viewModel: AccountViewModel,
  userProfile: UserProfileDoc? = null,
  onUpdateCredentials: ((newUsername: String?, newPassword: String?) -> Unit)? = null,
  onSignOut: (() -> Unit)? = null,
  onNavigateToDevices: () -> Unit,
  onNavigateToTranslationStyle: () -> Unit,
  onNavigateToSettings: () -> Unit,
  onNavigateToUpdates: () -> Unit,
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val otaState by viewModel.otaState.collectAsStateWithLifecycle()

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var isSecretCodeRevealed by remember { mutableStateOf(false) }
  var codeCopiedNotice by remember { mutableStateOf(false) }
  var showHelpDialog by remember { mutableStateOf(false) }
  var showTermsDialog by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  var showResetConfirm by remember { mutableStateOf(false) }

  val langName = SupportedLanguages.findByCode(settings.sourceLanguageCode).displayName
  val displayAccountName = userProfile?.username?.ifBlank { settings.accountLabel } ?: settings.accountLabel
  val displayEmail = userProfile?.email.orEmpty()

  Box(
    modifier = Modifier.fillMaxSize().testTag("account_screen"),
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
      // Top Header matching Screenshot 1 & 5
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        AppBrandBadge()
      }

      GlowingScreenTitle(text = "حسابي", fontSizeSp = 30)

      // Profile row matching Screenshot 1
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { showEditProfileDialog = true }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = displayAccountName,
            color = CrispWhite,
            style = MaterialTheme.typography.titleMedium,
          )
          if (displayEmail.isNotBlank()) {
            Text(
              text = displayEmail,
              color = MutedGrayText,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
          modifier =
            Modifier.size(48.dp)
              .clip(CircleShape)
              .background(GlassCardFillStrong)
              .border(1.dp, GlassWhiteBorder, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_person),
            contentDescription = "تعديل الاسم",
            tint = CrispWhite,
            modifier = Modifier.size(22.dp),
          )
        }
      }

      // Main Purple CTA Button matching Screenshot 1
      GlassGradientPillButton(
        text = "تخصيص شكل الترجمة",
        onClick = onNavigateToTranslationStyle,
        iconRes = R.drawable.ic_arrow_forward,
        modifier = Modifier.testTag("account_customize_style_cta"),
      )

      // Hidden Secret User Code Card (requires button press to reveal)
      if (userProfile != null && userProfile.secretCode.isNotBlank()) {
        GlassSurfaceCard(
          modifier = Modifier.fillMaxWidth().testTag("account_secret_code_card"),
          cornerRadius = 24.dp,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "كودك السري المخفي",
                style = MaterialTheme.typography.titleLarge,
                color = CrispWhite,
              )
              Text(
                text = if (isSecretCodeRevealed) "ظاهر الآن" else "مخفي",
                color = if (isSecretCodeRevealed) SoftLavender else MutedGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
              )
            }

            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(GlassCardFillStrong)
                  .border(1.dp, ElectricViolet.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                  .padding(vertical = 14.dp, horizontal = 16.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text =
                  if (isSecretCodeRevealed) userProfile.secretCode
                  else CryptoPasswordHelper.maskSecretCode(userProfile.secretCode),
                color = if (isSecretCodeRevealed) SoftLavender else LightGrayText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.testTag("secret_code_value"),
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = {
                  isSecretCodeRevealed = !isSecretCodeRevealed
                  codeCopiedNotice = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("reveal_secret_code_button"),
              ) {
                Text(
                  text = if (isSecretCodeRevealed) "إخفاء الكود" else "إظهار الكود المخفي",
                  color = CrispWhite,
                  fontWeight = FontWeight.Bold,
                )
              }

              if (isSecretCodeRevealed) {
                OutlinedButton(
                  onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                      ClipData.newPlainText("SecretCode", userProfile.secretCode)
                    )
                    codeCopiedNotice = true
                  },
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.height(48.dp).testTag("copy_secret_code_button"),
                ) {
                  Text("نسخ", color = SoftLavender)
                }
              }
            }

            if (codeCopiedNotice) {
              Text(
                text = "تم نسخ كودك السري.",
                color = SoftLavender,
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          }
        }
      }

      // Card 1: Service & Engine Status (matching Subscription card placement in Screenshot 1)
      GlassSurfaceCard(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onNavigateToDevices)
            .testTag("account_service_status_card"),
        cornerRadius = 24.dp,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(20.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "حالة الخدمة والترجمة",
              style = MaterialTheme.typography.titleLarge,
              color = CrispWhite,
            )
            Text(
              text = "${settings.speechEnginePreference.displayName}",
              style = MaterialTheme.typography.bodyLarge,
              color = LightGrayText,
            )
            Text(
              text = "اللغة المصدر: $langName • ${settings.audioSourcePreference.displayName}",
              style = MaterialTheme.typography.bodyMedium,
              color = MutedGrayText,
            )
          }

          Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = LightGrayText,
            modifier = Modifier.size(18.dp),
          )
        }
      }

      // Card 2: Menu List Card (matching Screenshot 1 & 5)
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
          AccountMenuRow(
            iconRes = R.drawable.ic_person,
            title = "تغيير اليوزر وكلمة المرور",
            onClick = { showPasswordDialog = true },
            testTag = "account_row_password",
          )
          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
          AccountMenuRow(
            iconRes = R.drawable.ic_brush,
            title = "شكل الترجمة",
            onClick = onNavigateToTranslationStyle,
            testTag = "account_row_style",
          )
          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
          AccountMenuRow(
            iconRes = R.drawable.ic_update,
            title =
              if (otaState.hasPendingPatchUpdate) "التحديثات والإشعارات • جديد"
              else "التحديثات والإشعارات",
            onClick = onNavigateToUpdates,
            testTag = "account_row_updates",
          )
          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
          AccountMenuRow(
            iconRes = R.drawable.ic_settings,
            title = "الإعدادات",
            onClick = onNavigateToSettings,
            testTag = "account_row_settings",
          )
          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
          AccountMenuRow(
            iconRes = R.drawable.ic_help,
            title = "المساعدة",
            onClick = { showHelpDialog = true },
            testTag = "account_row_help",
          )
          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
          AccountMenuRow(
            iconRes = R.drawable.ic_info,
            title = "الشروط والخصوصية",
            onClick = { showTermsDialog = true },
            testTag = "account_row_terms",
          )
        }
      }

      // Card 3: Clear & Reset Card (matching bottom card in Screenshot 5)
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { showClearConfirm = true }
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .testTag("account_clear_sessions_row"),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_trash),
              contentDescription = null,
              tint = RoseError,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "مسح سجل الجلسات",
              color = CrispWhite,
              style = MaterialTheme.typography.titleMedium,
            )
          }

          HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clickable { showResetConfirm = true }
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .testTag("account_reset_settings_row"),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_warning),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "إعادة ضبط الإعدادات",
              color = LightGrayText,
              style = MaterialTheme.typography.titleMedium,
            )
          }

          if (onSignOut != null) {
            HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable(onClick = onSignOut)
                  .padding(horizontal = 20.dp, vertical = 18.dp)
                  .testTag("account_sign_out_row"),
              horizontalArrangement = Arrangement.Start,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = RoseError,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "تسجيل الخروج",
                color = RoseError,
                style = MaterialTheme.typography.titleMedium,
              )
            }
          }
        }
      }

      Text(
        text = "الإصدار 1.0.0",
        color = MutedGrayText,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  if (showEditProfileDialog) {
    var draftLabel by remember(displayAccountName) { mutableStateOf(displayAccountName) }
    AlertDialog(
      onDismissRequest = { showEditProfileDialog = false },
      containerColor = VoidSurface,
      title = { Text("تعديل اسم العرض", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = draftLabel,
          onValueChange = { draftLabel = it },
          singleLine = true,
          label = { Text("اسم المستخدم", color = SoftLavender) },
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ElectricViolet,
              unfocusedBorderColor = GlassWhiteBorder,
              focusedTextColor = CrispWhite,
              unfocusedTextColor = CrispWhite,
            ),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.setAccountLabel(draftLabel)
            onUpdateCredentials?.invoke(draftLabel, null)
            showEditProfileDialog = false
          }
        ) {
          Text("حفظ", color = SoftLavender)
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditProfileDialog = false }) {
          Text("إلغاء", color = MutedGrayText)
        }
      },
    )
  }

  if (showPasswordDialog) {
    var draftUsername by remember(displayAccountName) { mutableStateOf(displayAccountName) }
    var draftNewPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
      onDismissRequest = { showPasswordDialog = false },
      containerColor = VoidSurface,
      title = { Text("تغيير اليوزر وكلمة المرور", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = draftUsername,
            onValueChange = { draftUsername = it },
            singleLine = true,
            label = { Text("اسم المستخدم الجديد", color = SoftLavender) },
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )
          OutlinedTextField(
            value = draftNewPassword,
            onValueChange = { draftNewPassword = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            label = { Text("كلمة المرور الجديدة (اتركها فارغة للإبقاء)", color = SoftLavender) },
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )
          if (!passwordError.isNullOrBlank()) {
            Text(passwordError.orEmpty(), color = RoseError, fontSize = 12.sp)
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (draftUsername.trim().length < 2) {
              passwordError = "اسم المستخدم يجب أن يكون حرفين على الأقل."
              return@TextButton
            }
            if (draftNewPassword.isNotEmpty() && draftNewPassword.length < 6) {
              passwordError = "كلمة المرور الجديدة يجب أن تكون 6 أحرف على الأقل."
              return@TextButton
            }
            viewModel.setAccountLabel(draftUsername)
            onUpdateCredentials?.invoke(
              draftUsername.trim(),
              draftNewPassword.takeIf { it.isNotEmpty() },
            )
            showPasswordDialog = false
          }
        ) {
          Text("حفظ", color = SoftLavender)
        }
      },
      dismissButton = {
        TextButton(onClick = { showPasswordDialog = false }) {
          Text("إلغاء", color = MutedGrayText)
        }
      },
    )
  }

  if (showHelpDialog) {
    AlertDialog(
      onDismissRequest = { showHelpDialog = false },
      containerColor = VoidSurface,
      title = { Text("كيف يعمل ترجمة فورية؟", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text =
            "1. اسمح بإذن «العرض فوق التطبيقات».\n" +
              "2. اختر «ترجمة الصوت» لليوتيوب والبثوث أو «ترجمة الشاشة» للألعاب والنصوص.\n" +
              "3. يتخطى التطبيق الكلام العربي تلقائياً دون تشويش في البثوث.\n" +
              "4. يمكنك تحديث التعديلات وإرسال الإشعارات من «التحديثات والإشعارات».",
          color = LightGrayText,
          lineHeight = 22.sp,
        )
      },
      confirmButton = {
        TextButton(onClick = { showHelpDialog = false }) {
          Text("فهمت", color = SoftLavender)
        }
      },
    )
  }

  if (showTermsDialog) {
    AlertDialog(
      onDismissRequest = { showTermsDialog = false },
      containerColor = VoidSurface,
      title = { Text("الشروط والخصوصية", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text =
            "يعمل التطبيق على التقاط الصوت فقط أثناء تفعيل جلسة الترجمة عبر إشعار واضح للمستخدم، " +
              "ويتم حفظ الجلسات والترجمات محلياً على جهازك فقط ويمكنك مسحها بالكامل في أي وقت.",
          color = LightGrayText,
          lineHeight = 22.sp,
        )
      },
      confirmButton = {
        TextButton(onClick = { showTermsDialog = false }) {
          Text("إغلاق", color = SoftLavender)
        }
      },
    )
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      containerColor = VoidSurface,
      title = { Text("مسح سجل الجلسات؟", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = { Text("سيتم حذف جميع الجلسات والنصوص المترجمة من الجهاز.", color = LightGrayText) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.clearAllSessionsAndHistory()
            showClearConfirm = false
          }
        ) {
          Text("مسح", color = RoseError)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text("إلغاء", color = SoftLavender)
        }
      },
    )
  }

  if (showResetConfirm) {
    AlertDialog(
      onDismissRequest = { showResetConfirm = false },
      containerColor = VoidSurface,
      title = { Text("إعادة ضبط الإعدادات؟", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = { Text("سيتم إرجاع اللغة والخط والألوان ومصدر الصوت إلى الوضع الافتراضي.", color = LightGrayText) },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.resetAllSettings()
            showResetConfirm = false
          }
        ) {
          Text("إعادة ضبط", color = RoseError)
        }
      },
      dismissButton = {
        TextButton(onClick = { showResetConfirm = false }) {
          Text("إلغاء", color = SoftLavender)
        }
      },
    )
  }
}

@Composable
private fun AccountMenuRow(
  iconRes: Int,
  title: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 18.dp)
        .testTag(testTag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = title,
        tint = SoftLavender,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = title,
        color = CrispWhite,
        style = MaterialTheme.typography.titleMedium,
      )
    }

    Icon(
      painter = painterResource(id = R.drawable.ic_chevron_right),
      contentDescription = null,
      tint = LightGrayText,
      modifier = Modifier.size(16.dp),
    )
  }
}
