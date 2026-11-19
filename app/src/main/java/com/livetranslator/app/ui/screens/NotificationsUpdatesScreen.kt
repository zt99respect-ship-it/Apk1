package com.livetranslator.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.remote.UserInboxDoc
import com.livetranslator.app.engine.OtaUpdateManager
import com.livetranslator.app.ui.theme.AmberAccent
import com.livetranslator.app.ui.theme.AppBrandBadge
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
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.UpdatesCenterViewModel

@Composable
fun NotificationsUpdatesScreen(
  viewModel: UpdatesCenterViewModel,
  inboxMessages: List<UserInboxDoc> = emptyList(),
  onMarkInboxRead: ((String) -> Unit)? = null,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val otaState by viewModel.otaState.collectAsStateWithLifecycle()

  var feedUrlInput by remember { mutableStateOf(settings.otaFeedUrl) }
  LaunchedEffect(settings.otaFeedUrl) {
    if (feedUrlInput.isBlank() && settings.otaFeedUrl.isNotBlank()) {
      feedUrlInput = settings.otaFeedUrl
    }
  }

  var notifTitle by remember { mutableStateOf("ترجمة فورية") }
  var notifBody by remember { mutableStateOf("تم تطبيق تحسينات الترجمة الفورية بنجاح!") }
  var notifIsUpdateAlert by remember { mutableStateOf(true) }
  var feedbackNotice by remember { mutableStateOf<String?>(null) }

  val importPatchLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        viewModel.importLocalPatch(uri)
        feedbackNotice = "تم استيراد ملف التعديل وتطبيقه مباشرة."
      }
    }

  val notifPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        viewModel.sendCustomNotification(notifTitle, notifBody, notifIsUpdateAlert)
        feedbackNotice = "تم إرسال الإشعار بنجاح."
      } else {
        feedbackNotice = "يرجى السماح بإذن الإشعارات لعرض التنبيه في شريط النظام."
      }
    }

  fun triggerSendNotification() {
    val needsPermission =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
    if (needsPermission) {
      notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.sendCustomNotification(notifTitle, notifBody, notifIsUpdateAlert)
      feedbackNotice = "تم إرسال الإشعار وإضافته للسجل."
    }
  }

  Box(
    modifier = Modifier.fillMaxSize().testTag("updates_center_screen"),
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
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularGlassIconButton(
          iconRes = R.drawable.ic_arrow_back,
          contentDescription = "رجوع",
          onClick = onBack,
          modifier = Modifier.testTag("updates_back_button"),
        )
        AppBrandBadge()
      }

      GlowingScreenTitle(text = "التحديثات والإشعارات", fontSizeSp = 28)

      // Card 1: Incremental In-App Updates
      GlassSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("ota_updates_card"),
        cornerRadius = 24.dp,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_update),
                contentDescription = null,
                tint = SoftLavender,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "التحديثات الجزئية داخل التطبيق",
                style = MaterialTheme.typography.titleLarge,
                color = CrispWhite,
              )
            }

            Text(
              text = "v${otaState.currentPatchVersion}",
              color = EmeraldAccent,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              modifier =
                Modifier.clip(RoundedCornerShape(50))
                  .background(EmeraldAccent.copy(alpha = 0.14f))
                  .border(1.dp, EmeraldAccent.copy(alpha = 0.4f), RoundedCornerShape(50))
                  .padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }

          Text(
            text =
              "تنزّل التعديلات وتحسينات القاموس وتصفية الكلام العربي كحزم خفيفة فوراً داخل التطبيق دون الحاجة لتنزيل التطبيق كاملاً من جديد.",
            style = MaterialTheme.typography.bodyMedium,
            color = LightGrayText,
          )

          Row(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VoidSurfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "مصطلحات نشطة: ${otaState.glossaryCount}",
              color = SoftLavender,
              style = MaterialTheme.typography.labelLarge,
            )
            Text(
              text = "قواعد التصفية: ${otaState.noiseRuleCount}",
              color = LightGrayText,
              style = MaterialTheme.typography.labelLarge,
            )
          }

          Text(
            text = otaState.lastStatusMessage,
            color = if (otaState.hasPendingPatchUpdate) AmberAccent else EmeraldAccent,
            style = MaterialTheme.typography.bodyMedium,
          )

          if (otaState.isChecking || otaState.isApplyingPatch) {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
              color = ElectricViolet,
              trackColor = VoidSurfaceVariant,
            )
          }

          OutlinedTextField(
            value = feedUrlInput,
            onValueChange = { feedUrlInput = it },
            singleLine = true,
            label = { Text("رابط مركز التحديثات (JSON)", color = SoftLavender) },
            placeholder = { Text("https://.../updates.json", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth().testTag("ota_feed_url_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          Button(
            onClick = { viewModel.checkUpdatesNow(feedUrlInput) },
            enabled = !otaState.isChecking && !otaState.isApplyingPatch,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("check_updates_button"),
          ) {
            Text("فحص التحديثات الآن", color = CrispWhite, fontWeight = FontWeight.Bold)
          }

          val manifest = otaState.availableManifest
          if (otaState.hasPendingPatchUpdate && manifest != null) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(16.dp))
                  .background(ElectricViolet.copy(alpha = 0.16f))
                  .border(1.dp, ElectricViolet.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                  .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  text = "تحديث جزئي جاهز: v${manifest.patchVersion}",
                  color = CrispWhite,
                  fontWeight = FontWeight.Bold,
                )
                if (manifest.patchNotes.isNotBlank()) {
                  Text(
                    text = manifest.patchNotes,
                    color = LightGrayText,
                    style = MaterialTheme.typography.bodyMedium,
                  )
                }
                GlassGradientPillButton(
                  text = "تنزيل وتطبيق التعديلات فقط",
                  onClick = { viewModel.applyPendingPatch() },
                  enabled = !otaState.isApplyingPatch,
                  iconRes = R.drawable.ic_check,
                  modifier = Modifier.testTag("apply_patch_button"),
                )
              }
            }
          }

          OutlinedButton(
            onClick = {
              importPatchLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("import_local_patch_button"),
          ) {
            Text("استيراد ملف تعديل (.json) من الجهاز", color = SoftLavender)
          }

          if (otaState.hasNewBinaryRelease && manifest != null && manifest.apkDownloadUrl.isNotBlank()) {
            OutlinedButton(
              onClick = {
                runCatching {
                  context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(manifest.apkDownloadUrl))
                  )
                }
              },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_apk_release_button"),
            ) {
              Text(
                text = "فتح رابط نسخة APK الكاملة (${manifest.latestAppVersionName})",
                color = AmberAccent,
              )
            }
          }
        }
      }

      // Card 2: Notification Composer Studio
      GlassSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("notification_studio_card"),
        cornerRadius = 24.dp,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_bell),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "إرسال إشعارات التطبيق",
              style = MaterialTheme.typography.titleLarge,
              color = CrispWhite,
            )
          }

          Text(
            text = "يمكنك إرسال إشعار مباشر أو إشعار تحديث فوري، كما يمكنك نسخ قالب JSON لنشر تنبيهات وتعديلات عن بُعد.",
            style = MaterialTheme.typography.bodyMedium,
            color = LightGrayText,
          )

          OutlinedTextField(
            value = notifTitle,
            onValueChange = { notifTitle = it },
            singleLine = true,
            label = { Text("عنوان الإشعار", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth().testTag("notif_title_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          OutlinedTextField(
            value = notifBody,
            onValueChange = { notifBody = it },
            minLines = 2,
            maxLines = 4,
            label = { Text("نص الإشعار", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth().testTag("notif_body_input"),
            colors =
              OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricViolet,
                unfocusedBorderColor = GlassWhiteBorder,
                focusedTextColor = CrispWhite,
                unfocusedTextColor = CrispWhite,
              ),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("تنبيه تحديث يفتح المركز عند الضغط", color = CrispWhite, style = MaterialTheme.typography.titleMedium)
              Text("يفتح شاشة التحديثات والإشعارات مباشرة", color = MutedGrayText, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
              checked = notifIsUpdateAlert,
              onCheckedChange = { notifIsUpdateAlert = it },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = CrispWhite,
                  checkedTrackColor = ElectricViolet,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }

          feedbackNotice?.let { msg ->
            Text(text = msg, color = EmeraldAccent, style = MaterialTheme.typography.bodyMedium)
          }

          GlassGradientPillButton(
            text = "إرسال إشعار الآن",
            onClick = { triggerSendNotification() },
            iconRes = R.drawable.ic_bell,
            modifier = Modifier.testTag("send_notification_button"),
          )

          OutlinedButton(
            onClick = {
              val clipboard = context.getSystemService(ClipboardManager::class.java)
              clipboard?.setPrimaryClip(
                ClipData.newPlainText(
                  "OtaTemplate",
                  OtaUpdateManager.sampleManifestJsonTemplate(),
                )
              )
              feedbackNotice = "تم نسخ قالب التحديث والإشعارات بصيغة JSON."
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("copy_ota_template_button"),
          ) {
            Text("نسخ قالب JSON للتحديث والإشعار", color = SoftLavender)
          }
        }
      }

      if (inboxMessages.isNotEmpty()) {
        GlassSurfaceCard(
          modifier = Modifier.fillMaxWidth().testTag("user_inbox_card"),
          cornerRadius = 24.dp,
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "رسائل الإدارة الخاصة بك",
              style = MaterialTheme.typography.titleLarge,
              color = CrispWhite,
            )
            inboxMessages.forEachIndexed { idx, msg ->
              if (idx > 0) {
                HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
              }
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = msg.title,
                    color = if (msg.read) LightGrayText else CrispWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                  )
                  if (!msg.read && onMarkInboxRead != null) {
                    OutlinedButton(
                      onClick = { onMarkInboxRead(msg.id) },
                      shape = RoundedCornerShape(10.dp),
                    ) {
                      Text("تمت القراءة", color = EmeraldAccent, fontSize = 11.sp)
                    }
                  }
                }
                Text(
                  text = msg.body,
                  color = LightGrayText,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }
        }
      }

      // Card 3: Alerts History Log
      GlassSurfaceCard(
        modifier = Modifier.fillMaxWidth().testTag("alerts_history_card"),
        cornerRadius = 24.dp,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "سجل التحديثات والإشعارات",
            style = MaterialTheme.typography.titleLarge,
            color = CrispWhite,
          )

          if (otaState.alerts.isEmpty()) {
            Text(
              text = "لا توجد إشعارات مسجلة بعد.",
              color = MutedGrayText,
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            otaState.alerts.forEachIndexed { idx, alert ->
              if (idx > 0) {
                HorizontalDivider(color = GlassWhiteBorder, thickness = 1.dp)
              }
              Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = alert.title,
                    color = CrispWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = alert.formattedTime(),
                    color = if (alert.isUpdateAlert) SoftLavender else MutedGrayText,
                    fontSize = 11.sp,
                  )
                }
                Text(
                  text = alert.body,
                  color = LightGrayText,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
