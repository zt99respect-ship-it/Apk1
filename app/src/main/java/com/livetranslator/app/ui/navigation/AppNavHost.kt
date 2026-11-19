package com.livetranslator.app.ui.navigation

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseUser
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.R
import com.livetranslator.app.data.model.AudioSourcePreference
import com.livetranslator.app.data.model.TranslationMode
import com.livetranslator.app.data.remote.CloudGateUiState
import com.livetranslator.app.data.remote.authStateFlow
import com.livetranslator.app.service.OverlayService
import com.livetranslator.app.service.TranslationForegroundService
import com.livetranslator.app.ui.screens.AccountScreen
import com.livetranslator.app.ui.screens.CloudLoadingProfileScreen
import com.livetranslator.app.ui.screens.DevicesScreen
import com.livetranslator.app.ui.screens.FirstEntryUserSetupScreen
import com.livetranslator.app.ui.screens.GlobalAdminBannerStrip
import com.livetranslator.app.ui.screens.GoogleAuthScreen
import com.livetranslator.app.ui.screens.HomeScreen
import com.livetranslator.app.ui.screens.InAppHotfixPromptDialog
import com.livetranslator.app.ui.screens.MaintenanceOrBlockedScreen
import com.livetranslator.app.ui.screens.NotificationsUpdatesScreen
import com.livetranslator.app.ui.screens.OnboardingScreen
import com.livetranslator.app.ui.screens.SettingsScreen
import com.livetranslator.app.ui.screens.SplashScreen
import com.livetranslator.app.ui.screens.SummaryScreen
import com.livetranslator.app.ui.screens.TranslationStyleScreen
import com.livetranslator.app.ui.theme.ArabicFontRegistry
import com.livetranslator.app.ui.theme.CrispWhite
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.GlassCardFillStrong
import com.livetranslator.app.ui.theme.LightGrayText
import com.livetranslator.app.ui.theme.SoftLavender
import com.livetranslator.app.ui.theme.VoidBackground
import com.livetranslator.app.ui.theme.homeRadialBackground
import com.livetranslator.app.ui.viewmodel.AccountViewModel
import com.livetranslator.app.ui.viewmodel.CloudAuthViewModel
import com.livetranslator.app.ui.viewmodel.CloudAuthViewModelFactory
import com.livetranslator.app.ui.viewmodel.CloudSessionViewModel
import com.livetranslator.app.ui.viewmodel.CloudSessionViewModelFactory
import com.livetranslator.app.ui.viewmodel.DevicesViewModel
import com.livetranslator.app.ui.viewmodel.LiveTranslatorViewModelFactory
import com.livetranslator.app.ui.viewmodel.MainViewModel
import com.livetranslator.app.ui.viewmodel.OverlayStyleViewModel
import com.livetranslator.app.ui.viewmodel.SummaryViewModel
import com.livetranslator.app.ui.viewmodel.UpdatesCenterViewModel

private data class FloatingTab(
  val route: String,
  val labelRes: Int,
  val iconRes: Int,
  val testTag: String,
)

@Composable
fun AppNavHost(app: LiveTranslatorApp) {
  val authFlow = remember(app.auth) { app.auth.authStateFlow() }
  val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = app.auth.currentUser)

  val authViewModel: CloudAuthViewModel = viewModel(factory = CloudAuthViewModelFactory(app))

  val signedInUser = currentUser
  if (signedInUser == null) {
    GoogleAuthScreen(viewModel = authViewModel)
    return
  }

  key(signedInUser.uid) {
    AuthenticatedAppRoot(app = app, user = signedInUser)
  }
}

