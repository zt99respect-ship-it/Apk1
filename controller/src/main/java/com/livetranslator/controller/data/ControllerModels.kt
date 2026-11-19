package com.livetranslator.controller.data

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

enum class OperationType {
  CREATE,
  GET,
  LIST,
  UPDATE,
  DELETE,
}

fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
  authUid: String?,
): Nothing {
  if (exception is CancellationException) throw exception
  val code = (exception as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
  Log.e(
    "ControllerFirestore",
    "op=$operationType path=$path uid=$authUid code=$code msg=${exception.message}",
    exception,
  )
  throw exception
}

fun toArabicAdminError(exception: Throwable): String {
  val code = (exception as? FirebaseFirestoreException)?.code
  return when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
      "لا تملك صلاحية المدير المطلوبة لتنفيذ هذا التعديل."
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
      "تعذر الوصول إلى خادم Firebase حالياً."
    else -> exception.localizedMessage ?: "حدث خطأ غير متوقع."
  }
}

@Keep
@IgnoreExtraProperties
data class AdminOperatorDoc(
  @DocumentId val id: String = "",
  val uid: String = "",
  val email: String = "",
  val operatorUsername: String = "RT",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ManagedUserDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val username: String = "",
  val secretCode: String = "",
  val role: String = "user",
  val blocked: Boolean = false,
  val blockedReason: String = "",
  val appliedPatchVersion: Int = 1,
  @ServerTimestamp val lastSeenAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class HotfixProfileDoc(
  @DocumentId val id: String = "",
  val patchVersion: Int = 1,
  val patchNotes: String = "",
  val mandatoryHotfix: Boolean = false,
  val glossaryAdditions: Map<String, String> = emptyMap(),
  val noiseFilterAdditions: List<String> = emptyList(),
  val latestAppVersionName: String = "1.0.0",
  val apkDownloadUrl: String = "",
  val controllerApkUrl: String = "",
  val updatedBy: String = "RT",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class AppControlConfigDoc(
  @DocumentId val id: String = "",
  val maintenanceEnabled: Boolean = false,
  val maintenanceMessage: String = "",
  val allowAudioLive: Boolean = true,
  val allowScreenOcr: Boolean = true,
  val allowOverlayBubble: Boolean = true,
  val forceSkipArabic: Boolean = false,
  val forcedSpeechEngine: String = "",
  val forcedSourceLanguage: String = "",
  val remoteAudioChunkSeconds: Int = 4,
  val remoteScreenScanIntervalMs: Int = 900,
  val showGlobalBanner: Boolean = false,
  val globalBannerTitle: String = "",
  val globalBannerMessage: String = "",
  val githubRepoSlug: String = "",
  val otaFeedUrl: String = "",
  val updatedBy: String = "RT",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class RemoteNotificationDoc(
  @DocumentId val id: String = "",
  val audience: String = "all",
  val title: String = "",
  val body: String = "",
  val category: String = "announcement",
  val updateAlert: Boolean = false,
  val senderLabel: String = "RT",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class GithubReleaseInfo(
  val tagName: String,
  val releaseName: String,
  val originalApkUrl: String,
  val controllerApkUrl: String,
)

object AdminOperatorVerifier {
  const val ADMIN_BOOTSTRAP_EMAIL = "mfr7h.carectar@gmail.com"
  const val OPERATOR_HANDLE = "RT"

  private fun expectedPasswordDigest(): ByteArray {
    val chars = charArrayOf('m', 'k', 'j', 'b', 'm', 'k', 'j', 'b')
    val bytes = String(chars).toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes)
  }

  fun verifyOperatorCredentials(username: String, password: String): Boolean {
    if (username.trim() != OPERATOR_HANDLE) return false
    val inputDigest =
      MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
    return MessageDigest.isEqual(inputDigest, expectedPasswordDigest())
  }
}
