package com.livetranslator.controller.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.livetranslator.controller.data.HotfixProfileDoc
import com.livetranslator.controller.ui.theme.AmberAccent
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
fun HotfixPublisherScreen(viewModel: ControllerConsoleViewModel) {
  val hotfix by viewModel.hotfixProfile.collectAsStateWithLifecycle()
  val appControl by viewModel.appControl.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val feedback by viewModel.statusFeedback.collectAsStateWithLifecycle()

  var patchVersion by remember { mutableIntStateOf(hotfix.patchVersion.coerceAtLeast(1)) }
  var patchNotes by remember { mutableStateOf(hotfix.patchNotes) }
  var mandatoryHotfix by remember { mutableStateOf(hotfix.mandatoryHotfix) }
  var latestVersionName by remember { mutableStateOf(hotfix.latestAppVersionName.ifBlank { "1.0.0" }) }
  var originalApkUrl by remember { mutableStateOf(hotfix.apkDownloadUrl) }
  var controllerApkUrl by remember { mutableStateOf(hotfix.controllerApkUrl) }
  var glossaryMap by remember { mutableStateOf(hotfix.glossaryAdditions) }
  var noiseList by remember { mutableStateOf(hotfix.noiseFilterAdditions) }

  var newGlossaryKey by remember { mutableStateOf("") }
  var newGlossaryValue by remember { mutableStateOf("") }
  var newNoisePhrase by remember { mutableStateOf("") }

  LaunchedEffect(hotfix) {
    patchVersion = hotfix.patchVersion.coerceAtLeast(1)
    patchNotes = hotfix.patchNotes
    mandatoryHotfix = hotfix.mandatoryHotfix
    latestVersionName = hotfix.latestAppVersionName.ifBlank { "1.0.0" }
    originalApkUrl = hotfix.apkDownloadUrl
    controllerApkUrl = hotfix.controllerApkUrl
    glossaryMap = hotfix.glossaryAdditions
    noiseList = hotfix.noiseFilterAdditions
  }

  Box(
    modifier = Modifier.fillMaxSize().testTag("hotfix_publisher_screen"),
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
      ControllerTitle("ناشر التصحيح الساخن (Hotfix)")

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

      // Patch metadata card
      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text("رقم حزمة التصحيح الساخن", color = CrispWhite, fontWeight = FontWeight.Bold)
              Text("ارفع الرقم ليصل التحديث لجميع المستخدمين بالخلفية", color = MutedGrayText, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = { if (patchVersion > 1) patchVersion-- }) {
                Text("-", color = CrispWhite, fontWeight = FontWeight.Bold)
              }
              Text("v$patchVersion", color = SoftLavender, fontSize = 18.sp, fontWeight = FontWeight.Bold)
              Button(
                onClick = { patchVersion++ },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
              ) {
                Text("+", color = CrispWhite, fontWeight = FontWeight.Bold)
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("تحديث إجباري فوري", color = CrispWhite, fontWeight = FontWeight.Bold)
              Text("يطلب من المستخدم التطبيق المباشر دون زر تخطي", color = MutedGrayText, fontSize = 12.sp)
            }
            Switch(
              checked = mandatoryHotfix,
              onCheckedChange = { mandatoryHotfix = it },
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = CrispWhite,
                  checkedTrackColor = AmberAccent,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }

          OutlinedTextField(
            value = patchNotes,
            onValueChange = { patchNotes = it },
            minLines = 2,
            maxLines = 4,
            label = { Text("ملاحظات التصحيح الساخن", color = SoftLavender) },
            placeholder = { Text("تحسين ترجمة الألعاب وإضافة مصطلحات جديدة...", color = MutedGrayText) },
            modifier = Modifier.fillMaxWidth().testTag("hotfix_notes_input"),
            colors = adminFieldColors(),
          )
        }
      }

      // Dynamic Glossary Card
      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("القاموس الديناميكي الفوري (${glossaryMap.size})", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = newGlossaryKey,
              onValueChange = { newGlossaryKey = it },
              singleLine = true,
              label = { Text("الكلمة الأجنبية", color = SoftLavender) },
              modifier = Modifier.weight(1f),
              colors = adminFieldColors(),
            )
            OutlinedTextField(
              value = newGlossaryValue,
              onValueChange = { newGlossaryValue = it },
              singleLine = true,
              label = { Text("الترجمة العربية", color = SoftLavender) },
              modifier = Modifier.weight(1f),
              colors = adminFieldColors(),
            )
          }
          OutlinedButton(
            onClick = {
              val k = newGlossaryKey.trim().lowercase()
              val v = newGlossaryValue.trim()
              if (k.isNotBlank() && v.isNotBlank()) {
                glossaryMap = glossaryMap + (k to v)
                newGlossaryKey = ""
                newGlossaryValue = ""
              }
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("إضافة مصطلح للتحديث الساخن", color = SoftLavender)
          }

          glossaryMap.entries.forEach { (k, v) ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("$k ← $v", color = LightGrayText, modifier = Modifier.weight(1f))
              TextButton(onClick = { glossaryMap = glossaryMap - k }) {
                Text("حذف", color = RoseError, fontSize = 12.sp)
              }
            }
          }
        }
      }

      // Noise filters & GitHub APK links Card
      ControllerGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("مرشحات الضوضاء (${noiseList.size})", style = MaterialTheme.typography.titleLarge, color = CrispWhite)
          Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
              value = newNoisePhrase,
              onValueChange = { newNoisePhrase = it },
              singleLine = true,
              label = { Text("عبارة للتجاهل أو التصفية", color = SoftLavender) },
              modifier = Modifier.weight(1f),
              colors = adminFieldColors(),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                val clean = newNoisePhrase.trim().lowercase()
                if (clean.isNotBlank() && clean !in noiseList) {
                  noiseList = noiseList + clean
                  newNoisePhrase = ""
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            ) {
              Text("إضافة", color = CrispWhite)
            }
          }
          noiseList.forEach { phrase ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(phrase, color = LightGrayText)
              TextButton(onClick = { noiseList = noiseList - phrase }) {
                Text("حذف", color = RoseError, fontSize = 12.sp)
              }
            }
          }

          HorizontalDivider(color = GlassWhiteBorder)

          Text("روابط GitHub Releases للتطبيقين", color = CrispWhite, fontWeight = FontWeight.Bold)
          if (appControl.githubRepoSlug.isNotBlank()) {
            OutlinedButton(
              onClick = {
                viewModel.syncLatestGithubRelease(appControl.githubRepoSlug, hotfix) { orig, ctrl, tag ->
                  if (orig.isNotBlank()) originalApkUrl = orig
                  if (ctrl.isNotBlank()) controllerApkUrl = ctrl
                  if (tag.isNotBlank()) latestVersionName = tag
                }
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("جلب أحدث روابط APK من GitHub (${appControl.githubRepoSlug})", color = EmeraldAccent)
            }
          }

          OutlinedTextField(
            value = latestVersionName,
            onValueChange = { latestVersionName = it },
            singleLine = true,
            label = { Text("إصدار البناء الكامل", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth(),
            colors = adminFieldColors(),
          )
          OutlinedTextField(
            value = originalApkUrl,
            onValueChange = { originalApkUrl = it },
            singleLine = true,
            label = { Text("رابط APK التطبيق الأصلي", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth(),
            colors = adminFieldColors(),
          )
          OutlinedTextField(
            value = controllerApkUrl,
            onValueChange = { controllerApkUrl = it },
            singleLine = true,
            label = { Text("رابط APK تطبيق المدير", color = SoftLavender) },
            modifier = Modifier.fillMaxWidth(),
            colors = adminFieldColors(),
          )
        }
      }

      ControllerGradientPillButton(
        text = if (isBusy) "جاري النشر..." else "نشر التحديث الساخن v$patchVersion الآن",
        onClick = {
          viewModel.publishHotfix(
            HotfixProfileDoc(
              patchVersion = patchVersion,
              patchNotes = patchNotes,
              mandatoryHotfix = mandatoryHotfix,
              glossaryAdditions = glossaryMap,
              noiseFilterAdditions = noiseList,
              latestAppVersionName = latestVersionName,
              apkDownloadUrl = originalApkUrl,
              controllerApkUrl = controllerApkUrl,
            )
          )
        },
        enabled = !isBusy,
        iconRes = R.drawable.ic_hotfix,
        modifier = Modifier.testTag("publish_hotfix_button"),
      )

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}
