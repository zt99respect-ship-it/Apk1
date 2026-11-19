package com.livetranslator.controller.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.livetranslator.controller.ControllerApp
import com.livetranslator.controller.R
import com.livetranslator.controller.data.AdminOperatorVerifier
import com.livetranslator.controller.data.AppControlConfigDoc
import com.livetranslator.controller.data.HotfixProfileDoc
import com.livetranslator.controller.data.ManagedUserDoc
import com.livetranslator.controller.data.RemoteNotificationDoc
import com.livetranslator.controller.data.toArabicAdminError
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AdminGateUiState {
  data object VerifyingAdmin : AdminGateUiState
  data object NeedsOperatorUnlock : AdminGateUiState
  data object Ready : AdminGateUiState
  data class Denied(val message: String) : AdminGateUiState
}

class ControllerAuthViewModel(private val app: ControllerApp) : ViewModel() {

  private val _isSigningIn = MutableStateFlow(false)
  val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

  private val _error = MutableStateFlow<String?>(null)
  val error: StateFlow<String?> = _error.asStateFlow()

  private var autoAttempted = false

  fun attemptAutoSignIn(activityContext: Context) {
    if (autoAttempted || app.auth.currentUser != null) return
    autoAttempted = true
    viewModelScope.launch {
      runCatching {
        val cm = CredentialManager.create(activityContext)
        val googleIdOption =
          GetGoogleIdOption.Builder()
            .setServerClientId(activityContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val req = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val res = cm.getCredential(activityContext, req)
        handleCredential(res.credential)
      }.onFailure { err ->
        if (err !is NoCredentialException && err !is GetCredentialCancellationException) {
          Log.w("ControllerAuth", "Auto sign-in skipped", err)
        }
      }
    }
  }

  fun signInWithGoogle(activityContext: Context) {
    if (_isSigningIn.value) return
    _isSigningIn.value = true
    _error.value = null
    viewModelScope.launch {
      try {
        val cm = CredentialManager.create(activityContext)
        val option =
          GetSignInWithGoogleOption.Builder(
            activityContext.getString(R.string.default_web_client_id)
          ).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val res = cm.getCredential(activityContext, req)
        handleCredential(res.credential)
      } catch (_: GetCredentialCancellationException) {
        _error.value = null
      } catch (e: Exception) {
        Log.e("ControllerAuth", "Google Sign-In failed", e)
        _error.value = "فشل تسجيل الدخول بحساب جوجل: ${e.localizedMessage ?: "حاول مجدداً"}"
      } finally {
        _isSigningIn.value = false
      }
    }
  }

  private suspend fun handleCredential(credential: androidx.credentials.Credential) {
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val idTokenCred = GoogleIdTokenCredential.createFrom(credential.data)
      val firebaseCred = GoogleAuthProvider.getCredential(idTokenCred.idToken, null)
      app.auth.signInWithCredential(firebaseCred).await()
    } else {
      _error.value = "بيانات اعتماد جوجل غير صالحة."
    }
  }
}

