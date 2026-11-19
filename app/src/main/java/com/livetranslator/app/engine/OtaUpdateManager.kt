package com.livetranslator.app.engine

import android.content.Context
import android.net.Uri
import com.livetranslator.app.data.remote.HotfixProfileDoc
import com.livetranslator.app.data.repository.TranslationRepository
import com.livetranslator.app.service.NotificationHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class OtaManifest(
  val patchVersion: Int,
  val patchNotes: String,
  val latestAppVersionName: String,
  val apkDownloadUrl: String,
  val notificationId: String,
  val notificationTitle: String,
  val notificationBody: String,
  val glossaryAdditions: Map<String, String>,
  val noiseFilterAdditions: List<String>,
)

data class AppAlertEntry(
  val id: String,
  val title: String,
  val body: String,
  val timestampMs: Long,
  val isUpdateAlert: Boolean = false,
) {
  fun formattedTime(): String =
    SimpleDateFormat("HH:mm • dd/MM", Locale.US).format(Date(timestampMs))
}

data class OtaCenterState(
  val currentPatchVersion: Int = 1,
  val availableManifest: OtaManifest? = null,
  val hasPendingPatchUpdate: Boolean = false,
  val isMandatoryHotfix: Boolean = false,
  val hasNewBinaryRelease: Boolean = false,
  val controllerApkUrl: String = "",
  val isChecking: Boolean = false,
  val isApplyingPatch: Boolean = false,
  val lastStatusMessage: String = "التعديلات والإصلاحات محدّثة (الحزمة v1)",
  val glossaryCount: Int = 0,
  val noiseRuleCount: Int = 0,
  val alerts: List<AppAlertEntry> = emptyList(),
)

/**
 * Manages lightweight incremental OTA delta patches (JSON glossary, noise filters, rules,
 * release metadata) and in-app/remote notifications without re-downloading the full APK
 * or loading executable binaries (.dex/.so).
 */
