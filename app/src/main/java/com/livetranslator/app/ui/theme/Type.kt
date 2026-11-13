package com.livetranslator.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.livetranslator.app.R
import com.livetranslator.app.data.model.ArabicFontOption

object ArabicFontRegistry {
  val CairoFontFamily: FontFamily =
    FontFamily(
      Font(R.font.cairo_regular, FontWeight.Normal),
      Font(R.font.cairo_regular, FontWeight.Medium),
      Font(R.font.cairo_bold, FontWeight.Bold),
    )

  private val TajawalFontFamily: FontFamily =
    runCatching { FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal)) }
      .getOrElse { CairoFontFamily }

  private val AlmaraiFontFamily: FontFamily =
    runCatching { FontFamily(Font(R.font.almarai_regular, FontWeight.Normal)) }
      .getOrElse { CairoFontFamily }

  private val AmiriFontFamily: FontFamily =
    runCatching { FontFamily(Font(R.font.amiri_regular, FontWeight.Normal)) }
      .getOrElse { CairoFontFamily }

  private val ReemKufiFontFamily: FontFamily =
    runCatching { FontFamily(Font(R.font.reem_kufi_regular, FontWeight.Normal)) }
      .getOrElse { CairoFontFamily }

  private val NotoKufiArabicFontFamily: FontFamily =
    runCatching { FontFamily(Font(R.font.noto_kufi_arabic_regular, FontWeight.Normal)) }
      .getOrElse { CairoFontFamily }

  fun fontFamilyFor(option: ArabicFontOption): FontFamily =
    when (option) {
      ArabicFontOption.CAIRO -> CairoFontFamily
      ArabicFontOption.TAJAWAL -> TajawalFontFamily
      ArabicFontOption.ALMARAI -> AlmaraiFontFamily
      ArabicFontOption.AMIRI -> AmiriFontFamily
      ArabicFontOption.REEM_KUFI -> ReemKufiFontFamily
      ArabicFontOption.NOTO_KUFI_ARABIC -> NotoKufiArabicFontFamily
    }
}

val LiveTranslatorTypography =
  Typography(
    displaySmall =
      TextStyle(
        fontFamily = ArabicFontRegistry.CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
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
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
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
