package com.livetranslator.controller.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.controller.data.ManagedUserDoc
import com.livetranslator.controller.ui.theme.ControllerGlassCard
import com.livetranslator.controller.ui.theme.ControllerTitle
import com.livetranslator.controller.ui.theme.CrispWhite
import com.livetranslator.controller.ui.theme.ElectricViolet
import com.livetranslator.controller.ui.theme.EmeraldAccent
import com.livetranslator.controller.ui.theme.GlassWhiteBorder
import com.livetranslator.controller.ui.theme.LightGrayText
import com.livetranslator.controller.ui.theme.MutedGrayText
import com.livetranslator.controller.ui.theme.RoseError
import com.livetranslator.controller.ui.theme.SoftLavender
import com.livetranslator.controller.ui.theme.VoidSurface
import com.livetranslator.controller.ui.viewmodel.ControllerConsoleViewModel

@Composable
fun UsersManagerScreen(viewModel: ControllerConsoleViewModel) {
  val context = LocalContext.current
  val users by viewModel.usersList.collectAsStateWithLifecycle()
  val feedback by viewModel.statusFeedback.collectAsStateWithLifecycle()

  var searchQuery by remember { mutableStateOf("") }
  var selectedUserForMessage by remember { mutableStateOf<ManagedUserDoc?>(null) }
  var selectedUserForBlock by remember { mutableStateOf<ManagedUserDoc?>(null) }

  val filtered =
    remember(users, searchQuery) {
      val q = searchQuery.trim().lowercase()
      if (q.isBlank()) users
      else {
        users.filter {
          it.username.lowercase().contains(q) ||
            it.email.lowercase().contains(q) ||
            it.secretCode.lowercase().contains(q)
        }
      }
    }

  Box(
    modifier = Modifier.fillMaxSize().testTag("users_manager_screen"),
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
      ControllerTitle("المستخدمون والأكواد (${users.size})")

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

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        singleLine = true,
        label = { Text("بحث باليوزر أو البريد أو الكود السري", color = SoftLavender) },
        modifier = Modifier.fillMaxWidth(),
        colors = adminFieldColors(),
      )

      if (filtered.isEmpty()) {
        ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
          Column(modifier = Modifier.padding(20.dp)) {
            Text("لا يوجد مستخدمون مطابقون للبحث.", color = MutedGrayText)
          }
        }
      } else {
        filtered.forEach { user ->
          ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = user.username.ifBlank { "بدون اسم" },
                    color = CrispWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                  )
                  Text(user.email, color = LightGrayText, fontSize = 12.sp)
                }
                Text(
                  text = if (user.blocked) "محظور" else "نشط • v${user.appliedPatchVersion}",
                  color = if (user.blocked) RoseError else EmeraldAccent,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                )
              }

              Row(
                modifier =
                  Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElectricViolet.copy(alpha = 0.15f))
                    .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "الكود السري: ${user.secretCode}",
                  color = SoftLavender,
                  fontWeight = FontWeight.Bold,
                )
                TextButton(
                  onClick = {
                    val cb = context.getSystemService(ClipboardManager::class.java)
                    cb?.setPrimaryClip(ClipData.newPlainText("UserSecretCode", user.secretCode))
                  }
                ) {
                  Text("نسخ", color = CrispWhite, fontSize = 12.sp)
                }
              }

              HorizontalDivider(color = GlassWhiteBorder)

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                  onClick = { selectedUserForMessage = user },
                  colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text("إرسال إشعار خاص", color = CrispWhite, fontSize = 12.sp)
                }

                OutlinedButton(
                  onClick = {
                    if (user.blocked) {
                      viewModel.setUserBlocked(user.userId, false, "")
                    } else {
                      selectedUserForBlock = user
                    }
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Text(
                    text = if (user.blocked) "فك الحظر" else "حظر المستخدم",
                    color = if (user.blocked) EmeraldAccent else RoseError,
                    fontSize = 12.sp,
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }

  selectedUserForMessage?.let { target ->
    var msgTitle by remember { mutableStateOf("رسالة من إدارة ترجمة فورية") }
    var msgBody by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { selectedUserForMessage = null },
      containerColor = VoidSurface,
      title = { Text("إشعار خاص إلى ${target.username}", color = CrispWhite, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = msgTitle,
            onValueChange = { msgTitle = it },
            singleLine = true,
            label = { Text("العنوان", color = SoftLavender) },
            colors = adminFieldColors(),
          )
          OutlinedTextField(
            value = msgBody,
            onValueChange = { msgBody = it },
            minLines = 3,
            label = { Text("نص الرسالة", color = SoftLavender) },
            colors = adminFieldColors(),
          )
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (msgTitle.isNotBlank() && msgBody.isNotBlank()) {
              viewModel.sendDirectInbox(target.userId, msgTitle, msgBody, "direct")
              selectedUserForMessage = null
            }
          }
        ) {
          Text("إرسال", color = SoftLavender)
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedUserForMessage = null }) {
          Text("إلغاء", color = MutedGrayText)
        }
      },
    )
  }

  selectedUserForBlock?.let { target ->
    var reason by remember { mutableStateOf("تم تقييد الحساب مؤقتاً من قِبل الإدارة.") }
    AlertDialog(
      onDismissRequest = { selectedUserForBlock = null },
      containerColor = VoidSurface,
      title = { Text("حظر ${target.username}؟", color = RoseError, fontWeight = FontWeight.Bold) },
      text = {
        OutlinedTextField(
          value = reason,
          onValueChange = { reason = it },
          minLines = 2,
          label = { Text("سبب الحظر", color = SoftLavender) },
          colors = adminFieldColors(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.setUserBlocked(target.userId, true, reason)
            selectedUserForBlock = null
          }
        ) {
          Text("تأكيد الحظر", color = RoseError)
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedUserForBlock = null }) {
          Text("إلغاء", color = MutedGrayText)
        }
      },
    )
  }
}
