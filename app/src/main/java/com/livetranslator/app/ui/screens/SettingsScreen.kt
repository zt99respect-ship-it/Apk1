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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.ui.theme.AmberAccent
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.EmeraldAccent
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.RoseError
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidBackground
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onNavigateToAbout: () -> Unit,
) {
  val focusManager = LocalFocusManager.current
  val textToolbar = LocalTextToolbar.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()

  var langMenuExpanded by remember { mutableStateOf(false) }
  val selectedLanguage = SupportedLanguages.findByCode(settings.sourceLanguageCode)

  Box(
    modifier = Modifier.fillMaxSize().background(VoidBackground).testTag("settings_screen"),
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
      Text(
        text = "الإعدادات",
        style = MaterialTheme.typography.headlineMedium,
        color = LavenderWhite,
      )

      // Source Language dropdown (Vosk + ML Kit)
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "لغة الصوت المصدر (Vosk)",
            style = MaterialTheme.typography.titleMedium,
            color = LavenderWhite,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "اختر لغة البث لتفريغها محلياً عبر Vosk ثم ترجمتها للعربية عبر ML Kit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedViolet,
          )
          Spacer(modifier = Modifier.height(10.dp))

          ExposedDropdownMenuBox(
            expanded = langMenuExpanded,
            onExpandedChange = {
              focusManager.clearFocus(force = true)
              textToolbar.hide()
              langMenuExpanded = !langMenuExpanded
            },
          ) {
            Surface(
              modifier =
                Modifier.fillMaxWidth()
                  .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                  .clip(RoundedCornerShape(14.dp))
                  .border(
                    1.dp,
                    if (langMenuExpanded) ElectricViolet else VioletOutline,
                    RoundedCornerShape(14.dp),
                  )
                  .testTag("language_dropdown"),
              color = VoidSurfaceVariant,
              shape = RoundedCornerShape(14.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "${selectedLanguage.displayName} (~${selectedLanguage.approxSizeMb} MB)",
                  color = LavenderWhite,
                  style = MaterialTheme.typography.bodyLarge,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = langMenuExpanded)
              }
            }

            ExposedDropdownMenu(
              expanded = langMenuExpanded,
              onDismissRequest = { langMenuExpanded = false },
              modifier = Modifier.background(VoidSurface),
            ) {
              SupportedLanguages.all.forEach { lang ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = "${lang.displayName} (${lang.code.uppercase()})",
                      color = LavenderWhite,
                    )
                  },
                  onClick = {
                    viewModel.setSourceLanguageCode(lang.code)
                    langMenuExpanded = false
                  },
                )
              }
            }
          }
        }
      }

      // Offline Models Manager Card (NO API keys)
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth().testTag("offline_models_settings_card")) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "النماذج المحلية بدون إنترنت",
              style = MaterialTheme.typography.titleMedium,
              color = LavenderWhite,
            )
            Text(
              text = "مجاناً بلا حدود",
              style = MaterialTheme.typography.labelSmall,
              color = EmeraldAccent,
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(EmeraldAccent.copy(alpha = 0.16f))
                  .padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = modelsState.errorMessage ?: modelsState.stageMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = if (modelsState.errorMessage != null) RoseError else MutedViolet,
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            ModelStatusBadge(
              title = "نموذج Vosk (${selectedLanguage.displayName})",
              ready = modelsState.voskModelReady,
              modifier = Modifier.weight(1f),
            )
            ModelStatusBadge(
              title = "نموذج ML Kit (للعربية)",
              ready = modelsState.mlKitModelsReady,
              modifier = Modifier.weight(1f),
            )
          }

          if (modelsState.isDownloading) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
              progress = { (modelsState.progressPercent / 100f).coerceIn(0.02f, 1f) },
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
              color = ElectricViolet,
              trackColor = VoidSurfaceVariant,
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Button(
              onClick = { viewModel.downloadModelsFor(settings.sourceLanguageCode) },
              enabled = !modelsState.isDownloading,
              modifier = Modifier.weight(1f).height(48.dp).testTag("download_lang_models_button"),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
            ) {
              Text(
                text = if (modelsState.allReady) "تحديث النماذج" else "تنزيل النماذج",
                color = LavenderWhite,
              )
            }

            if (modelsState.voskModelReady && !modelsState.isDownloading) {
              OutlinedButton(
                onClick = { viewModel.deleteVoskModel(settings.sourceLanguageCode) },
                modifier = Modifier.weight(1f).height(48.dp).testTag("delete_vosk_model_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text("حذف نموذج الصوت", color = SoftLavender)
              }
            }
          }
        }
      }

      // Audio Chunk Size 2-15s
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "مدة المقطع الصوتي (Audio Chunk)",
                style = MaterialTheme.typography.titleMedium,
                color = LavenderWhite,
              )
              Text(
                text = "الحد الأقصى لتجميع الكلام المتواصل قبل ترجمته فوراً.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedViolet,
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = "${settings.audioChunkSeconds} ث",
              style = MaterialTheme.typography.titleLarge,
              color = SoftLavender,
            )
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                viewModel.setAudioChunkSeconds((settings.audioChunkSeconds - 1).coerceIn(2, 15))
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_minus),
                contentDescription = "تقليل المدة",
                tint = SoftLavender,
              )
            }
            Slider(
              value = settings.audioChunkSeconds.toFloat(),
              onValueChange = { viewModel.setAudioChunkSeconds(it.roundToInt()) },
              valueRange = 2f..15f,
              steps = 12,
              modifier = Modifier.weight(1f).testTag("chunk_size_slider"),
              colors =
                SliderDefaults.colors(
                  thumbColor = ElectricViolet,
                  activeTrackColor = ElectricViolet,
                  inactiveTrackColor = VoidSurfaceVariant,
                ),
            )
            IconButton(
              onClick = {
                viewModel.setAudioChunkSeconds((settings.audioChunkSeconds + 1).coerceIn(2, 15))
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = "زيادة المدة",
                tint = SoftLavender,
              )
            }
          }
        }
      }

      // Toggles
      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("تخطي الكلام العربي", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
              Text(
                "تجاهل الجمل المكتوبة بالعربية أصلاً دون إعادة ترجمتها.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedViolet,
              )
            }
            Switch(
              checked = settings.skipArabic,
              onCheckedChange = { viewModel.setSkipArabic(it) },
              modifier = Modifier.testTag("skip_arabic_switch"),
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = LavenderWhite,
                  checkedTrackColor = ElectricViolet,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                "استعادة الفقاعة عند التشغيل",
                style = MaterialTheme.typography.titleMedium,
                color = LavenderWhite,
              )
              Text(
                "إظهار الفقاعة العائمة تلقائياً بعد إعادة تشغيل الجهاز.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedViolet,
              )
            }
            Switch(
              checked = settings.autoRestartOnBoot,
              onCheckedChange = { viewModel.setAutoRestartOnBoot(it) },
              modifier = Modifier.testTag("boot_restart_switch"),
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = LavenderWhite,
                  checkedTrackColor = ElectricViolet,
                  uncheckedTrackColor = VoidSurfaceVariant,
                ),
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("المظهر", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
              Text(
                "الوضع الداكن البنفسجي مقفل لراحة العين ووضوح الترجمة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedViolet,
              )
            }
            Text(
              text = "داكن دائم",
              style = MaterialTheme.typography.labelSmall,
              color = SoftLavender,
              modifier =
                Modifier.clip(RoundedCornerShape(8.dp))
                  .background(ElectricViolet.copy(alpha = 0.22f))
                  .border(1.dp, VioletOutline, RoundedCornerShape(8.dp))
                  .padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      // About Link Card
      GlassSurfaceCard(
        modifier =
          Modifier.fillMaxWidth()
            .clickable(onClick = onNavigateToAbout)
            .testTag("settings_about_row")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_info),
              contentDescription = "حول التطبيق",
              tint = SoftLavender,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("حول LiveTranslator", style = MaterialTheme.typography.titleMedium, color = LavenderWhite)
              Text("الإصدار 1.0.0 • Vosk + ML Kit (بدون نت)", style = MaterialTheme.typography.bodyMedium, color = MutedViolet)
            }
          }
          Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = "فتح صفحة حول التطبيق",
            tint = MutedViolet,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun ModelStatusBadge(
  title: String,
  ready: Boolean,
  modifier: Modifier = Modifier,
) {
  val color = if (ready) EmeraldAccent else AmberAccent
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(VoidSurfaceVariant)
        .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.labelSmall,
      color = LavenderWhite,
      maxLines = 1,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = if (ready) "جاهز محلياً" else "غير منزّل",
        style = MaterialTheme.typography.labelSmall,
        color = color,
      )
    }
  }
}