class OtaUpdateManager(
  private val context: Context,
  private val repository: TranslationRepository,
  private val mlKitEngine: MlKitTranslationEngine,
) {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(25, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  private val patchFile: File
    get() = File(context.filesDir, "ota_delta_patch.json")

  private val alertsFile: File
    get() = File(context.filesDir, "ota_alerts_log.json")

  private val _state = MutableStateFlow(OtaCenterState())
  val state: StateFlow<OtaCenterState> = _state.asStateFlow()

  private val seenAlertIds = mutableSetOf<String>()

  suspend fun initializeFromDisk() =
    withContext(Dispatchers.IO) {
      val settings = repository.getCurrentSettings()
      var patchVer = settings.appliedPatchVersion
      var glossary = emptyMap<String, String>()
      var noiseRules = emptyList<String>()

      if (patchFile.exists()) {
        runCatching { parseManifestJson(patchFile.readText()) }
          .getOrNull()
          ?.let { manifest ->
            patchVer = maxOf(patchVer, manifest.patchVersion)
            glossary = manifest.glossaryAdditions
            noiseRules = manifest.noiseFilterAdditions
          }
      }

      mlKitEngine.updateDynamicRules(glossary, noiseRules)
      val savedAlerts = loadSavedAlerts()
      savedAlerts.forEach { seenAlertIds.add(it.id) }

      _state.update {
        it.copy(
          currentPatchVersion = patchVer,
          glossaryCount = glossary.size,
          noiseRuleCount = noiseRules.size,
          alerts = savedAlerts,
          lastStatusMessage = "الحزمة المطبقة: v$patchVer (${glossary.size} مصطلح • ${noiseRules.size} قاعدة تصفية)",
        )
      }
    }

  suspend fun checkFeedForUpdates(feedUrl: String, notifyUser: Boolean = true): Result<OtaManifest?> =
    withContext(Dispatchers.IO) {
      val cleanUrl = feedUrl.trim()
      if (cleanUrl.isBlank()) {
        _state.update {
          it.copy(
            isChecking = false,
            lastStatusMessage = "أضف رابط التحديثات (JSON) أو استورد ملف التعديل من جهازك.",
          )
        }
        return@withContext Result.success(null)
      }

      _state.update { it.copy(isChecking = true, lastStatusMessage = "جاري فحص التحديثات الجزئية...") }

      runCatching {
          val request = Request.Builder().url(cleanUrl).get().build()
          client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
              throw IllegalStateException("تعذر الاتصال بمركز التحديثات (HTTP ${response.code})")
            }
            val rawJson = response.body?.string().orEmpty()
            parseManifestJson(rawJson)
              ?: throw IllegalArgumentException("صيغة ملف التحديث JSON غير صالحة")
          }
        }
        .onSuccess { manifest ->
          handleDiscoveredManifest(manifest, notifyUser)
        }
        .onFailure { err ->
          _state.update {
            it.copy(
              isChecking = false,
              lastStatusMessage = err.message ?: "تعذر فحص التحديثات حالياً",
            )
          }
        }
    }

  suspend fun importLocalDeltaPatch(uri: Uri): Result<OtaManifest> =
    withContext(Dispatchers.IO) {
      _state.update { it.copy(isApplyingPatch = true, lastStatusMessage = "جاري قراءة ملف التعديل...") }
      runCatching {
          val rawText =
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
              ?: throw IllegalStateException("تعذر فتح ملف التعديل المختار")
          val manifest =
            parseManifestJson(rawText)
              ?: throw IllegalArgumentException("ملف التعديل ليس بصيغة JSON صالحة")
          applyManifestNow(manifest)
          manifest
        }
        .onFailure { err ->
          _state.update {
            it.copy(
              isApplyingPatch = false,
              lastStatusMessage = err.message ?: "فشل تطبيق ملف التعديل",
            )
          }
        }
    }

  suspend fun applyAvailableManifest(
    onPatchApplied: (suspend (Int) -> Unit)? = null
  ): Result<Int> =
    withContext(Dispatchers.IO) {
      val manifest =
        _state.value.availableManifest ?: return@withContext Result.success(_state.value.currentPatchVersion)
      _state.update { it.copy(isApplyingPatch = true, lastStatusMessage = "جاري تطبيق التحديث الساخن...") }
      runCatching {
          applyManifestNow(manifest)
          onPatchApplied?.invoke(manifest.patchVersion)
          manifest.patchVersion
        }
        .onFailure { err ->
          _state.update {
            it.copy(
              isApplyingPatch = false,
              lastStatusMessage = err.message ?: "تعذر تطبيق التحديث",
            )
          }
        }
    }

  fun stageCloudHotfix(profile: HotfixProfileDoc, notifyUser: Boolean = true) {
    val manifest =
      OtaManifest(
        patchVersion = profile.patchVersion.coerceAtLeast(1),
        patchNotes = profile.patchNotes.trim(),
        latestAppVersionName = profile.latestAppVersionName.trim(),
        apkDownloadUrl = profile.apkDownloadUrl.trim(),
        notificationId = "",
        notificationTitle = "",
        notificationBody = "",
        glossaryAdditions =
          profile.glossaryAdditions
            .mapKeys { it.key.trim().lowercase() }
            .mapValues { it.value.trim() }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() },
        noiseFilterAdditions =
          profile.noiseFilterAdditions.map { it.trim().lowercase() }.filter { it.isNotBlank() },
      )
    handleDiscoveredManifest(
      manifest = manifest,
      notifyUser = notifyUser,
      isMandatory = profile.mandatoryHotfix,
      controllerApkUrl = profile.controllerApkUrl.trim(),
    )
  }

  fun recordRemoteNotification(
    id: String,
    title: String,
    body: String,
    isUpdateAlert: Boolean,
    postSystemNotification: Boolean = true,
  ) {
    val cleanId = id.trim().ifBlank { "remote_${title.hashCode()}_${body.hashCode()}" }
    if (!seenAlertIds.add(cleanId)) return
    val cleanTitle = title.trim().ifBlank { "إشعار من الإدارة" }
    val cleanBody = body.trim()
    if (cleanBody.isBlank()) return

    if (postSystemNotification) {
      NotificationHelper.showAlertNotification(
        context = context,
        title = cleanTitle,
        body = cleanBody,
        notificationId = (cleanId.hashCode() and 0x0FFF) + 4000,
        openUpdatesScreen = isUpdateAlert,
      )
    }
    appendAlert(
      AppAlertEntry(
        id = cleanId,
        title = cleanTitle,
        body = cleanBody,
        timestampMs = System.currentTimeMillis(),
        isUpdateAlert = isUpdateAlert,
      )
    )
  }

  private suspend fun applyManifestNow(manifest: OtaManifest) {
    patchFile.writeText(serializeManifestJson(manifest))
    repository.setAppliedPatchVersion(manifest.patchVersion)
    mlKitEngine.updateDynamicRules(manifest.glossaryAdditions, manifest.noiseFilterAdditions)

    val appliedAlert =
      AppAlertEntry(
        id = "applied_${manifest.patchVersion}_${System.currentTimeMillis()}",
        title = "تم تحديث التعديلات بنجاح (v${manifest.patchVersion})",
        body =
          manifest.patchNotes.ifBlank {
            "تم تطبيق ${manifest.glossaryAdditions.size} مصطلح و ${manifest.noiseFilterAdditions.size} قاعدة تصفية مباشرة دون إعادة تنزيل التطبيق."
          },
        timestampMs = System.currentTimeMillis(),
        isUpdateAlert = true,
      )
    appendAlert(appliedAlert)

    _state.update {
      it.copy(
        currentPatchVersion = manifest.patchVersion,
        availableManifest = manifest,
        hasPendingPatchUpdate = false,
        isMandatoryHotfix = false,
        isApplyingPatch = false,
        glossaryCount = manifest.glossaryAdditions.size,
        noiseRuleCount = manifest.noiseFilterAdditions.size,
        lastStatusMessage = "تم التحديث للنسخة الجزئية v${manifest.patchVersion} بنجاح!",
      )
    }
  }

  private fun handleDiscoveredManifest(
    manifest: OtaManifest,
    notifyUser: Boolean,
    isMandatory: Boolean = false,
    controllerApkUrl: String = _state.value.controllerApkUrl,
  ) {
    val currentPatch = _state.value.currentPatchVersion
    val hasPatch = manifest.patchVersion > currentPatch
    val hasApk =
      manifest.latestAppVersionName.isNotBlank() &&
        manifest.latestAppVersionName != APP_VERSION_NAME &&
        manifest.apkDownloadUrl.isNotBlank()

    if (hasPatch && notifyUser) {
      val patchAlertId = "patch_avail_${manifest.patchVersion}"
      val isFirstSighting = seenAlertIds.add(patchAlertId)
      val title = "تحديث تصحيح ساخن متاح (v${manifest.patchVersion})"
      val body =
        manifest.patchNotes.ifBlank {
          "تتوفر تحسينات جديدة للدقة وتصفية الكلام — يمكنك التحديث فوراً من داخل التطبيق بدون تنزيل APK."
        }
      if (isFirstSighting) {
        NotificationHelper.showAlertNotification(
          context = context,
          title = title,
          body = body,
          notificationId = NotificationHelper.UPDATE_ALERT_NOTIFICATION_ID,
          openUpdatesScreen = true,
        )
        appendAlert(
          AppAlertEntry(
            id = patchAlertId,
            title = title,
            body = body,
            timestampMs = System.currentTimeMillis(),
            isUpdateAlert = true,
          )
        )
      }
    }

    if (manifest.notificationTitle.isNotBlank() && manifest.notificationBody.isNotBlank()) {
      val alertId = manifest.notificationId.ifBlank { "remote_${manifest.notificationTitle.hashCode()}" }
      if (!seenAlertIds.contains(alertId)) {
        seenAlertIds.add(alertId)
        if (notifyUser) {
          NotificationHelper.showAlertNotification(
            context = context,
            title = manifest.notificationTitle,
            body = manifest.notificationBody,
            openUpdatesScreen = true,
          )
        }
        appendAlert(
          AppAlertEntry(
            id = alertId,
            title = manifest.notificationTitle,
            body = manifest.notificationBody,
            timestampMs = System.currentTimeMillis(),
            isUpdateAlert = false,
          )
        )
      }
    }

    _state.update {
      it.copy(
        availableManifest = manifest,
        hasPendingPatchUpdate = hasPatch,
        isMandatoryHotfix = if (hasPatch) isMandatory else false,
        hasNewBinaryRelease = hasApk,
        controllerApkUrl = controllerApkUrl.ifBlank { it.controllerApkUrl },
        isChecking = false,
        lastStatusMessage =
          when {
            hasPatch -> "يوجد تحديث ساخن جديد (v${manifest.patchVersion}) جاهز للتطبيق الفوري!"
            hasApk -> "تتوفر نسخة APK أحدث (${manifest.latestAppVersionName})"
            else -> "أنت على أحدث حزمة تعديلات (v$currentPatch)"
          },
      )
    }
  }

  suspend fun sendLocalNotification(title: String, body: String, isUpdateAlert: Boolean): Boolean =
    withContext(Dispatchers.IO) {
      val cleanTitle = title.trim().ifBlank { "ترجمة فورية" }
      val cleanBody = body.trim().ifBlank { "إشعار جديد من التطبيق" }
      val posted =
        NotificationHelper.showAlertNotification(
          context = context,
          title = cleanTitle,
          body = cleanBody,
          openUpdatesScreen = isUpdateAlert,
        )
      appendAlert(
        AppAlertEntry(
          id = "local_${System.nanoTime()}",
          title = cleanTitle,
          body = cleanBody,
          timestampMs = System.currentTimeMillis(),
          isUpdateAlert = isUpdateAlert,
        )
      )
      posted
    }

  private fun appendAlert(entry: AppAlertEntry) {
    seenAlertIds.add(entry.id)
    val updated = (listOf(entry) + _state.value.alerts.filterNot { it.id == entry.id }).take(25)
    _state.update { it.copy(alerts = updated) }
    saveAlerts(updated)
  }

  private fun loadSavedAlerts(): List<AppAlertEntry> {
    if (!alertsFile.exists()) return emptyList()
    return runCatching {
        val arr = JSONArray(alertsFile.readText())
        buildList {
          for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            add(
              AppAlertEntry(
                id = obj.optString("id"),
                title = obj.optString("title"),
                body = obj.optString("body"),
                timestampMs = obj.optLong("ts"),
                isUpdateAlert = obj.optBoolean("update", false),
              )
            )
          }
        }
      }
      .getOrDefault(emptyList())
  }

  private fun saveAlerts(alerts: List<AppAlertEntry>) {
    runCatching {
      val arr = JSONArray()
      alerts.forEach { item ->
        arr.put(
          JSONObject().apply {
            put("id", item.id)
            put("title", item.title)
            put("body", item.body)
            put("ts", item.timestampMs)
            put("update", item.isUpdateAlert)
          }
        )
      }
      alertsFile.writeText(arr.toString())
    }
  }

  companion object {
    const val APP_VERSION_NAME = "1.0.0"

    fun sampleManifestJsonTemplate(): String =
      """
      {
        "patchVersion": 2,
        "patchNotes": "تحسين ترجمة الألعاب وتصفية الضوضاء والكلام العربي في البثوث",
        "latestAppVersionName": "1.0.0",
        "apkDownloadUrl": "",
        "notificationId": "alert_v2",
        "notificationTitle": "تحديث جديد متاح لـ ترجمة فورية",
        "notificationBody": "اضغط لتطبيق تحسينات الترجمة الفورية الآن بدون إعادة تنزيل التطبيق.",
        "glossary": {
          "gg": "لعب رائع",
          "nerf": "إضعاف",
          "buff": "تقوية",
          "clutch": "لقطة حاسمة"
        },
        "noiseFilters": [
          "eh wallah",
          "ya shabab"
        ]
      }
      """.trimIndent()

    internal fun parseManifestJson(rawJson: String): OtaManifest? {
      if (rawJson.isBlank()) return null
      return runCatching {
        val obj = JSONObject(rawJson)
        val glossaryObj = obj.optJSONObject("glossary")
        val glossaryMap = buildMap {
          if (glossaryObj != null) {
            val keys = glossaryObj.keys()
            while (keys.hasNext()) {
              val k = keys.next().trim().lowercase()
              val v = glossaryObj.optString(k, "").trim()
              if (k.isNotBlank() && v.isNotBlank()) put(k, v)
            }
          }
        }
        val filtersArr = obj.optJSONArray("noiseFilters")
        val filters = buildList {
          if (filtersArr != null) {
            for (i in 0 until filtersArr.length()) {
              val entry = filtersArr.optString(i, "").trim().lowercase()
              if (entry.isNotBlank()) add(entry)
            }
          }
        }
        OtaManifest(
          patchVersion = obj.optInt("patchVersion", 1).coerceAtLeast(1),
          patchNotes = obj.optString("patchNotes", "").trim(),
          latestAppVersionName = obj.optString("latestAppVersionName", "").trim(),
          apkDownloadUrl = obj.optString("apkDownloadUrl", "").trim(),
          notificationId = obj.optString("notificationId", "").trim(),
          notificationTitle = obj.optString("notificationTitle", "").trim(),
          notificationBody = obj.optString("notificationBody", "").trim(),
          glossaryAdditions = glossaryMap,
          noiseFilterAdditions = filters,
        )
      }.getOrNull()
    }

    private fun serializeManifestJson(manifest: OtaManifest): String {
      return JSONObject()
        .apply {
          put("patchVersion", manifest.patchVersion)
          put("patchNotes", manifest.patchNotes)
          put("latestAppVersionName", manifest.latestAppVersionName)
          put("apkDownloadUrl", manifest.apkDownloadUrl)
          put("notificationId", manifest.notificationId)
          put("notificationTitle", manifest.notificationTitle)
          put("notificationBody", manifest.notificationBody)
          put("glossary", JSONObject(manifest.glossaryAdditions))
          put("noiseFilters", JSONArray(manifest.noiseFilterAdditions))
        }
        .toString(2)
    }
  }
}
