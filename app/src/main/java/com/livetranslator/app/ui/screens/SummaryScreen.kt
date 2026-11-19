package com.livetranslator.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.R
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.data.local.db.TranslationSessionEntity
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.service.TranslationForegroundService
import com.livetranslator.app.ui.theme.AppBrandBadge
import com.livetranslator.app.ui.theme.ArabicFontRegistry
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
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.MainViewModel
import com.livetranslator.app.ui.viewmodel.SummaryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun SummaryScreen(
  viewModel: SummaryViewModel,
  mainViewModel: MainViewModel,
  onNavigateToHome: () -> Unit,
) {
  val context = LocalContext.current
  val app = context.applicationContext as LiveTranslatorApp
  val scope = rememberCoroutineScope()

  val sessions by viewModel.sessions.collectAsStateWithLifecycle()
  val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val settings by mainViewModel.settings.collectAsStateWithLifecycle()
  val sessionState by mainViewModel.sessionState.collectAsStateWithLifecycle()

  var showClearDialog by remember { mutableStateOf(false) }

  val overlayPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

  val projectionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        app.sessionManager.setPendingMediaProjection(result.resultCode, result.data)
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      }
    }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START_MIC_ONLY
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      }
    }

  fun triggerStartOrStop() {
    if (sessionState.isRunning) {
      val stopIntent =
        Intent(context, TranslationForegroundService::class.java).apply {
          action = TranslationForegroundService.ACTION_STOP
        }
      context.startService(stopIntent)
      return
    }
    if (!Settings.canDrawOverlays(context)) {
      overlayPermissionLauncher.launch(
        Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}"),
        )
      )
      return
    }
    if (settings.audioSourcePreference == AudioSourcePreference.MICROPHONE) {
      val hasMic =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      if (hasMic) {
        val serviceIntent =
          Intent(context, TranslationForegroundService::class.java).apply {
            action = TranslationForegroundService.ACTION_START_MIC_ONLY
          }
        ContextCompat.startForegroundService(context, serviceIntent)
      } else {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    } else {
      val mgr =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
      projectionLauncher.launch(mgr.createScreenCaptureIntent())
    }
  }

  val exportLauncher =
    rememberLauncherForActivityResult(
      ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
      if (uri != null) {
        scope.launch {
          val txt = viewModel.buildExportText()
          runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
              out.write(txt.toByteArray(Charsets.UTF_8))
            }
          }
        }
      }
    }

  val isTotallyEmpty = sessions.isEmpty() && historyItems.isEmpty() && searchQuery.isBlank()

  Box(
    modifier = Modifier.fillMaxSize().testTag("summary_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).padding(horizontal = 20.dp)
    ) {
      Spacer(modifier = Modifier.height(16.dp))

      // Top Header matching Screenshot 2
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (!isTotallyEmpty) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
              onClick = {
                val stamp =
                  SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                exportLauncher.launch("LiveTranslator_$stamp.txt")
              },
              modifier =
                Modifier.size(44.dp)
                  .clip(CircleShape)
                  .background(GlassCardFillStrong)
                  .border(1.dp, GlassWhiteBorder, CircleShape)
                  .testTag("export_history_button"),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_share),
                contentDescription = "تصدير السجل",
                tint = SoftLavender,
                modifier = Modifier.size(20.dp),
              )
            }

            IconButton(
              onClick = { showClearDialog = true },
              modifier =
                Modifier.size(44.dp)
                  .clip(CircleShape)
                  .background(GlassCardFillStrong)
                  .border(1.dp, GlassWhiteBorder, CircleShape)
                  .testTag("clear_history_button"),
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_trash),
                contentDescription = "مسح الجلسات",
                tint = RoseError,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        } else {
          Spacer(modifier = Modifier.size(44.dp))
        }

        AppBrandBadge()
      }

      Spacer(modifier = Modifier.height(18.dp))

      GlowingScreenTitle(
        text = stringResource(R.string.summary_title),
        fontSizeSp = 30,
      )

      if (isTotallyEmpty) {
        // Exact Screenshot 2 Empty State
        Column(
          modifier = Modifier.fillMaxWidth().weight(1f),
          horizontalAlignment = Alignment.Start,
        ) {
          Spacer(modifier = Modifier.height(42.dp))

          Icon(
            painter = painterResource(id = R.drawable.ic_summary),
            contentDescription = null,
            tint = SoftLavender,
            modifier = Modifier.size(44.dp),
          )

          Spacer(modifier = Modifier.height(18.dp))

          Text(
            text = stringResource(R.string.summary_empty_title),
            style = MaterialTheme.typography.headlineMedium,
            color = CrispWhite,
            fontWeight = FontWeight.Bold,
          )

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = stringResource(R.string.summary_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = LightGrayText,
            lineHeight = 26.sp,
          )

          Spacer(modifier = Modifier.weight(1f))

          GlassGradientPillButton(
            text =
              if (sessionState.isRunning) stringResource(R.string.home_stop_cta)
              else stringResource(R.string.home_start_cta),
            onClick = { triggerStartOrStop() },
            isActiveStop = sessionState.isRunning,
            iconRes = if (sessionState.isRunning) R.drawable.ic_stop else R.drawable.ic_arrow_forward,
            modifier = Modifier.testTag("summary_start_cta"),
          )

          Spacer(modifier = Modifier.height(20.dp))
        }
      } else {
        Spacer(modifier = Modifier.height(14.dp))

        // Search input
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { viewModel.updateSearchQuery(it) },
          placeholder = {
            Text(
              text = stringResource(R.string.summary_search_hint),
              color = MutedGrayText,
            )
          },
          leadingIcon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_search),
              contentDescription = null,
              tint = SoftLavender,
              modifier = Modifier.size(20.dp),
            )
          },
          singleLine = true,
          shape = RoundedCornerShape(18.dp),
          colors =
            OutlinedTextFieldDefaults.colors(
              focusedBorderColor = ElectricViolet,
              unfocusedBorderColor = GlassWhiteBorder,
              focusedContainerColor = GlassCardFillStrong,
              unfocusedContainerColor = GlassCardFillStrong,
              focusedTextColor = CrispWhite,
              unfocusedTextColor = CrispWhite,
            ),
          modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          if (sessions.isNotEmpty() && searchQuery.isBlank()) {
            item {
              Text(
                text = "الجلسات المسجلة (${sessions.size})",
                style = MaterialTheme.typography.titleMedium,
                color = SoftLavender,
              )
            }
            items(items = sessions, key = { "session_${it.id}" }) { session ->
              SessionCard(
                session = session,
                onDelete = { viewModel.deleteSession(session.id) },
              )
            }
          }

          if (historyItems.isNotEmpty()) {
            item {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "النصوص المترجمة (${historyItems.size})",
                style = MaterialTheme.typography.titleMedium,
                color = SoftLavender,
              )
            }

            items(items = historyItems, key = { "hist_${it.id}" }) { item ->
              val dismissState =
                rememberSwipeToDismissBoxState(
                  confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.EndToStart ||
                      value == SwipeToDismissBoxValue.StartToEnd
                    ) {
                      viewModel.deleteHistoryItem(item.id)
                      true
                    } else {
                      false
                    }
                  }
                )
              SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                  Box(
                    modifier =
                      Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(RoseError.copy(alpha = 0.18f))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                  ) {
                    Icon(
                      painter = painterResource(id = R.drawable.ic_trash),
                      contentDescription = "حذف",
                      tint = RoseError,
                    )
                  }
                },
              ) {
                TranslationHistoryCard(
                  item = item,
                  fontFamily = ArabicFontRegistry.fontFamilyFor(settings.overlayFont),
                  onClick = onNavigateToHome,
                )
              }
            }
          } else if (searchQuery.isNotBlank()) {
            item {
              GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                  text = "لا توجد نتائج مطابقة لـ «$searchQuery»",
                  color = LightGrayText,
                  modifier = Modifier.padding(20.dp),
                )
              }
            }
          }
        }
      }
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      containerColor = VoidSurface,
      title = {
        Text(
          text = stringResource(R.string.summary_clear_title),
          color = CrispWhite,
          fontWeight = FontWeight.Bold,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.summary_clear_message),
          color = LightGrayText,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.clearAll()
            showClearDialog = false
          }
        ) {
          Text(stringResource(R.string.summary_clear_confirm), color = RoseError)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text(stringResource(R.string.summary_cancel), color = SoftLavender)
        }
      },
    )
  }
}