@Composable
private fun AuthenticatedAppRoot(
  app: LiveTranslatorApp,
  user: FirebaseUser,
) {
  val context = LocalContext.current
  val cloudSessionViewModel: CloudSessionViewModel =
    viewModel(
      key = "cloud_session_${user.uid}",
      factory = CloudSessionViewModelFactory(user, app),
    )

  val gateState by cloudSessionViewModel.uiState.collectAsStateWithLifecycle()
  val isSubmittingSetup by cloudSessionViewModel.isSubmittingSetup.collectAsStateWithLifecycle()
  val setupError by cloudSessionViewModel.setupError.collectAsStateWithLifecycle()
  val otaState by cloudSessionViewModel.otaState.collectAsStateWithLifecycle()
  val dismissedPatchVer by
    cloudSessionViewModel.hotfixDialogDismissedVersion.collectAsStateWithLifecycle()

  fun performSignOut() {
    runCatching {
      context.stopService(Intent(context, TranslationForegroundService::class.java))
      context.stopService(Intent(context, OverlayService::class.java))
    }
    cloudSessionViewModel.signOut(context)
  }

  when (val state = gateState) {
    is CloudGateUiState.LoadingProfile -> {
      CloudLoadingProfileScreen()
    }

    is CloudGateUiState.NeedsCredentialsSetup -> {
      FirstEntryUserSetupScreen(
        email = user.email.orEmpty(),
        isSubmitting = isSubmittingSetup,
        errorMessage = setupError,
        onSubmit = { username, password, confirm ->
          cloudSessionViewModel.registerFirstTimeCredentials(username, password, confirm)
        },
        onSignOut = { performSignOut() },
      )
    }

    is CloudGateUiState.Error -> {
      MaintenanceOrBlockedScreen(
        title = "تعذر الاتصال بالحساب",
        message = state.message,
        isBlockedAccount = true,
        secretCode = null,
        onSignOut = { performSignOut() },
      )
    }

    is CloudGateUiState.Ready -> {
      val profile = state.profile
      val appControl = state.appControl

      if (profile.blocked) {
        MaintenanceOrBlockedScreen(
          title = "تم تقييد الحساب",
          message =
            profile.blockedReason.ifBlank {
              "تم إيقاف هذا الحساب مؤقتاً من قِبل الإدارة."
            },
          isBlockedAccount = true,
          secretCode = profile.secretCode,
          onSignOut = { performSignOut() },
        )
        return
      }

      if (appControl.maintenanceEnabled) {
        MaintenanceOrBlockedScreen(
          title = "التطبيق في وضع الصيانة",
          message =
            appControl.maintenanceMessage.ifBlank {
              "جاري تنفيذ تحديثات وتحسينات حالياً. يرجى المحاولة بعد قليل."
            },
          isBlockedAccount = false,
          secretCode = profile.secretCode,
          onSignOut = { performSignOut() },
        )
        return
      }

      val factory = LiveTranslatorViewModelFactory(app)
      val mainViewModel: MainViewModel = viewModel(factory = factory)
      val overlayStyleViewModel: OverlayStyleViewModel = viewModel(factory = factory)
      val summaryViewModel: SummaryViewModel = viewModel(factory = factory)
      val devicesViewModel: DevicesViewModel = viewModel(factory = factory)
      val accountViewModel: AccountViewModel = viewModel(factory = factory)
      val updatesCenterViewModel: UpdatesCenterViewModel = viewModel(factory = factory)

      val settings by mainViewModel.settings.collectAsStateWithLifecycle()
      val sessionState by mainViewModel.sessionState.collectAsStateWithLifecycle()
      val navController = rememberNavController()
      val backStackEntry by navController.currentBackStackEntryAsState()
      val currentRoute = backStackEntry?.destination?.route
      val focusManager = LocalFocusManager.current
      val textToolbar = LocalTextToolbar.current

      val overlayProjectionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
          if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            app.sessionManager.setPendingMediaProjection(result.resultCode, result.data)
            val serviceIntent =
              Intent(context, TranslationForegroundService::class.java).apply {
                action = TranslationForegroundService.ACTION_START
              }
            ContextCompat.startForegroundService(context, serviceIntent)
            (context as? android.app.Activity)?.moveTaskToBack(true)
          } else {
            app.sessionManager.publishError("تم إلغاء إذن التقاط صوت الهاتف", expandOverlay = false)
          }
        }

      val overlayMicLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
          if (granted) {
            val serviceIntent =
              Intent(context, TranslationForegroundService::class.java).apply {
                action = TranslationForegroundService.ACTION_START_MIC_ONLY
              }
            ContextCompat.startForegroundService(context, serviceIntent)
            (context as? android.app.Activity)?.moveTaskToBack(true)
          }
        }

      LaunchedEffect(sessionState.pendingStartRequestId) {
        if (sessionState.pendingStartRequestId > 0L) {
          app.sessionManager.clearPendingStartRequest()
          val remote = app.remoteControlConfig.value
          if (settings.translationMode == TranslationMode.SCREEN_OCR && !remote.allowScreenOcr) {
            app.sessionManager.publishError("تم إيقاف وضع ترجمة الشاشة مؤقتاً من لوحة التحكم.")
            return@LaunchedEffect
          }
          if (settings.translationMode == TranslationMode.AUDIO_LIVE && !remote.allowAudioLive) {
            app.sessionManager.publishError("تم إيقاف وضع ترجمة الصوت مؤقتاً من لوحة التحكم.")
            return@LaunchedEffect
          }

          if (settings.translationMode == TranslationMode.AUDIO_LIVE &&
            settings.audioSourcePreference == AudioSourcePreference.MICROPHONE
          ) {
            val hasMic =
              ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
              ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasMic) {
              val serviceIntent =
                Intent(context, TranslationForegroundService::class.java).apply {
                  action = TranslationForegroundService.ACTION_START_MIC_ONLY
                }
              ContextCompat.startForegroundService(context, serviceIntent)
              (context as? android.app.Activity)?.moveTaskToBack(true)
            } else {
              overlayMicLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
          } else {
            val mgr =
              context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                as android.media.projection.MediaProjectionManager
            overlayProjectionLauncher.launch(mgr.createScreenCaptureIntent())
          }
        }
      }

      LaunchedEffect(sessionState.pendingOpenUpdatesRequestId, currentRoute) {
        if (sessionState.pendingOpenUpdatesRequestId > 0L && currentRoute != Routes.SPLASH) {
          app.sessionManager.clearOpenUpdatesRequest()
          navController.navigate(Routes.UPDATES_CENTER) { launchSingleTop = true }
        }
      }

      LaunchedEffect(currentRoute) {
        focusManager.clearFocus(force = true)
        textToolbar.hide()
      }

      val tabs =
        listOf(
          FloatingTab(Routes.HOME, R.string.nav_home, R.drawable.ic_home, "nav_home"),
          FloatingTab(Routes.SUMMARY, R.string.nav_summary, R.drawable.ic_summary, "nav_summary"),
          FloatingTab(
            Routes.DEVICES,
            R.string.nav_devices,
            R.drawable.ic_device_audio,
            "nav_devices",
          ),
          FloatingTab(Routes.ACCOUNT, R.string.nav_account, R.drawable.ic_person, "nav_account"),
        )

      val showBottomBar = currentRoute in tabs.map { it.route }

      fun navigateToTab(route: String) {
        navController.navigate(route) {
          popUpTo(navController.graph.findStartDestination().id) { saveState = true }
          launchSingleTop = true
          restoreState = true
        }
      }

      Box(modifier = Modifier.fillMaxSize().homeRadialBackground()) {
        Scaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets.statusBars,
          bottomBar = {
            if (showBottomBar) {
              FloatingGlassBottomBar(
                tabs = tabs,
                currentRoute = currentRoute,
                onSelectTab = { navigateToTab(it) },
              )
            }
          },
        ) { innerPadding ->
          Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (appControl.showGlobalBanner && showBottomBar) {
              GlobalAdminBannerStrip(
                title = appControl.globalBannerTitle,
                message = appControl.globalBannerMessage,
              )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
              NavHost(navController = navController, startDestination = Routes.SPLASH) {
                composable(Routes.SPLASH) {
                  SplashScreen(
                    onFinished = {
                      val dest = if (settings.onboardingCompleted) Routes.HOME else Routes.ONBOARDING
                      navController.navigate(dest) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                      }
                    }
                  )
                }

                composable(Routes.ONBOARDING) {
                  OnboardingScreen(
                    onComplete = {
                      mainViewModel.completeOnboarding()
                      navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                      }
                    }
                  )
                }

                composable(Routes.HOME) {
                  HomeScreen(
                    viewModel = mainViewModel,
                    onNavigateToDevices = { navigateToTab(Routes.DEVICES) },
                    onNavigateToAccount = { navigateToTab(Routes.ACCOUNT) },
                    onNavigateToStyle = { navController.navigate(Routes.TRANSLATION_STYLE) },
                    onNavigateToUpdates = { navController.navigate(Routes.UPDATES_CENTER) },
                  )
                }

                composable(Routes.SUMMARY) {
                  SummaryScreen(
                    viewModel = summaryViewModel,
                    mainViewModel = mainViewModel,
                    onNavigateToHome = { navigateToTab(Routes.HOME) },
                  )
                }

                composable(Routes.DEVICES) {
                  DevicesScreen(viewModel = devicesViewModel)
                }

                composable(Routes.ACCOUNT) {
                  AccountScreen(
                    viewModel = accountViewModel,
                    userProfile = profile,
                    onUpdateCredentials = { newUsername, newPassword ->
                      cloudSessionViewModel.updateUsernameOrPassword(newUsername, newPassword)
                    },
                    onSignOut = { performSignOut() },
                    onNavigateToDevices = { navigateToTab(Routes.DEVICES) },
                    onNavigateToTranslationStyle = { navController.navigate(Routes.TRANSLATION_STYLE) },
                    onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateToUpdates = { navController.navigate(Routes.UPDATES_CENTER) },
                  )
                }

                composable(Routes.TRANSLATION_STYLE) {
                  TranslationStyleScreen(
                    viewModel = overlayStyleViewModel,
                    sessionManager = app.sessionManager,
                    onBack = { navController.popBackStack() },
                  )
                }

                composable(Routes.SETTINGS) {
                  SettingsScreen(
                    viewModel = accountViewModel,
                    onNavigateToTranslationStyle = { navController.navigate(Routes.TRANSLATION_STYLE) },
                    onBack = { navController.popBackStack() },
                  )
                }

                composable(Routes.UPDATES_CENTER) {
                  NotificationsUpdatesScreen(
                    viewModel = updatesCenterViewModel,
                    inboxMessages = state.inboxMessages,
                    onMarkInboxRead = { msgId -> cloudSessionViewModel.markInboxMessageRead(msgId) },
                    onBack = { navController.popBackStack() },
                  )
                }
              }
            }
          }
        }

        val pendingManifest = otaState.availableManifest
        if (
          otaState.hasPendingPatchUpdate &&
            pendingManifest != null &&
            currentRoute != Routes.SPLASH &&
            (otaState.isMandatoryHotfix || dismissedPatchVer < pendingManifest.patchVersion)
        ) {
          InAppHotfixPromptDialog(
            otaState = otaState,
            onApplyNow = { cloudSessionViewModel.applyHotfixNow() },
            onDismiss = {
              cloudSessionViewModel.dismissHotfixPromptForVersion(pendingManifest.patchVersion)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun FloatingGlassBottomBar(
  tabs: List<FloatingTab>,
  currentRoute: String?,
  onSelectTab: (String) -> Unit,
) {
  Box(
    modifier =
      Modifier.fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Row(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxWidth()
          .height(72.dp)
          .shadow(
            elevation = 20.dp,
            shape = RoundedCornerShape(36.dp),
            ambientColor = ElectricViolet.copy(alpha = 0.45f),
            spotColor = VoidBackground,
          )
          .clip(RoundedCornerShape(36.dp))
          .background(GlassCardFillStrong)
          .border(
            width = 1.dp,
            brush =
              Brush.linearGradient(
                listOf(
                  CrispWhite.copy(alpha = 0.28f),
                  ElectricViolet.copy(alpha = 0.42f),
                  CrispWhite.copy(alpha = 0.14f),
                )
              ),
            shape = RoundedCornerShape(36.dp),
          )
          .padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      tabs.forEach { tab ->
        val selected = currentRoute == tab.route
        val interactionSource = remember { MutableInteractionSource() }
        Box(
          modifier =
            Modifier.weight(1f)
              .height(60.dp)
              .clip(RoundedCornerShape(30.dp))
              .then(
                if (selected) {
                  Modifier.background(
                    Brush.verticalGradient(
                      listOf(
                        ElectricViolet.copy(alpha = 0.34f),
                        ElectricViolet.copy(alpha = 0.16f),
                      )
                    )
                  )
                    .border(
                      1.dp,
                      ElectricViolet.copy(alpha = 0.45f),
                      RoundedCornerShape(30.dp),
                    )
                } else {
                  Modifier
                }
              )
              .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSelectTab(tab.route) },
              )
              .testTag(tab.testTag),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Icon(
              painter = painterResource(id = tab.iconRes),
              contentDescription = stringResource(tab.labelRes),
              tint = if (selected) SoftLavender else LightGrayText.copy(alpha = 0.72f),
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = stringResource(tab.labelRes),
              color = if (selected) CrispWhite else LightGrayText.copy(alpha = 0.75f),
              fontFamily = ArabicFontRegistry.CairoFontFamily,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              fontSize = 11.sp,
              maxLines = 1,
            )
          }
        }
      }
    }
  }
}
