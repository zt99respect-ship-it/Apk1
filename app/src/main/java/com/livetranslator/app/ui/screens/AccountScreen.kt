package com.livetranslator.app.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.model.SupportedLanguages
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
  onNavigateToDevices: () -> Unit,
  onNavigateToTranslationStyle: () -> Unit,
  onNavigateToSettings: () -> Unit,
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showHelpDialog by remember { mutableStateOf(false) }
  var showTermsDialog by remember { mutableStateOf(false) }
  var showClearConfirm by remember { mutableStateOf(false) }
  var showResetConfirm by remember { mutableStateOf(false) }

  val langName = SupportedLanguages.findByCode(settings.sourceLanguageCode).displayName

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
        Text(
          text = settings.accountLabel,
          color = LightGrayText,
          style = MaterialTheme.typography.titleMedium,
        )
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
            iconRes = R.drawable.ic_brush,
            title = "شكل الترجمة",
            onClick = onNavigateToTranslationStyle,
            testTag = "account_row_style",
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
    var draftLabel by remember(settings.accountLabel) { mutableStateOf(settings.accountLabel) }
    AlertDialog(
      onDismissRequest = { showEditProfileDialog = false },
      containerColor = VoidSurface,
      title = { Text("تعديل اسم العرض", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = draftLabel,
          onValueChange = { draftLabel = it },
          singleLine = true,
          label = { Text("الاسم أو البريد", color = SoftLavender) },
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

  if (showHelpDialog) {
    AlertDialog(
      onDismissRequest = { showHelpDialog = false },
      containerColor = VoidSurface,
      title = { Text("كيف يعمل LiveTranslator؟", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text =
            "1. اسمح بإذن «العرض فوق التطبيقات».\n" +
              "2. اضغط «ابدأ الترجمة» من الرئيسية واسمح بالتقاط صوت الهاتف.\n" +
              "3. افتح يوتيوب أو أي فيديو أو بث مباشر وستظهر الترجمة العربية في الصندوق الزجاجي العائم.\n" +
              "4. يمكنك تخصيص الخط والحجم واللون والشفافية من «شكل الترجمة».",
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