@Composable
private fun SessionCard(
  session: TranslationSessionEntity,
  onDelete: () -> Unit,
) {
  val dateStr =
    remember(session.startTimestamp) {
      SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.US).format(Date(session.startTimestamp))
    }
  val langLabel = SupportedLanguages.findByCode(session.sourceLang).displayName
  GlassSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = session.audioMode,
            color = CrispWhite,
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = langLabel,
            color = SoftLavender,
            fontSize = 11.sp,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(ElectricViolet.copy(alpha = 0.22f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
        Text(
          text = "$dateStr • ${formatDurationArabic(session.durationSeconds)} • ${session.translatedCount} جملة",
          color = MutedGrayText,
          style = MaterialTheme.typography.bodyMedium,
        )
      }

      IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
        Icon(
          painter = painterResource(id = R.drawable.ic_trash),
          contentDescription = "حذف الجلسة",
          tint = MutedGrayText,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun TranslationHistoryCard(
  item: TranslationHistoryEntity,
  fontFamily: androidx.compose.ui.text.font.FontFamily,
  onClick: () -> Unit,
) {
  val timeStr =
    remember(item.timestamp) {
      SimpleDateFormat("HH:mm • dd MMM", Locale.US).format(Date(item.timestamp))
    }

  GlassSurfaceCard(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    cornerRadius = 20.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${item.sourceLang.uppercase()} ← AR",
          color = SoftLavender,
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
              .background(VoidSurfaceVariant)
              .border(1.dp, VioletOutline, RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(text = timeStr, color = MutedGrayText, style = MaterialTheme.typography.labelSmall)
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = item.translatedText,
        fontFamily = fontFamily,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = CrispWhite,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
      )

      if (item.sourceText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = item.sourceText,
          color = LightGrayText.copy(alpha = 0.78f),
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }
  }
}
