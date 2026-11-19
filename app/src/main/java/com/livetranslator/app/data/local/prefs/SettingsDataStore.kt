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
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.OverlayBackground
import com.livetranslator.app.data.model.OverlayFontWeight
import com.livetranslator.app.data.model.OverlayPosition
import com.livetranslator.app.data.model.OverlayStylePreset
import com.livetranslator.app.data.model.SpeechEnginePreference
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "live_translator_settings")

class SettingsDataStore(private val context: Context) {

  private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val ACCOUNT_LABEL = stringPreferencesKey("account_label")
    val SOURCE_LANGUAGE_CODE = stringPreferencesKey("source_language_code")
    val SKIP_ARABIC = booleanPreferencesKey("skip_arabic")
    val AUDIO_CHUNK_SECONDS = intPreferencesKey("audio_chunk_seconds")
    val AUTO_RESTART_ON_BOOT = booleanPreferencesKey("auto_restart_on_boot")
    val AUDIO_SOURCE_PREFERENCE = stringPreferencesKey("audio_source_preference")
    val SPEECH_ENGINE_PREFERENCE = stringPreferencesKey("speech_engine_preference")
    val OVERLAY_POSITION = stringPreferencesKey("overlay_position")
    val OVERLAY_BACKGROUND = stringPreferencesKey("overlay_background")
    val OVERLAY_PRESET = stringPreferencesKey("overlay_preset")
    val OVERLAY_FONT_KEY = stringPreferencesKey("overlay_font_key")
    val OVERLAY_FONT_WEIGHT = stringPreferencesKey("overlay_font_weight")
    val OVERLAY_FONT_SIZE_SP = floatPreferencesKey("overlay_font_size_sp")
    val OVERLAY_TEXT_COLOR_ARGB = longPreferencesKey("overlay_text_color_argb")
    val OVERLAY_BOX_COLOR_ARGB = longPreferencesKey("overlay_box_color_argb")
    val OVERLAY_BOX_OPACITY = floatPreferencesKey("overlay_box_opacity")
    val OVERLAY_CORNER_RADIUS_DP = floatPreferencesKey("overlay_corner_radius_dp")
    val OVERLAY_CUSTOM_X = intPreferencesKey("overlay_custom_x")
    val OVERLAY_CUSTOM_Y = intPreferencesKey("overlay_custom_y")
  }

  val settingsFlow: Flow<UserSettings> =
    context.settingsDataStore.data.map { prefs ->
      UserSettings(
        onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
        accountLabel =
          prefs[Keys.ACCOUNT_LABEL]?.takeIf { it.isNotBlank() } ?: "مستخدم LiveTranslator",
        sourceLanguageCode = prefs[Keys.SOURCE_LANGUAGE_CODE] ?: SupportedLanguages.AUTO_CODE,
        skipArabic = prefs[Keys.SKIP_ARABIC] ?: true,
        audioChunkSeconds = (prefs[Keys.AUDIO_CHUNK_SECONDS] ?: 4).coerceIn(2, 15),
        autoRestartOnBoot = prefs[Keys.AUTO_RESTART_ON_BOOT] ?: false,
        audioSourcePreference =
          AudioSourcePreference.fromKey(prefs[Keys.AUDIO_SOURCE_PREFERENCE]),
        speechEnginePreference =
          SpeechEnginePreference.fromKey(prefs[Keys.SPEECH_ENGINE_PREFERENCE]),
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
        overlayPreset = OverlayStylePreset.fromKey(prefs[Keys.OVERLAY_PRESET]),
        overlayFont = ArabicFontOption.fromKey(prefs[Keys.OVERLAY_FONT_KEY]),
        overlayFontWeight = OverlayFontWeight.fromKey(prefs[Keys.OVERLAY_FONT_WEIGHT]),
        overlayFontSizeSp = (prefs[Keys.OVERLAY_FONT_SIZE_SP] ?: 24f).coerceIn(14f, 42f),
        overlayTextColorArgb = prefs[Keys.OVERLAY_TEXT_COLOR_ARGB] ?: 0xFFFFFFFF,
        overlayBoxColorArgb = prefs[Keys.OVERLAY_BOX_COLOR_ARGB] ?: 0xFF09090B,
        overlayBoxOpacity = (prefs[Keys.OVERLAY_BOX_OPACITY] ?: 0.86f).coerceIn(0f, 1f),
        overlayCornerRadiusDp = (prefs[Keys.OVERLAY_CORNER_RADIUS_DP] ?: 22f).coerceIn(0f, 32f),
        overlayCustomX = prefs[Keys.OVERLAY_CUSTOM_X] ?: -1,
        overlayCustomY = prefs[Keys.OVERLAY_CUSTOM_Y] ?: -1,
      )
    }

  suspend fun setOnboardingCompleted(completed: Boolean) {
    context.settingsDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
  }

  suspend fun setAccountLabel(label: String) {
    context.settingsDataStore.edit {
      it[Keys.ACCOUNT_LABEL] = label.trim().ifBlank { "مستخدم LiveTranslator" }
    }
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

  suspend fun setAudioSourcePreference(preference: AudioSourcePreference) {
    context.settingsDataStore.edit { it[Keys.AUDIO_SOURCE_PREFERENCE] = preference.key }
  }

  suspend fun setSpeechEnginePreference(preference: SpeechEnginePreference) {
    context.settingsDataStore.edit { it[Keys.SPEECH_ENGINE_PREFERENCE] = preference.key }
  }

  suspend fun setOverlayPosition(position: OverlayPosition) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_POSITION] = position.name }
  }

  suspend fun setOverlayBackground(background: OverlayBackground) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_BACKGROUND] = background.name }
  }

  suspend fun applyStylePreset(preset: OverlayStylePreset) {
    context.settingsDataStore.edit {
      it[Keys.OVERLAY_PRESET] = preset.key
      it[Keys.OVERLAY_FONT_KEY] = preset.font.key
      it[Keys.OVERLAY_FONT_WEIGHT] = preset.weight.key
      it[Keys.OVERLAY_FONT_SIZE_SP] = preset.fontSizeSp
      it[Keys.OVERLAY_TEXT_COLOR_ARGB] = preset.textColorArgb
      it[Keys.OVERLAY_BOX_COLOR_ARGB] = preset.boxColorArgb
      it[Keys.OVERLAY_BOX_OPACITY] = preset.boxOpacity
      it[Keys.OVERLAY_BACKGROUND] = OverlayBackground.GLASS.name
    }
  }

  suspend fun saveFullOverlayStyle(
    preset: OverlayStylePreset,
    font: ArabicFontOption,
    weight: OverlayFontWeight,
    fontSizeSp: Float,
    textColorArgb: Long,
    boxColorArgb: Long,
    boxOpacity: Float,
    position: OverlayPosition,
    cornerRadiusDp: Float,
  ) {
    context.settingsDataStore.edit {
      it[Keys.OVERLAY_PRESET] = preset.key
      it[Keys.OVERLAY_FONT_KEY] = font.key
      it[Keys.OVERLAY_FONT_WEIGHT] = weight.key
      it[Keys.OVERLAY_FONT_SIZE_SP] = fontSizeSp.coerceIn(14f, 42f)
      it[Keys.OVERLAY_TEXT_COLOR_ARGB] = textColorArgb
      it[Keys.OVERLAY_BOX_COLOR_ARGB] = boxColorArgb
      it[Keys.OVERLAY_BOX_OPACITY] = boxOpacity.coerceIn(0f, 1f)
      it[Keys.OVERLAY_POSITION] = position.name
      it[Keys.OVERLAY_CORNER_RADIUS_DP] = cornerRadiusDp.coerceIn(0f, 32f)
      it[Keys.OVERLAY_BACKGROUND] =
        if (boxOpacity <= 0.02f) OverlayBackground.NONE.name else OverlayBackground.GLASS.name
    }
  }

  suspend fun setOverlayFontSizeSp(sizeSp: Float) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_FONT_SIZE_SP] = sizeSp.coerceIn(14f, 42f) }
  }

  suspend fun setOverlayTextColorArgb(argb: Long) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_TEXT_COLOR_ARGB] = argb }
  }

  suspend fun setOverlayBoxColorArgb(argb: Long) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_BOX_COLOR_ARGB] = argb }
  }

  suspend fun setOverlayBoxOpacity(opacity: Float) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_BOX_OPACITY] = opacity.coerceIn(0f, 1f) }
  }

  suspend fun setOverlayFont(font: ArabicFontOption) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_FONT_KEY] = font.key }
  }

  suspend fun setOverlayFontWeight(weight: OverlayFontWeight) {
    context.settingsDataStore.edit { it[Keys.OVERLAY_FONT_WEIGHT] = weight.key }
  }

  suspend fun setOverlayCornerRadiusDp(radiusDp: Float) {
    context.settingsDataStore.edit {
      it[Keys.OVERLAY_CORNER_RADIUS_DP] = radiusDp.coerceIn(0f, 32f)
    }
  }

  suspend fun setOverlayCustomPosition(x: Int, y: Int) {
    context.settingsDataStore.edit {
      it[Keys.OVERLAY_CUSTOM_X] = x.coerceAtLeast(0)
      it[Keys.OVERLAY_CUSTOM_Y] = y.coerceAtLeast(0)
    }
  }

  suspend fun resetOverlayCustomPosition() {
    context.settingsDataStore.edit {
      it[Keys.OVERLAY_CUSTOM_X] = -1
      it[Keys.OVERLAY_CUSTOM_Y] = -1
    }
  }

  suspend fun resetStyleToDefaults() {
    applyStylePreset(OverlayStylePreset.ORIGINAL)
    setOverlayPosition(OverlayPosition.BOTTOM)
    setOverlayCornerRadiusDp(22f)
    resetOverlayCustomPosition()
  }

  suspend fun resetAllSettings() {
    context.settingsDataStore.edit { prefs ->
      val onboardingDone = prefs[Keys.ONBOARDING_COMPLETED] ?: true
      prefs.clear()
      prefs[Keys.ONBOARDING_COMPLETED] = onboardingDone
    }
  }
}
