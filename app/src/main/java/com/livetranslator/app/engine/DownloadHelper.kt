package com.livetranslator.app.engine

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.livetranslator.app.data.model.SourceLanguage
import com.livetranslator.app.data.model.SupportedLanguages
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class OfflineModelsState(
  val activeLanguageCode: String = SupportedLanguages.AUTO_CODE,
  val voskModelReady: Boolean = false,
  val mlKitModelsReady: Boolean = false,
  val isDownloading: Boolean = false,
  val progressPercent: Int = 0,
  val stageMessage: String = "الخدمة الفورية جاهزة (تلقائي / مرن)",
  val errorMessage: String? = null,
) {
  val allReady: Boolean
    get() = voskModelReady && mlKitModelsReady
}

class DownloadHelper(private val context: Context) {

  private val client =
    OkHttpClient.Builder()
      .connectTimeout(25, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()

  private val downloadMutex = Mutex()

  private val _state = MutableStateFlow(OfflineModelsState())
  val state: StateFlow<OfflineModelsState> = _state.asStateFlow()

  private val modelsRootDir: File
    get() = File(context.filesDir, "vosk_models").apply { mkdirs() }

  fun isVoskModelDownloaded(languageCode: String): Boolean {
    val resolvedCode = SupportedLanguages.resolveVoskLanguage(languageCode).code
    return resolveVoskModelDir(resolvedCode) != null
  }

  fun resolveVoskModelDir(languageCode: String): File? {
    val resolvedCode = SupportedLanguages.resolveVoskLanguage(languageCode).code
    val langDir = File(modelsRootDir, resolvedCode)
    val readyMarker = File(langDir, READY_MARKER_FILE)
    if (!langDir.exists() || !readyMarker.exists()) return null
    return findValidVoskRoot(langDir)
  }

  suspend fun refreshModelStatus(sourceLangCode: String): OfflineModelsState =
    withContext(Dispatchers.IO) {
      val lang = SupportedLanguages.resolveVoskLanguage(sourceLangCode)
      val voskReady = isVoskModelDownloaded(lang.code)
      val mlKitReady = isMlKitPairDownloaded(lang.mlKitLanguageCode)
      val next =
        OfflineModelsState(
          activeLanguageCode = sourceLangCode,
          voskModelReady = voskReady,
          mlKitModelsReady = mlKitReady,
          isDownloading = false,
          progressPercent = if (voskReady && mlKitReady) 100 else 0,
          stageMessage =
            when {
              voskReady && mlKitReady ->
                "الخدمة جاهزة (متصلة + محلية احتياطية: ${lang.displayName})"
              !voskReady && !mlKitReady ->
                "المحرك المرن جاهز • يمكنك تنزيل الحزمة المحلية (${lang.displayName}) كخيار إضافي"
              !voskReady -> "حزمة Vosk (${lang.displayName}) غير محمّلة محلياً (اختياري عند توفر النت)"
              else -> "حزمة ML Kit (${lang.displayName} ← العربية) ستُحمّل تلقائياً عند الحاجة"
            },
          errorMessage = null,
        )
      _state.value = next
      next
    }

  suspend fun ensureAllModelsReady(sourceLangCode: String): Result<File> =
    downloadMutex.withLock {
      withContext(Dispatchers.IO) {
        val lang = SupportedLanguages.resolveVoskLanguage(sourceLangCode)
        runCatching {
            val voskReadyInitially = isVoskModelDownloaded(lang.code)
            val mlKitReadyInitially = isMlKitPairDownloaded(lang.mlKitLanguageCode)

            _state.value =
              OfflineModelsState(
                activeLanguageCode = sourceLangCode,
                voskModelReady = voskReadyInitially,
                mlKitModelsReady = mlKitReadyInitially,
                isDownloading = true,
                progressPercent = if (voskReadyInitially) 70 else 2,
                stageMessage =
                  if (voskReadyInitially) "نموذج Vosk جاهز — جاري فحص حزمة الترجمة..."
                  else "جاري تجهيز نموذج الصوت ${lang.displayName} (~${lang.approxSizeMb} MB)...",
                errorMessage = null,
              )

            val modelDir =
              resolveVoskModelDir(lang.code) ?: downloadAndUnzipVoskModel(lang)

            _state.update {
              it.copy(
                voskModelReady = true,
                progressPercent = 80,
                stageMessage = "تم تجهيز نموذج الصوت • جاري تجهيز المترجم العربي...",
              )
            }

            ensureMlKitPairDownloaded(lang.mlKitLanguageCode)

            _state.value =
              OfflineModelsState(
                activeLanguageCode = sourceLangCode,
                voskModelReady = true,
                mlKitModelsReady = true,
                isDownloading = false,
                progressPercent = 100,
                stageMessage = "النماذج جاهزة (${lang.displayName} ← العربية)",
                errorMessage = null,
              )

            modelDir
          }
          .onFailure { err ->
            val currentVosk = isVoskModelDownloaded(lang.code)
            val currentMlKit = isMlKitPairDownloaded(lang.mlKitLanguageCode)
            _state.value =
              OfflineModelsState(
                activeLanguageCode = sourceLangCode,
                voskModelReady = currentVosk,
                mlKitModelsReady = currentMlKit,
                isDownloading = false,
                progressPercent = 0,
                stageMessage = "تعذر تحميل الحزمة الإضافية",
                errorMessage = err.message ?: "تحقق من اتصال الإنترنت ثم أعد المحاولة",
              )
          }
      }
    }

  suspend fun deleteVoskModel(languageCode: String) =
    withContext(Dispatchers.IO) {
      val resolved = SupportedLanguages.resolveVoskLanguage(languageCode).code
      val langDir = File(modelsRootDir, resolved)
      if (langDir.exists()) {
        langDir.deleteRecursively()
      }
      refreshModelStatus(languageCode)
    }

  private fun downloadAndUnzipVoskModel(lang: SourceLanguage): File {
    val langDir = File(modelsRootDir, lang.code)
    if (langDir.exists()) {
      langDir.deleteRecursively()
    }
    langDir.mkdirs()

    val tempZip = File(modelsRootDir, "${lang.code}_download.zip.tmp")
    if (tempZip.exists()) tempZip.delete()

    try {
      val request = Request.Builder().url(lang.voskDownloadUrl).get().build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          throw IOException("فشل تنزيل نموذج الصوت (HTTP ${response.code})")
        }
        val body = response.body ?: throw IOException("استجابة التنزيل فارغة")
        val contentLength = body.contentLength()
        var totalRead = 0L

        body.byteStream().use { input ->
          FileOutputStream(tempZip).use { output ->
            val buffer = ByteArray(16 * 1024)
            var lastReportedPct = -1
            while (true) {
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              totalRead += read
              if (contentLength > 0) {
                val pct = ((totalRead * 65L) / contentLength).toInt().coerceIn(2, 65)
                if (pct != lastReportedPct) {
                  lastReportedPct = pct
                  _state.update {
                    it.copy(
                      progressPercent = pct,
                      stageMessage =
                        "جاري تنزيل نموذج ${lang.displayName}: ${((totalRead * 100L) / contentLength).toInt()}%",
                    )
                  }
                }
              }
            }
          }
        }
      }

      _state.update {
        it.copy(
          progressPercent = 70,
          stageMessage = "جاري فك ضغط نموذج الصوت (${lang.displayName})...",
        )
      }

      unzipSafely(tempZip, langDir)

      val resolvedRoot =
        findValidVoskRoot(langDir)
          ?: throw IOException("الملف المفكوك لا يحتوي على بنية نموذج Vosk صالحة")

      File(langDir, READY_MARKER_FILE).writeText(lang.voskModelName)
      return resolvedRoot
    } finally {
      if (tempZip.exists()) {
        tempZip.delete()
      }
    }
  }

  internal fun unzipSafely(zipFile: File, targetDir: File) {
    val targetCanonical = targetDir.canonicalPath
    ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
      val buffer = ByteArray(16 * 1024)
      while (true) {
        val entry = zis.nextEntry ?: break
        val outFile = File(targetDir, entry.name)
        val outCanonical = outFile.canonicalPath
        if (!outCanonical.startsWith(targetCanonical + File.separator) && outCanonical != targetCanonical) {
          throw SecurityException("مسار ملف مضغوط غير آمن: ${entry.name}")
        }
        if (entry.isDirectory) {
          outFile.mkdirs()
        } else {
          outFile.parentFile?.mkdirs()
          FileOutputStream(outFile).use { fos ->
            while (true) {
              val count = zis.read(buffer)
              if (count == -1) break
              fos.write(buffer, 0, count)
            }
          }
        }
        zis.closeEntry()
      }
    }
  }

  internal fun findValidVoskRoot(dir: File): File? {
    if (!dir.isDirectory) return null
    if (looksLikeVoskModelDir(dir)) return dir
    val children = dir.listFiles()?.filter { it.isDirectory } ?: return null
    for (child in children) {
      if (looksLikeVoskModelDir(child)) return child
    }
    return null
  }

  private fun looksLikeVoskModelDir(dir: File): Boolean {
    val hasAm = File(dir, "am").isDirectory
    val hasConf = File(dir, "conf").isDirectory
    val hasGraph = File(dir, "graph").isDirectory
    return hasAm && hasConf || (hasConf && hasGraph)
  }

  suspend fun isMlKitPairDownloaded(sourceMlKitCode: String): Boolean {
    return runCatching {
        val manager = RemoteModelManager.getInstance()
        val sourceModel = TranslateRemoteModel.Builder(sourceMlKitCode).build()
        val arabicModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
        val srcOk = runCatching { manager.isModelDownloaded(sourceModel).awaitResult() }.getOrDefault(false)
        val arOk = runCatching { manager.isModelDownloaded(arabicModel).awaitResult() }.getOrDefault(false)
        srcOk && arOk
      }
      .getOrDefault(false)
  }

  suspend fun ensureMlKitPairDownloaded(sourceMlKitCode: String) {
    val manager = RemoteModelManager.getInstance()
    val conditions = DownloadConditions.Builder().build()

    val sourceModel = TranslateRemoteModel.Builder(sourceMlKitCode).build()
    if (!manager.isModelDownloaded(sourceModel).awaitResult()) {
      _state.update {
        it.copy(
          progressPercent = 85,
          stageMessage = "جاري تجهيز حزمة اللغة لـ ML Kit...",
        )
      }
      manager.download(sourceModel, conditions).awaitResult()
    }

    val arabicModel = TranslateRemoteModel.Builder(TranslateLanguage.ARABIC).build()
    if (!manager.isModelDownloaded(arabicModel).awaitResult()) {
      _state.update {
        it.copy(
          progressPercent = 93,
          stageMessage = "جاري تجهيز حزمة اللغة العربية لـ ML Kit...",
        )
      }
      manager.download(arabicModel, conditions).awaitResult()
    }
  }

  companion object {
    private const val READY_MARKER_FILE = ".vosk_ready"
  }
}

suspend fun <T> Task<T>.awaitResult(): T =
  suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
      if (continuation.isActive) {
        continuation.resume(result)
      }
    }
    addOnFailureListener { exception ->
      if (continuation.isActive) {
        continuation.resumeWithException(exception)
      }
    }
    addOnCanceledListener {
      continuation.cancel()
    }
  }