class ControllerConsoleViewModel(
  private val user: FirebaseUser,
  private val app: ControllerApp,
) : ViewModel() {

  private val repo = app.controllerRepository

  private val _gateState = MutableStateFlow<AdminGateUiState>(AdminGateUiState.VerifyingAdmin)
  val gateState: StateFlow<AdminGateUiState> = _gateState.asStateFlow()

  private val _unlockError = MutableStateFlow<String?>(null)
  val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

  private val _statusFeedback = MutableStateFlow<String?>(null)
  val statusFeedback: StateFlow<String?> = _statusFeedback.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  val appControl: StateFlow<AppControlConfigDoc> =
    repo
      .observeAppControl()
      .map { it.getOrDefault(AppControlConfigDoc()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppControlConfigDoc())

  val hotfixProfile: StateFlow<HotfixProfileDoc> =
    repo
      .observeHotfixProfile()
      .map { it.getOrDefault(HotfixProfileDoc()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HotfixProfileDoc())

  val remoteNotifications: StateFlow<List<RemoteNotificationDoc>> =
    repo
      .observeRemoteNotifications()
      .map { it.getOrDefault(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val usersList: StateFlow<List<ManagedUserDoc>> =
    repo
      .observeAllUsers()
      .map { it.getOrDefault(emptyList()) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  init {
    viewModelScope.launch {
      repo
        .verifyAndBootstrapAdmin(user)
        .onSuccess { authorized ->
          _gateState.value =
            if (authorized) AdminGateUiState.NeedsOperatorUnlock
            else AdminGateUiState.Denied(
              "هذا الحساب (${user.email ?: user.uid}) غير مصرح له كمدير. سجّل دخولك بحساب المدير المعتمد."
            )
        }
        .onFailure { err ->
          _gateState.value = AdminGateUiState.Denied(toArabicAdminError(err))
        }
    }
  }

  fun unlockOperatorConsole(username: String, password: String) {
    if (AdminOperatorVerifier.verifyOperatorCredentials(username, password)) {
      _unlockError.value = null
      _gateState.value = AdminGateUiState.Ready
    } else {
      _unlockError.value = "اسم المستخدم أو كلمة المرور غير صحيحة."
    }
  }

  fun saveAppControl(config: AppControlConfigDoc) {
    runAction("تم حفظ وتطبيق إعدادات التحكم الشامل على جميع الأجهزة فوراً.") {
      repo.saveAppControl(config)
    }
  }

  fun publishHotfix(profile: HotfixProfileDoc) {
    runAction("تم نشر التصحيح الساخن v${profile.patchVersion} للمستخدمين بدون إعادة تثبيت!") {
      repo.publishHotfixProfile(profile)
    }
  }

  fun syncLatestGithubRelease(
    repoSlug: String,
    currentHotfix: HotfixProfileDoc,
    onUrlsFetched: (originalApk: String, controllerApk: String, tagName: String) -> Unit,
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repo
        .fetchLatestGithubRelease(repoSlug)
        .onSuccess { info ->
          onUrlsFetched(info.originalApkUrl, info.controllerApkUrl, info.tagName)
          _statusFeedback.value = "تم جلب روابط آخر إصدار من GitHub (${info.tagName})."
        }
        .onFailure { err ->
          _statusFeedback.value = err.message ?: "تعذر الاتصال بـ GitHub."
        }
      _isBusy.value = false
    }
  }

  fun sendBroadcast(title: String, body: String, category: String, updateAlert: Boolean) {
    runAction("تم إرسال الإشعار العام لجميع مستخدمي التطبيق الأصلي.") {
      repo.sendBroadcastNotification(title, body, category, updateAlert)
    }
  }

  fun deleteBroadcast(id: String) {
    runAction("تم حذف الإشعار من السجل السحابي.") {
      repo.deleteBroadcastNotification(id)
    }
  }

  fun setUserBlocked(userId: String, blocked: Boolean, reason: String) {
    runAction(if (blocked) "تم حظر المستخدم بنجاح." else "تم رفع الحظر عن المستخدم بنجاح.") {
      repo.updateUserBlockState(userId, blocked, reason)
    }
  }

  fun sendDirectInbox(recipientUid: String, title: String, body: String, category: String) {
    runAction("تم إرسال الإشعار الخاص إلى المستخدم المحدد.") {
      repo.sendDirectInboxMessage(recipientUid, title, body, category)
    }
  }

  fun clearFeedback() {
    _statusFeedback.value = null
  }

  private fun runAction(successMessage: String, block: suspend () -> Result<Unit>) {
    viewModelScope.launch {
      _isBusy.value = true
      block()
        .onSuccess { _statusFeedback.value = successMessage }
        .onFailure { err -> _statusFeedback.value = err.message ?: toArabicAdminError(err) }
      _isBusy.value = false
    }
  }

  fun signOut(context: Context) {
    viewModelScope.launch {
      withContext(NonCancellable) {
        runCatching {
          CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
      }
      app.auth.signOut()
    }
  }
}

class ControllerAuthViewModelFactory(private val app: ControllerApp) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    ControllerAuthViewModel(app) as T
}

class ControllerConsoleViewModelFactory(
  private val user: FirebaseUser,
  private val app: ControllerApp,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    ControllerConsoleViewModel(user, app) as T
}
