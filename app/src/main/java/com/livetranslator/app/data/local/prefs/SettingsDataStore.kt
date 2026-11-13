package com.livetranslator.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.livetranslator.app.data.model.ArabicFontOption
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "live_translator_settings")

class SettingsDataStore(private val context: Context) {

  private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val SOURCE_LANGUAGE_CODE = stringPreferencesKey("source_language_code")
    val SKIP_ARABIC = booleanPreferencesKey("skip_arabic")
    val AUDIO_CHUNK_SECONDS = intPreferencesKey("audio_chunk_seconds")
    val AUTO_RESTART_ON_BOOT = booleanPreferencesKey("auto_restart_on_boot")
    val OVERLAY_POSITION = stringPreferencesKey("overlay_position")
    val OVERLAY_BACKGROUND = stringPreferencesKey("overlay_background")
    val OVERLAY_FONT_SIZE_SP = floatPreferencesKey("overlay_font_size_sp")
    val OVERLAY_TEXT_COLOR_ARGB = longPreferencesKey("overlay_text_color_argb")
    val OVERLAY_FONT_KEY = stringPreferencesKey("overlay_font_key")
    val OVERLAY_CORNER_RADIUS_DP = floatPreferencesKey("overlay_corner_radius_dp")
  }

  val settingsFlow: Flow<UserSettings> =
    context.settingsDataStore.data.map { prefs ->
      UserSettings(
        onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
        sourceLanguageCode = prefs[Keys.SOURCE_LANGUAGE_CODE] ?: "en",
        skipArabic = prefs[Keys.SKIP_ARABIC] ?: true,
        audioChunkSeconds = (prefs[Keys.AUDIO_CHUNK_SECONDS] ?: 4).coerceIn(2, 15),
        autoRestartOnBoot = prefs[Keys.AUTO_RESTART_ON_BOOT] ?: false,
        overlayPosition =
          runCatching {
              OverlayPosition.valueOf(prefs[Keys.OVERLAY_POSITION] ?: OverlayPosition.BOTTOM.name)
            }
            .getOrDefault(OverlayPosition.BOTTOM),
        overlayBackground =
          runCatching {
              OverlayBackground.valueOf(
                prefs[Keys.OVERLAY_BACKGROUND] ?: OverlayBackground.GLASS.name
              )
            }
            .getOrDefault(OverlayBackground.GLASS),
        overlayFontSizeSp = (prefs[Keys.OVERLAY_FONT_SIZE_SP] ?: 20f).coerceIn(14f, 60f),
        overlayTextColorArgb = prefs[Keys.OVERLAY_TEXT_COLOR_ARGB] ?: 0xFFF3E8FF,
        overlayFont = ArabicFontOption.fromKey(prefs[Keys.OVERLAY_FONT_KEY]),
        overlayCornerRadiusDp = (prefs[Keys.OVERLAY_CORNER_RADIUS_DP] ?: 18f).coerceIn(0f, 32f),
      )
    }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  suspend fun setSourceLanguageCode(code: String) {
    context.settingsDataStore.edit { it[Keys.SOURCE_LANGUAGE_CODE] = code }
  }

  suspend fun setSkipArabic(skip: Boolean) {
    context.settingsDataStore.edit { it[Keys.SKIP_ARABIC] = skip }
  }

  suspend fun setAudioChunkSeconds(seconds: Int) {
    context.settingsDataStore.edit { it[Keys.AUDIO_CHUNK_SECONDS] = seconds.coerceIn(2, 15) }
  }

  suspend fun setAutoRestartOnBoot(enabled: Boolean) {
    context.settingsDataStore.edit { it[Keys.AUTO_RESTART_ON_BOOT] = enabled }
  }

  suspend fun setOverlayPosition(position: OverlayPosition) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_POSITION] = position.name }
  }

  suspend fun setOverlayBackground(background: OverlayBackground) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_BACKGROUND] = background.name }
  }

  suspend fun setOverlayFontSizeSp(sizeSp: Float) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_FONT_SIZE_SP] = sizeSp.coerceIn(14f, 60f) }
  }

  suspend fun setOverlayTextColorArgb(argb: Long) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_TEXT_COLOR_ARGB] = argb }
  }

  suspend fun setOverlayFont(font: ArabicFontOption) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_FONT_KEY] = font.key }
  }

  suspend fun setOverlayCornerRadiusDp(radiusDp: Float) {
    context.settingsDataStore.edit {
      it[Keys.OVERLAY_CORNER_RADIUS_DP] = radiusDp.coerceIn(0f, 32f)
    }
  }
}
