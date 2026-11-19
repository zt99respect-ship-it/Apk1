package com.livetranslator.app.ui.viewmodel

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
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.R
import com.livetranslator.app.data.model.SpeechEnginePreference
import com.livetranslator.app.data.model.SupportedLanguages
import com.livetranslator.app.data.remote.AppControlConfigDoc
import com.livetranslator.app.data.remote.CloudGateUiState
import com.livetranslator.app.data.remote.HotfixProfileDoc
import com.livetranslator.app.data.remote.RemoteNotificationDoc
import com.livetranslator.app.data.remote.UserInboxDoc
import com.livetranslator.app.data.remote.UserProfileDoc
import com.livetranslator.app.data.remote.toArabicCloudError
import com.livetranslator.app.engine.HotfixBackgroundWorker
import com.livetranslator.app.engine.OtaCenterState
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class CloudAuthViewModel(private val app: LiveTranslatorApp) : ViewModel() {

  private val _isSigningIn = MutableStateFlow(false)
  val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private var autoSignInAttempted = false

  fun attemptAutoSignIn(activityContext: Context) {
    if (autoSignInAttempted || app.auth.currentUser != null) return
    autoSignInAttempted = true
    viewModelScope.launch {
      runCatching {
        val credentialManager = CredentialManager.create(activityContext)
        val googleIdOption =
          GetGoogleIdOption.Builder()
            .setServerClientId(activityContext.getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(true)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
        val response = credentialManager.getCredential(activityContext, request)
        handleCredential(response.credential)
      }.onFailure { err ->
        if (err !is NoCredentialException && err !is GetCredentialCancellationException) {
          Log.w("CloudAuth", "Silent Google sign-in skipped", err)
        }
      }
    }
  }

  fun signInWithGoogle(activityContext: Context) {
    if (_isSigningIn.value) return
    _isSigningIn.value = true
    _authError.value = null
    viewModelScope.launch {
      try {
        val credentialManager = CredentialManager.create(activityContext)
        val signInOption =
          GetSignInWithGoogleOption.Builder(
            activityContext.getString(R.string.default_web_client_id)
          ).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
        val response = credentialManager.getCredential(activityContext, request)
        handleCredential(response.credential)
      } catch (_: GetCredentialCancellationException) {
        _authError.value = null
      } catch (e: Exception) {
        Log.e("CloudAuth", "Interactive Google Sign-In failed", e)
        _authError.value = "تعذر تسجيل الدخول بحساب جوجل: ${e.localizedMessage ?: "يرجى المحاولة مجدداً"}"
      } finally {
        _isSigningIn.value = false
      }
    }
  }

  fun clearError() {
    _authError.value = null
  }

  private suspend fun handleCredential(credential: androidx.credentials.Credential) {
    if (credential is CustomCredential &&
      credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
      val firebaseCredential =
        GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
      app.auth.signInWithCredential(firebaseCredential).await()
      HotfixBackgroundWorker.triggerImmediateCheck(app)
    } else {
      _authError.value = "نوع بيانات الاعتماد المستلم غير مدعوم."
    }
  }
}

class CloudSessionViewModel(
  private val uid: String,
  private val userEmail: String,
  private val app: LiveTranslatorApp,
) : ViewModel() {

  private val cloudSync = app.cloudSyncRepository
  private val otaManager = app.otaUpdateManager
  private val repository = app.repository

  private val _isSubmittingSetup = MutableStateFlow(false)
  val isSubmittingSetup: StateFlow<Boolean> = _isSubmittingSetup.asStateFlow()

  private val _setupError = MutableStateFlow<String?>(null)
  val setupError: StateFlow<String?> = _setupError.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  private val _hotfixDialogDismissedVersion = MutableStateFlow(0)
  val hotfixDialogDismissedVersion: StateFlow<Int> = _hotfixDialogDismissedVersion.asStateFlow()

  val otaState: StateFlow<OtaCenterState> = otaManager.state

  private val profileFlow = cloudSync.observeUserProfile(uid)
  private val controlFlow = cloudSync.observeAppControlConfig()
  private val hotfixFlow = cloudSync.observeHotfixProfile()
  private val globalNotifFlow = cloudSync.observeGlobalNotifications()
  private val inboxFlow = cloudSync.observeUserInbox(uid)

  val uiState: StateFlow<CloudGateUiState> =
    combine(
      profileFlow,
      controlFlow,
      hotfixFlow,
      inboxFlow,
      globalNotifFlow,
    ) { profileRes, controlRes, hotfixRes, inboxRes, globalRes ->
      val profileErr = profileRes.exceptionOrNull()
      if (profileErr != null) {
        return@combine CloudGateUiState.Error(toArabicCloudError(profileErr))
      }

      val profile = profileRes.getOrNull()
      if (profile == null) {
        return@combine CloudGateUiState.NeedsCredentialsSetup
      }

      val appControl = controlRes.getOrNull() ?: AppControlConfigDoc()
      val hotfix = hotfixRes.getOrNull()
      val inbox = inboxRes.getOrNull().orEmpty()
      val remoteNotifs = globalRes.getOrNull().orEmpty()

      CloudGateUiState.Ready(
        profile = profile,
        appControl = appControl,
        hotfix = hotfix,
        inboxMessages = inbox,
        remoteNotifications = remoteNotifs,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = CloudGateUiState.LoadingProfile,
    )

  init {
    viewModelScope.launch {
      cloudSync.touchLastSeen(uid)
    }

    // Observe AppControlConfig and push enforcement into LiveTranslatorApp & DataStore
    viewModelScope.launch {
      controlFlow.collect { res ->
        val cfg = res.getOrNull() ?: return@collect
        app.remoteControlConfig.value = cfg
        applyRemoteControlOverrides(cfg)
      }
    }

    // Observe HotfixProfileDoc and stage incremental updates live in-app
    viewModelScope.launch {
      hotfixFlow.collect { res ->
        val hotfix = res.getOrNull() ?: return@collect
        otaManager.stageCloudHotfix(hotfix, notifyUser = true)
      }
    }

    // Observe global remote_notifications pushed by Admin Controller
    viewModelScope.launch {
      globalNotifFlow.collect { res ->
        val items = res.getOrNull().orEmpty()
        items.forEach { doc ->
          otaManager.recordRemoteNotification(
            id = "global_${doc.id}",
            title = doc.title,
            body = doc.body,
            isUpdateAlert = doc.updateAlert || doc.category == "update",
            postSystemNotification = true,
          )
        }
      }
    }

    // Observe user-targeted inbox notifications pushed by Admin Controller
    viewModelScope.launch {
      inboxFlow.collect { res ->
        val items = res.getOrNull().orEmpty()
        items.forEach { msg ->
          otaManager.recordRemoteNotification(
            id = "inbox_${msg.id}",
            title = msg.title,
            body = msg.body,
            isUpdateAlert = msg.category == "update",
            postSystemNotification = !msg.read,
          )
        }
      }
    }
  }

  private suspend fun applyRemoteControlOverrides(cfg: AppControlConfigDoc) {
    if (cfg.forceSkipArabic) {
      repository.setSkipArabic(true)
    }
    if (cfg.forcedSpeechEngine.isNotBlank()) {
      repository.setSpeechEnginePreference(SpeechEnginePreference.fromKey(cfg.forcedSpeechEngine))
    }
    if (cfg.forcedSourceLanguage.isNotBlank() &&
      SupportedLanguages.all.any { it.code == cfg.forcedSourceLanguage }
    ) {
      repository.setSourceLanguageCode(cfg.forcedSourceLanguage)
    }
    if (cfg.remoteAudioChunkSeconds in 2..15) {
      repository.setAudioChunkSeconds(cfg.remoteAudioChunkSeconds)
    }
    if (cfg.otaFeedUrl.isNotBlank()) {
      repository.setOtaFeedUrl(cfg.otaFeedUrl)
    }
  }

  fun registerFirstTimeCredentials(username: String, password: String, confirmPassword: String) {
    val cleanUser = username.trim()
    if (cleanUser.length < 2 || cleanUser.length > 40) {
      _setupError.value = "اسم المستخدم يجب أن يكون بين حرفين و 40 حرفاً."
      return
    }
    if (password.length < 6) {
      _setupError.value = "كلمة المرور يجب أن تكون 6 أحرف على الأقل."
      return
    }
    if (password != confirmPassword) {
      _setupError.value = "كلمتا المرور غير متطابقتين."
      return
    }
    _isSubmittingSetup.value = true
    _setupError.value = null
    viewModelScope.launch {
      val currentPatch = repository.getCurrentSettings().appliedPatchVersion
      cloudSync
        .registerUserProfile(
          uid = uid,
          email = userEmail,
          username = cleanUser,
          rawPassword = password,
          appliedPatchVersion = currentPatch,
        )
        .onSuccess { created ->
          repository.setAccountLabel(created.username)
          _actionMessage.value = "تم إنشاء حسابك وتوليد الكود السري الخاص بك بنجاح."
        }
        .onFailure { err ->
          _setupError.value = err.message ?: toArabicCloudError(err)
        }
      _isSubmittingSetup.value = false
    }
  }

  fun updateUsernameOrPassword(newUsername: String?, newPassword: String?) {
    viewModelScope.launch {
      cloudSync
        .updateUserCredentials(uid = uid, newUsername = newUsername, newPassword = newPassword)
        .onSuccess {
          if (!newUsername.isNullOrBlank()) {
            repository.setAccountLabel(newUsername.trim())
          }
          _actionMessage.value = "تم تحديث بيانات الحساب في السحابة بنجاح."
        }
        .onFailure { err ->
          _actionMessage.value = err.message ?: toArabicCloudError(err)
        }
    }
  }

  fun applyHotfixNow() {
    viewModelScope.launch {
      otaManager
        .applyAvailableManifest { appliedVersion ->
          cloudSync.syncAppliedPatchVersion(uid, appliedVersion)
        }
        .onSuccess { version ->
          _actionMessage.value = "تم تطبيق التحديث الساخن v$version بنجاح بدون إعادة تثبيت!"
        }
        .onFailure { err ->
          _actionMessage.value = err.message ?: "تعذر تطبيق التحديث الساخن."
        }
    }
  }

  fun dismissHotfixPromptForVersion(version: Int) {
    _hotfixDialogDismissedVersion.value = version
  }

  fun markInboxMessageRead(messageId: String) {
    viewModelScope.launch {
      cloudSync.markInboxRead(uid, messageId)
    }
  }

  fun clearActionMessage() {
    _actionMessage.value = null
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

class CloudAuthViewModelFactory(private val app: LiveTranslatorApp) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(CloudAuthViewModel::class.java)) {
      return CloudAuthViewModel(app) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}

class CloudSessionViewModelFactory(
  private val user: FirebaseUser,
  private val app: LiveTranslatorApp,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(CloudSessionViewModel::class.java)) {
      return CloudSessionViewModel(
        uid = user.uid,
        userEmail = user.email.orEmpty(),
        app = app,
      ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
