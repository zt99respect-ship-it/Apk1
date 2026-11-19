package com.livetranslator.controller.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.controller.R
import com.livetranslator.controller.ui.theme.ControllerGlassCard
import com.livetranslator.controller.ui.theme.ControllerGradientPillButton
import com.livetranslator.controller.ui.theme.ControllerTitle
import com.livetranslator.controller.ui.theme.CrispWhite
import com.livetranslator.controller.ui.theme.ElectricViolet
import com.livetranslator.controller.ui.theme.EmeraldAccent
import com.livetranslator.controller.ui.theme.GlassWhiteBorder
import com.livetranslator.controller.ui.theme.LightGrayText
import com.livetranslator.controller.ui.theme.MutedGrayText
import com.livetranslator.controller.ui.theme.RoseError
import com.livetranslator.controller.ui.theme.SoftLavender
import com.livetranslator.controller.ui.theme.VoidSurfaceVariant
import com.livetranslator.controller.ui.viewmodel.ControllerConsoleViewModel

@Composable
fun NotificationsSenderScreen(viewModel: ControllerConsoleViewModel) {
  val broadcasts by viewModel.remoteNotifications.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedback by viewModel.statusFeedback.collectAsStateWithLifecycle()

  var title by remember { mutableStateOf("") }
  var body by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("announcement") }
  var updateAlert by remember { mutableStateOf(true) }

  Box(
    modifier = Modifier.fillMaxSize().testTag("notifications_sender_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 620.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ControllerTitle("إرسال إشعارات التطبيق")

      if (!feedback.isNullOrBlank()) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(14.dp))
              .background(EmeraldAccent.copy(alpha = 0.14f))
              .border(1.dp, EmeraldAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
              .padding(12.dp)
        ) {
          Text(text = feedback.orEmpty(), color = EmeraldAccent, fontWeight = FontWeight.Bold)
        }
      }

      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("إشعار عام لجميع المستخدمين", style = MaterialTheme.typography.titleLarge, color = CrispWhite)

          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            label = { Text("عنوان الإشعار", color = SoftLavender) },
            placeholder = { Text("تنبيه أو تحديث جديد...", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth().testTag("broadcast_title_input"),
            colors = adminFieldColors(),
          )

          OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            minLines = 3,
            maxLines = 5,
            label = { Text("نص الإشعار", color = SoftLavender) },
            placeholder = { Text("اكتب الرسالة التي ستظهر في شريط الإشعارات وداخل التطبيق...", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth().testTag("broadcast_body_input"),
            colors = adminFieldColors(),
          )

          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
              "announcement" to "إعلان",
              "update" to "تحديث",
              "warning" to "تنبيه هام",
            ).forEach { (catKey, label) ->
              val selected = category == catKey
              Box(
                modifier =
                  Modifier.weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) ElectricViolet.copy(alpha = 0.28f) else VoidSurfaceVariant)
                    .border(
                      1.dp,
                      if (selected) ElectricViolet else GlassWhiteBorder,
                      RoundedCornerShape(12.dp),
                    )
                    .clickable { category = catKey },
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = label,
                  color = if (selected) CrispWhite else LightGrayText,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("يفتح مركز التحديثات عند الضغط", color = CrispWhite, fontWeight = FontWeight.Bold)
              Text("يوجه المستخدم لتطبيق أي تصحيح ساخن", color = MutedGrayText, fontSize = 12.sp)
            }
            Switch(
              checked = updateAlert,
              onCheckedChange = { updateAlert = it },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = CrispWhite,
                  checkedTrackColor = ElectricViolet,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }

          ControllerGradientPillButton(
            text = if (isBusy) "جاري الإرسال..." else "إرسال الإشعار للجميع الآن",
            onClick = {
              viewModel.sendBroadcast(title, body, category, updateAlert)
              title = ""
              body = ""
            },
            enabled = !isBusy && title.isNotBlank() && body.isNotBlank(),
            iconRes = R.drawable.ic_bell,
            modifier = Modifier.testTag("send_broadcast_button"),
          )
        }
      }

      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("سجل الإشعارات المرسلة (${broadcasts.size})", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
          if (broadcasts.isEmpty()) {
            Text("لم ترسل أي إشعارات عامة بعد.", color = MutedGrayText)
          } else {
            broadcasts.forEachIndexed { idx, item ->
              if (idx > 0) HorizontalDivider(color = GlassWhiteBorder)
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
              ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Text(item.title, color = CrispWhite, fontWeight = FontWeight.Bold)
                  Text(item.body, color = LightGrayText, fontSize = 13.sp)
                  Text("التصنيف: ${item.category}", color = SoftLavender, fontSize = 11.sp)
                }
                TextButton(onClick = { viewModel.deleteBroadcast(item.id) }) {
                  Text("حذف", color = RoseError, fontSize = 12.sp)
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
