package com.livetranslator.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.livetranslator.app.R
import com.livetranslator.app.data.local.db.TranslationHistoryEntity
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.RoseError
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidBackground
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val textToolbar = LocalTextToolbar.current
  val scope = rememberCoroutineScope()
  val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
  val items by viewModel.historyItems.collectAsStateWithLifecycle()
  var showClearDialog by remember { mutableStateOf(false) }

  val exportLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
      if (uri != null) {
        scope.launch {
          val content = viewModel.buildExportText()
          val saved =
            withContext(Dispatchers.IO) {
              runCatching {
                  context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                  }
                }
                .isSuccess
            }
          Toast.makeText(
              context,
              if (saved) "تم تصدير السجل بنجاح" else "تعذر حفظ الملف",
              Toast.LENGTH_SHORT,
            )
            .show()
        }
      }
    }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      containerColor = VoidSurface,
      title = {
        Text(
          text = stringResource(R.string.history_clear_title),
          color = LavenderWhite,
        )
      },
      text = {
        Text(
          text = stringResource(R.string.history_clear_message),
          color = MutedViolet,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.clearAll()
            showClearDialog = false
          },
          colors = ButtonDefaults.textButtonColors(contentColor = RoseError),
        ) {
          Text(stringResource(R.string.history_clear_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text(stringResource(R.string.history_cancel), color = SoftLavender)
        }
      },
    )
  }

  Box(
    modifier = Modifier.fillMaxSize().background(VoidBackground).testTag("history_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize().widthIn(max = 600.dp).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "سجل الترجمة",
            style = MaterialTheme.typography.headlineMedium,
            color = LavenderWhite,
          )
          Text(
            text = "${items.size} سجل محفوظ • يتم المسح تلقائياً بعد 30 يوماً",
            style = MaterialTheme.typography.labelSmall,
            color = MutedViolet,
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          IconButton(
            onClick = {
              textToolbar.hide()
              focusManager.clearFocus(force = true)
              val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
              exportLauncher.launch("LiveTranslator_$timestamp.txt")
            },
            modifier =
              Modifier.size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(VoidSurfaceVariant)
                .border(1.dp, VioletOutline, RoundedCornerShape(14.dp))
                .testTag("export_txt_button"),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_share),
              contentDescription = "تصدير السجل إلى ملف نصي",
              tint = SoftLavender,
            )
          }

          IconButton(
            onClick = {
              textToolbar.hide()
              focusManager.clearFocus(force = true)
              if (items.isNotEmpty()) showClearDialog = true
            },
            modifier =
              Modifier.size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(VoidSurfaceVariant)
                .border(1.dp, VioletOutline, RoundedCornerShape(14.dp))
                .testTag("clear_history_button"),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_trash),
              contentDescription = "مسح السجل بالكامل",
              tint = if (items.isNotEmpty()) RoseError else MutedViolet.copy(alpha = 0.4f),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = searchQuery,
        onValueChange = { viewModel.updateSearchQuery(it) },
        modifier = Modifier.fillMaxWidth().testTag("history_search_input"),
        placeholder = {
          Text(
            text = stringResource(R.string.history_search_hint),
            color = MutedViolet.copy(alpha = 0.7f),
          )
        },
        leadingIcon = {
          Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = "بحث",
            tint = SoftLavender,
            modifier = Modifier.size(20.dp),
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(
              onClick = {
                textToolbar.hide()
                viewModel.updateSearchQuery("")
              }
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "مسح البحث",
                tint = MutedViolet,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedTextColor = LavenderWhite,
            unfocusedTextColor = LavenderWhite,
            focusedBorderColor = ElectricViolet,
            unfocusedBorderColor = VioletOutline,
            focusedContainerColor = VoidSurface,
            unfocusedContainerColor = VoidSurface,
          ),
      )

      Spacer(modifier = Modifier.height(14.dp))

      if (items.isEmpty()) {
        Box(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_history),
              contentDescription = null,
              tint = MutedViolet.copy(alpha = 0.6f),
              modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = stringResource(R.string.history_empty_title),
              style = MaterialTheme.typography.titleLarge,
              color = LavenderWhite,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stringResource(R.string.history_empty_subtitle),
              style = MaterialTheme.typography.bodyMedium,
              color = MutedViolet,
              textAlign = TextAlign.Center,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(bottom = 16.dp),
        ) {
          items(items = items, key = { it.id }) { entry ->
            val dismissState =
              rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                  if (value == SwipeToDismissBoxValue.EndToStart) {
                    viewModel.deleteItem(entry.id)
                    true
                  } else {
                    false
                  }
                }
              )

            SwipeToDismissBox(
              state = dismissState,
              enableDismissFromStartToEnd = false,
              backgroundContent = {
                Box(
                  modifier =
                    Modifier.fillMaxSize()
                      .clip(RoundedCornerShape(18.dp))
                      .background(RoseError.copy(alpha = 0.25f))
                      .border(1.dp, RoseError.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                      .padding(horizontal = 20.dp),
                  contentAlignment = Alignment.CenterEnd,
                ) {
                  Icon(
                    painter = painterResource(id = R.drawable.ic_trash),
                    contentDescription = "حذف العنصر",
                    tint = RoseError,
                  )
                }
              },
            ) {
              HistoryItemCard(
                entry = entry,
                onDelete = { viewModel.deleteItem(entry.id) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryItemCard(
  entry: TranslationHistoryEntity,
  onDelete: () -> Unit,
) {
  val formattedTime =
    remember(entry.timestamp) {
      SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(entry.timestamp))
    }

  GlassSurfaceCard(
    modifier = Modifier.fillMaxWidth().testTag("history_item_${entry.id}"),
    cornerRadius = 18.dp,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${entry.sourceLang.uppercase()} ← AR",
            style = MaterialTheme.typography.labelSmall,
            color = SoftLavender,
            modifier =
              Modifier.clip(RoundedCornerShape(8.dp))
                .background(ElectricViolet.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = formattedTime,
            style = MaterialTheme.typography.labelSmall,
            color = MutedViolet,
          )
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_trash),
            contentDescription = "حذف السجل",
            tint = MutedViolet,
            modifier = Modifier.size(16.dp),
          )
        }
      }

      if (entry.sourceText.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = entry.sourceText,
          style = MaterialTheme.typography.bodyMedium,
          color = MutedViolet,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = entry.translatedText,
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        color = LavenderWhite,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
