package com.livetranslator.controller.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class ControllerRepository(
  private val firestore: FirebaseFirestore,
  private val auth: FirebaseAuth,
) {
  companion object {
    const val COLLECTION_ADMINS = "admins"
    const val COLLECTION_USERS = "users"
    const val SUBCOLLECTION_INBOX = "inbox"
    const val COLLECTION_HOTFIX = "hotfix_profiles"
    const val DOC_HOTFIX_LIVE = "live"
    const val COLLECTION_APP_CONTROL = "app_control"
    const val DOC_APP_CONTROL_GLOBAL = "global"
    const val COLLECTION_REMOTE_NOTIFICATIONS = "remote_notifications"
  }

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .build()

  private fun currentUid(): String? = auth.currentUser?.uid

  suspend fun verifyAndBootstrapAdmin(user: FirebaseUser): Result<Boolean> {
    val path = "$COLLECTION_ADMINS/${user.uid}"
    return try {
      val adminRef = firestore.collection(COLLECTION_ADMINS).document(user.uid)
      val existing = adminRef.get().await()
      if (existing.exists()) {
        return Result.success(true)
      }
      if (user.email.equals(AdminOperatorVerifier.ADMIN_BOOTSTRAP_EMAIL, ignoreCase = true)) {
        val payload =
          hashMapOf<String, Any>(
            "uid" to user.uid,
            "email" to (user.email ?: AdminOperatorVerifier.ADMIN_BOOTSTRAP_EMAIL),
            "operatorUsername" to AdminOperatorVerifier.OPERATOR_HANDLE,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        adminRef.set(payload).await()
        return Result.success(true)
      }
      Result.success(false)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.GET, path, currentUid()) }
      Result.failure(e)
    }
  }

  fun observeAppControl(): Flow<Result<AppControlConfigDoc>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_APP_CONTROL)
        .document(DOC_APP_CONTROL_GLOBAL)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val doc =
            snapshot
              ?.takeIf { it.exists() }
              ?.toObject(AppControlConfigDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              ?: AppControlConfigDoc()
          trySend(Result.success(doc))
        }
    awaitClose { registration.remove() }
  }

  suspend fun saveAppControl(config: AppControlConfigDoc): Result<Unit> {
    val path = "$COLLECTION_APP_CONTROL/$DOC_APP_CONTROL_GLOBAL"
    return try {
      val docRef = firestore.collection(COLLECTION_APP_CONTROL).document(DOC_APP_CONTROL_GLOBAL)
      val exists = docRef.get().await().exists()
      val baseMap =
        hashMapOf<String, Any>(
          "maintenanceEnabled" to config.maintenanceEnabled,
          "maintenanceMessage" to config.maintenanceMessage.trim().take(600),
          "allowAudioLive" to config.allowAudioLive,
          "allowScreenOcr" to config.allowScreenOcr,
          "allowOverlayBubble" to config.allowOverlayBubble,
          "forceSkipArabic" to config.forceSkipArabic,
          "forcedSpeechEngine" to config.forcedSpeechEngine.trim().take(32),
          "forcedSourceLanguage" to config.forcedSourceLanguage.trim().take(16),
          "remoteAudioChunkSeconds" to config.remoteAudioChunkSeconds.coerceIn(2, 15),
          "remoteScreenScanIntervalMs" to config.remoteScreenScanIntervalMs.coerceIn(450, 3000),
          "showGlobalBanner" to config.showGlobalBanner,
          "globalBannerTitle" to config.globalBannerTitle.trim().take(120),
          "globalBannerMessage" to config.globalBannerMessage.trim().take(500),
          "githubRepoSlug" to config.githubRepoSlug.trim().take(120),
          "otaFeedUrl" to config.otaFeedUrl.trim().take(500),
          "updatedBy" to AdminOperatorVerifier.OPERATOR_HANDLE,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!exists) {
        baseMap["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(baseMap).await()
      } else {
        docRef.update(baseMap).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.UPDATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  fun observeHotfixProfile(): Flow<Result<HotfixProfileDoc>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_HOTFIX)
        .document(DOC_HOTFIX_LIVE)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val doc =
            snapshot
              ?.takeIf { it.exists() }
              ?.toObject(HotfixProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              ?: HotfixProfileDoc()
          trySend(Result.success(doc))
        }
    awaitClose { registration.remove() }
  }

  suspend fun publishHotfixProfile(profile: HotfixProfileDoc): Result<Unit> {
    val path = "$COLLECTION_HOTFIX/$DOC_HOTFIX_LIVE"
    return try {
      val docRef = firestore.collection(COLLECTION_HOTFIX).document(DOC_HOTFIX_LIVE)
      val exists = docRef.get().await().exists()

      val cleanedGlossary =
        profile.glossaryAdditions
          .mapKeys { it.key.trim().lowercase() }
          .mapValues { it.value.trim() }
          .filter { it.key.isNotBlank() && it.value.isNotBlank() }

      val cleanedNoise =
        profile.noiseFilterAdditions.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()

      val baseMap =
        hashMapOf<String, Any>(
          "patchVersion" to profile.patchVersion.coerceIn(1, 100000),
          "patchNotes" to profile.patchNotes.trim().take(1000),
          "mandatoryHotfix" to profile.mandatoryHotfix,
          "glossaryAdditions" to cleanedGlossary,
          "noiseFilterAdditions" to cleanedNoise,
          "latestAppVersionName" to profile.latestAppVersionName.trim().ifBlank { "1.0.0" }.take(32),
          "apkDownloadUrl" to profile.apkDownloadUrl.trim().take(500),
          "controllerApkUrl" to profile.controllerApkUrl.trim().take(500),
          "updatedBy" to AdminOperatorVerifier.OPERATOR_HANDLE,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!exists) {
        baseMap["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(baseMap).await()
      } else {
        docRef.update(baseMap).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.UPDATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  fun observeRemoteNotifications(): Flow<Result<List<RemoteNotificationDoc>>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_REMOTE_NOTIFICATIONS)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(40)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val list =
            snapshot?.documents?.mapNotNull { doc ->
              doc.toObject(RemoteNotificationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.orEmpty()
          trySend(Result.success(list))
        }
    awaitClose { registration.remove() }
  }

  suspend fun sendBroadcastNotification(
    title: String,
    body: String,
    category: String,
    updateAlert: Boolean,
  ): Result<Unit> {
    val path = COLLECTION_REMOTE_NOTIFICATIONS
    return try {
      val cleanTitle = title.trim().take(120)
      val cleanBody = body.trim().take(800)
      require(cleanTitle.isNotBlank() && cleanBody.isNotBlank()) { "يرجى إدخال عنوان ونص الإشعار." }
      val validCategory = category.takeIf { it in listOf("update", "announcement", "warning") } ?: "announcement"

      val docRef = firestore.collection(COLLECTION_REMOTE_NOTIFICATIONS).document()
      val payload =
        hashMapOf<String, Any>(
          "audience" to "all",
          "title" to cleanTitle,
          "body" to cleanBody,
          "category" to validCategory,
          "updateAlert" to updateAlert,
          "senderLabel" to AdminOperatorVerifier.OPERATOR_HANDLE,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      docRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.CREATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  suspend fun deleteBroadcastNotification(notifId: String): Result<Unit> {
    val path = "$COLLECTION_REMOTE_NOTIFICATIONS/$notifId"
    return try {
      firestore.collection(COLLECTION_REMOTE_NOTIFICATIONS).document(notifId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.DELETE, path, currentUid()) }
      Result.failure(e)
    }
  }

  fun observeAllUsers(): Flow<Result<List<ManagedUserDoc>>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_USERS)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(100)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val list =
            snapshot?.documents?.mapNotNull { doc ->
              doc.toObject(ManagedUserDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.orEmpty()
          trySend(Result.success(list))
        }
    awaitClose { registration.remove() }
  }

  suspend fun updateUserBlockState(
    userId: String,
    blocked: Boolean,
    reason: String,
  ): Result<Unit> {
    val path = "$COLLECTION_USERS/$userId"
    return try {
      firestore
        .collection(COLLECTION_USERS)
        .document(userId)
        .update(
          mapOf(
            "blocked" to blocked,
            "blockedReason" to reason.trim().take(300),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.UPDATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  suspend fun sendDirectInboxMessage(
    recipientUid: String,
    title: String,
    body: String,
    category: String = "direct",
  ): Result<Unit> {
    val path = "$COLLECTION_USERS/$recipientUid/$SUBCOLLECTION_INBOX"
    return try {
      val cleanTitle = title.trim().take(120)
      val cleanBody = body.trim().take(800)
      require(cleanTitle.isNotBlank() && cleanBody.isNotBlank()) { "يرجى إدخال عنوان ونص الرسالة." }
      val validCategory =
        category.takeIf { it in listOf("update", "announcement", "warning", "direct") } ?: "direct"

      val msgRef =
        firestore
          .collection(COLLECTION_USERS)
          .document(recipientUid)
          .collection(SUBCOLLECTION_INBOX)
          .document()
      val payload =
        hashMapOf<String, Any>(
          "recipientUid" to recipientUid,
          "title" to cleanTitle,
          "body" to cleanBody,
          "category" to validCategory,
          "read" to false,
          "senderLabel" to AdminOperatorVerifier.OPERATOR_HANDLE,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      msgRef.set(payload).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.CREATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  suspend fun fetchLatestGithubRelease(repoSlug: String): Result<GithubReleaseInfo> =
    withContext(Dispatchers.IO) {
      try {
        val cleanSlug = repoSlug.trim().removePrefix("https://github.com/").trim('/')
        require(cleanSlug.contains("/")) { "اكتب اسم المستودع بصيغة owner/repo" }
        val request =
          Request.Builder()
            .url("https://api.github.com/repos/$cleanSlug/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
          if (!response.isSuccessful) {
            throw IllegalStateException("تعذر قراءة إصدار GitHub (HTTP ${response.code})")
          }
          val root = JSONObject(response.body?.string().orEmpty())
          val tag = root.optString("tag_name", "latest-debug")
          val name = root.optString("name", tag)
          val assets = root.optJSONArray("assets")
          var originalUrl = ""
          var controllerUrl = ""
          if (assets != null) {
            for (i in 0 until assets.length()) {
              val item = assets.getJSONObject(i)
              val assetName = item.optString("name", "").lowercase()
              val dl = item.optString("browser_download_url", "")
              if (assetName.contains("controller") && assetName.endsWith(".apk")) {
                controllerUrl = dl
              } else if (assetName.endsWith(".apk")) {
                originalUrl = dl
              }
            }
          }
          Result.success(
            GithubReleaseInfo(
              tagName = tag,
              releaseName = name,
              originalApkUrl = originalUrl,
              controllerApkUrl = controllerUrl,
            )
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
}
