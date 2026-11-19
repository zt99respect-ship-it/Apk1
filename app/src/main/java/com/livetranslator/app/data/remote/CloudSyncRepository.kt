package com.livetranslator.app.data.remote

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
    trySend(firebaseAuth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

class CloudSyncRepository(
  private val firestore: FirebaseFirestore,
  private val auth: FirebaseAuth = Firebase.auth,
) {
  companion object {
    const val COLLECTION_USERS = "users"
    const val SUBCOLLECTION_INBOX = "inbox"
    const val COLLECTION_HOTFIX = "hotfix_profiles"
    const val DOC_HOTFIX_LIVE = "live"
    const val COLLECTION_APP_CONTROL = "app_control"
    const val DOC_APP_CONTROL_GLOBAL = "global"
    const val COLLECTION_REMOTE_NOTIFICATIONS = "remote_notifications"
  }

  private fun currentUid(): String? = auth.currentUser?.uid

  fun observeUserProfile(uid: String): Flow<Result<UserProfileDoc?>> = callbackFlow {
    val path = "$COLLECTION_USERS/$uid"
    val registration =
      firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val profile =
            snapshot
              ?.takeIf { it.exists() }
              ?.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          trySend(Result.success(profile))
        }
    awaitClose { registration.remove() }
  }

  suspend fun registerUserProfile(
    uid: String,
    email: String,
    username: String,
    rawPassword: String,
    appliedPatchVersion: Int,
  ): Result<UserProfileDoc> {
    val path = "$COLLECTION_USERS/$uid"
    return try {
      val cleanUsername = username.trim()
      require(cleanUsername.length in 2..40) { "يجب أن يكون اسم المستخدم بين 2 و 40 حرفاً." }
      require(rawPassword.length >= 6) { "يجب أن تكون كلمة المرور 6 أحرف على الأقل." }

      val docRef = firestore.collection(COLLECTION_USERS).document(uid)
      val existing =
        docRef
          .get()
          .await()
          .takeIf { it.exists() }
          ?.toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

      if (existing != null && existing.secretCode.isNotBlank()) {
        return Result.success(existing)
      }

      val salt = CryptoPasswordHelper.generateSaltHex()
      val hash = CryptoPasswordHelper.hashPassword(rawPassword, salt)
      val secretCode = CryptoPasswordHelper.generateUniqueUserCode()

      val payload =
        hashMapOf<String, Any>(
          "userId" to uid,
          "email" to email.trim().take(200),
          "username" to cleanUsername,
          "passwordHash" to hash,
          "passwordSalt" to salt,
          "secretCode" to secretCode,
          "role" to "user",
          "blocked" to false,
          "appliedPatchVersion" to appliedPatchVersion.coerceIn(1, 100000),
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )

      docRef.set(payload).await()
      val created =
        docRef
          .get()
          .await()
          .toObject(UserProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          ?: UserProfileDoc(
            id = uid,
            userId = uid,
            email = email.trim(),
            username = cleanUsername,
            passwordHash = hash,
            passwordSalt = salt,
            secretCode = secretCode,
            role = "user",
            blocked = false,
            appliedPatchVersion = appliedPatchVersion.coerceIn(1, 100000),
          )
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.CREATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  suspend fun updateUserCredentials(
    uid: String,
    newUsername: String?,
    newPassword: String?,
  ): Result<Unit> {
    val path = "$COLLECTION_USERS/$uid"
    return try {
      val updates = hashMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
      if (!newUsername.isNullOrBlank()) {
        val clean = newUsername.trim()
        require(clean.length in 2..40) { "اسم المستخدم يجب أن يكون بين 2 و 40 حرفاً." }
        updates["username"] = clean
      }
      if (!newPassword.isNullOrBlank()) {
        require(newPassword.length >= 6) { "كلمة المرور يجب أن تكون 6 أحرف على الأقل." }
        val salt = CryptoPasswordHelper.generateSaltHex()
        val hash = CryptoPasswordHelper.hashPassword(newPassword, salt)
        updates["passwordSalt"] = salt
        updates["passwordHash"] = hash
      }
      firestore.collection(COLLECTION_USERS).document(uid).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.UPDATE, path, currentUid()) }
      Result.failure(e)
    }
  }

  suspend fun syncAppliedPatchVersion(uid: String, patchVersion: Int): Result<Unit> {
    val path = "$COLLECTION_USERS/$uid"
    return try {
      val updates =
        hashMapOf<String, Any>(
          "appliedPatchVersion" to patchVersion.coerceIn(1, 100000),
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      firestore.collection(COLLECTION_USERS).document(uid).update(updates).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun touchLastSeen(uid: String) {
    runCatching {
      firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .update(
          mapOf(
            "lastSeenAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    }
  }

  fun observeHotfixProfile(): Flow<Result<HotfixProfileDoc?>> = callbackFlow {
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
          trySend(Result.success(doc))
        }
    awaitClose { registration.remove() }
  }

  suspend fun fetchHotfixProfileOnce(): Result<HotfixProfileDoc?> {
    val path = "$COLLECTION_HOTFIX/$DOC_HOTFIX_LIVE"
    return try {
      val snapshot = firestore.collection(COLLECTION_HOTFIX).document(DOC_HOTFIX_LIVE).get().await()
      val doc =
        snapshot
          .takeIf { it.exists() }
          ?.toObject(HotfixProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      Result.success(doc)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      runCatching { handleFirestoreError(e, OperationType.GET, path, currentUid()) }
      Result.failure(e)
    }
  }

  fun observeAppControlConfig(): Flow<Result<AppControlConfigDoc>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_APP_CONTROL)
        .document(DOC_APP_CONTROL_GLOBAL)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val cfg =
            snapshot
              ?.takeIf { it.exists() }
              ?.toObject(AppControlConfigDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              ?: AppControlConfigDoc()
          trySend(Result.success(cfg))
        }
    awaitClose { registration.remove() }
  }

  suspend fun fetchAppControlConfigOnce(): Result<AppControlConfigDoc?> {
    val path = "$COLLECTION_APP_CONTROL/$DOC_APP_CONTROL_GLOBAL"
    return try {
      val snapshot =
        firestore.collection(COLLECTION_APP_CONTROL).document(DOC_APP_CONTROL_GLOBAL).get().await()
      val cfg =
        snapshot
          .takeIf { it.exists() }
          ?.toObject(AppControlConfigDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      Result.success(cfg)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun observeGlobalNotifications(): Flow<Result<List<RemoteNotificationDoc>>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_REMOTE_NOTIFICATIONS)
        .whereEqualTo("audience", "all")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(25)
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

  fun observeUserInbox(uid: String): Flow<Result<List<UserInboxDoc>>> = callbackFlow {
    val registration =
      firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .collection(SUBCOLLECTION_INBOX)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(30)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            trySend(Result.failure(error))
            return@addSnapshotListener
          }
          val list =
            snapshot?.documents?.mapNotNull { doc ->
              doc.toObject(UserInboxDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.orEmpty()
          trySend(Result.success(list))
        }
    awaitClose { registration.remove() }
  }

  suspend fun markInboxRead(uid: String, messageId: String): Result<Unit> {
    val path = "$COLLECTION_USERS/$uid/$SUBCOLLECTION_INBOX/$messageId"
    return try {
      firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .collection(SUBCOLLECTION_INBOX)
        .document(messageId)
        .update(
          mapOf(
            "read" to true,
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
}
