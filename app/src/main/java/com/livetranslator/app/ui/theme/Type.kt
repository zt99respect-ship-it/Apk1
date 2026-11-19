package com.livetranslator.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.livetranslator.app.R
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayFontWeight

object ArabicFontRegistry {
  val CairoFontFamily: FontFamily =
    FontFamily(
      Font(R.font.cairo, FontWeight.Normal),
      Font(R.font.cairo, FontWeight.Medium),
      Font(R.font.cairo_bold, FontWeight.Bold),
    )

  val TajawalFontFamily: FontFamily =
    FontFamily(
      Font(R.font.tajawal, FontWeight.Normal),
      Font(R.font.tajawal, FontWeight.Medium),
      Font(R.font.tajawal, FontWeight.Bold),
    )

  val IbmPlexSansArabicFontFamily: FontFamily =
    FontFamily(
      Font(R.font.ibm_plex_sans_arabic, FontWeight.Normal),
      Font(R.font.ibm_plex_sans_arabic, FontWeight.Medium),
      Font(R.font.ibm_plex_sans_arabic, FontWeight.Bold),
    )

  val NotoNaskhArabicFontFamily: FontFamily =
    FontFamily(
      Font(R.font.noto_naskh_arabic, FontWeight.Normal),
      Font(R.font.noto_naskh_arabic, FontWeight.Medium),
      Font(R.font.noto_naskh_arabic, FontWeight.Bold),
    )

  fun fontFamilyFor(option: ArabicFontOption): FontFamily =
    when (option) {
      ArabicFontOption.CAIRO -> CairoFontFamily
      ArabicFontOption.IBM_PLEX_SANS_ARABIC -> IbmPlexSansArabicFontFamily
      ArabicFontOption.NOTO_NASKH_ARABIC -> NotoNaskhArabicFontFamily
      ArabicFontOption.TAJAWAL -> TajawalFontFamily
    }

  fun composeWeightFor(weight: OverlayFontWeight): FontWeight =
    when (weight) {
      OverlayFontWeight.NORMAL -> FontWeight.Normal
      OverlayFontWeight.BOLD -> FontWeight.Bold
    }
}

val LiveTranslatorTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
      ),
  )
