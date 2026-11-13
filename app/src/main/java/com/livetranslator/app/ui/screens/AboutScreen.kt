package com.livetranslator.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.livetranslator.app.R
import com.livetranslator.app.ui.theme.BrightAmethyst
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.GlassSurfaceCard
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.homeRadialBackground

@Composable
fun AboutScreen(onBack: () -> Unit) {
  val context = LocalContext.current

  Box(
    modifier =
      Modifier.fillMaxSize().homeRadialBackground().safeDrawingPadding().testTag("about_screen"),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(48.dp).testTag("about_back_button"),
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = "رجوع",
            tint = LavenderWhite,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "حول التطبيق",
          style = MaterialTheme.typography.headlineMedium,
          color = LavenderWhite,
        )
      }

      Spacer(modifier = Modifier.height(24.dp))

      Box(
        modifier =
          Modifier.size(92.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(ElectricViolet, BrightAmethyst)))
            .border(1.5.dp, SoftLavender, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_translate),
          contentDescription = stringResource(R.string.app_name),
          tint = LavenderWhite,
          modifier = Modifier.size(44.dp),
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineMedium,
        color = LavenderWhite,
      )
      Text(
        text = "الإصدار 1.0.0 • مجاني 100% بدون إنترنت",
        style = MaterialTheme.typography.labelLarge,
        color = SoftLavender,
      )

      Spacer(modifier = Modifier.height(20.dp))

      GlassSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = "المترجم الفوري للبثوث والصوتيات (Offline)",
            style = MaterialTheme.typography.titleMedium,
            color = LavenderWhite,
          )
          Text(
            text =
              "يلتقط صوت البثوث والتطبيقات داخلياً بجودة 16kHz PCM ويعالجه بالكامل على الجهاز عبر محرك Vosk للتعرف الصوتي ومحرك Google ML Kit للترجمة إلى العربية، مجاناً وبلا حدود ودون الحاجة لأي مفتاح API.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedViolet,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Button(
        onClick = {
          val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/livetranslator/LiveTranslator"))
          runCatching { context.startActivity(intent) }
        },
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("github_link_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_share),
          contentDescription = null,
          tint = LavenderWhite,
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("عرض المشروع على GitHub", color = LavenderWhite)
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
