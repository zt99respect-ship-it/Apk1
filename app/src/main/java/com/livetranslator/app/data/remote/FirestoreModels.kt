package com.livetranslator.app.data.remote

import android.util.Log
import androidx.annotation.Keep
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.cancellation.CancellationException

enum class OperationType {
  CREATE,
  GET,
  LIST,
  UPDATE,
  DELETE,
}

data class FirestoreErrorInfo(
  val error: String,
  val operationType: OperationType,
  val path: String?,
  val authUid: String?,
)

fun handleFirestoreError(
  exception: Throwable,
  operationType: OperationType,
  path: String?,
  authUid: String?,
): Nothing {
  if (exception is CancellationException) throw exception
  val code = (exception as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
  val info =
    FirestoreErrorInfo(
      error = "[$code] ${exception.message ?: exception.javaClass.simpleName}",
      operationType = operationType,
      path = path,
      authUid = authUid,
    )
  Log.e("FirestoreError", "Operation=${info.operationType} path=${info.path} uid=${info.authUid} err=${info.error}", exception)
  throw exception
}

fun toArabicCloudError(exception: Throwable): String {
  val code = (exception as? FirebaseFirestoreException)?.code
  return when (code) {
    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
      "لا تملك صلاحية تنفيذ هذا الإجراء في السحابة."
    FirebaseFirestoreException.Code.UNAVAILABLE,
    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
      "تعذر الاتصال بالخادم حالياً، يرجى المحاولة لاحقاً."
    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
      "يرجى تسجيل الدخول بحساب جوجل أولاً."
    else ->
      if (exception is FirebaseException) {
        exception.localizedMessage ?: "حدث خطأ أثناء المزامنة السحابية."
      } else {
        exception.localizedMessage ?: "تعذر إتمام العملية السحابية."
      }
  }
}

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val username: String = "",
  val passwordHash: String = "",
  val passwordSalt: String = "",
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

@Keep
@IgnoreExtraProperties
data class UserInboxDoc(
  @DocumentId val id: String = "",
  val recipientUid: String = "",
  val title: String = "",
  val body: String = "",
  val category: String = "direct",
  val read: Boolean = false,
  val senderLabel: String = "RT",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

sealed interface CloudGateUiState {
  data object LoadingProfile : CloudGateUiState
  data object NeedsCredentialsSetup : CloudGateUiState
  data class Ready(
    val profile: UserProfileDoc,
    val appControl: AppControlConfigDoc,
    val hotfix: HotfixProfileDoc?,
    val inboxMessages: List<UserInboxDoc>,
    val remoteNotifications: List<RemoteNotificationDoc>,
  ) : CloudGateUiState
  data class Error(val message: String) : CloudGateUiState
}

object CryptoPasswordHelper {
  private val secureRandom = SecureRandom()
  private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

  fun generateSaltHex(bytesCount: Int = 12): String {
    val bytes = ByteArray(bytesCount.coerceIn(8, 32))
    secureRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun hashPassword(password: String, saltHex: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = "$saltHex:$password".toByteArray(Charsets.UTF_8)
    return digest.digest(input).joinToString("") { "%02x".format(it) }
  }

  fun verifyPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
    if (saltHex.isBlank() || expectedHashHex.length != 64) return false
    val candidate = hashPassword(password, saltHex)
    return MessageDigest.isEqual(
      candidate.toByteArray(Charsets.UTF_8),
      expectedHashHex.toByteArray(Charsets.UTF_8),
    )
  }

  fun generateUniqueUserCode(): String {
    fun block(len: Int): String =
      buildString(len) {
        repeat(len) {
          append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
        }
      }
    return "TR-${block(4)}-${block(4)}"
  }

  fun maskSecretCode(secretCode: String): String {
    if (secretCode.isBlank()) return "TR-••••-••••"
    return "TR-••••-${secretCode.takeLast(2).padStart(2, '•')}"
  }
}
