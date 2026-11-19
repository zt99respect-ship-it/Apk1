package com.livetranslator.controller.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseUser
import com.livetranslator.controller.ControllerApp
import com.livetranslator.controller.R
import com.livetranslator.controller.data.authStateFlow
import com.livetranslator.controller.ui.screens.AdminDeniedScreen
import com.livetranslator.controller.ui.screens.AdminVerifyingScreen
import com.livetranslator.controller.ui.screens.ControllerGoogleAuthScreen
import com.livetranslator.controller.ui.screens.HotfixPublisherScreen
import com.livetranslator.controller.ui.screens.NotificationsSenderScreen
import com.livetranslator.controller.ui.screens.OperatorCredentialLockScreen
import com.livetranslator.controller.ui.screens.RemoteControlScreen
import com.livetranslator.controller.ui.screens.UsersManagerScreen
import com.livetranslator.controller.ui.theme.CrispWhite
import com.livetranslator.controller.ui.theme.ElectricViolet
import com.livetranslator.controller.ui.theme.GlassCardFillStrong
import com.livetranslator.controller.ui.theme.LightGrayText
import com.livetranslator.controller.ui.theme.SoftLavender
import com.livetranslator.controller.ui.theme.VoidBackground
import com.livetranslator.controller.ui.theme.controllerRadialBackground
import com.livetranslator.controller.ui.viewmodel.AdminGateUiState
import com.livetranslator.controller.ui.viewmodel.ControllerAuthViewModel
import com.livetranslator.controller.ui.viewmodel.ControllerAuthViewModelFactory
import com.livetranslator.controller.ui.viewmodel.ControllerConsoleViewModel
import com.livetranslator.controller.ui.viewmodel.ControllerConsoleViewModelFactory

private object ControllerRoutes {
  const val CONTROL = "control"
  const val HOTFIX = "hotfix"
  const val NOTIFICATIONS = "notifications"
  const val USERS = "users"
}

private data class ControllerTab(
  val route: String,
  val labelRes: Int,
  val iconRes: Int,
  val testTag: String,
)

@Composable
fun ControllerNavHost(app: ControllerApp) {
  val authFlow = remember(app.auth) { app.auth.authStateFlow() }
  val currentUser by authFlow.collectAsStateWithLifecycle(initialValue = app.auth.currentUser)

  val authViewModel: ControllerAuthViewModel =
    viewModel(factory = ControllerAuthViewModelFactory(app))

  val signedInUser = currentUser
  if (signedInUser == null) {
    ControllerGoogleAuthScreen(viewModel = authViewModel)
    return
  }

  key(signedInUser.uid) {
    AuthenticatedControllerRoot(app = app, user = signedInUser)
  }
}

@Composable
private fun AuthenticatedControllerRoot(app: ControllerApp, user: FirebaseUser) {
  val context = LocalContext.current
  val consoleViewModel: ControllerConsoleViewModel =
    viewModel(
      key = "controller_console_${user.uid}",
      factory = ControllerConsoleViewModelFactory(user, app),
    )

  val gateState by consoleViewModel.gateState.collectAsStateWithLifecycle()
  val unlockError by consoleViewModel.unlockError.collectAsStateWithLifecycle()

  when (val state = gateState) {
    is AdminGateUiState.VerifyingAdmin -> AdminVerifyingScreen()

    is AdminGateUiState.Denied ->
      AdminDeniedScreen(
        message = state.message,
        onSignOut = { consoleViewModel.signOut(context) },
      )

    is AdminGateUiState.NeedsOperatorUnlock ->
      OperatorCredentialLockScreen(
        email = user.email.orEmpty(),
        unlockError = unlockError,
        onUnlock = { username, password ->
          consoleViewModel.unlockOperatorConsole(username, password)
        },
        onSignOut = { consoleViewModel.signOut(context) },
      )

    is AdminGateUiState.Ready -> {
      val navController = rememberNavController()
      val backStackEntry by navController.currentBackStackEntryAsState()
      val currentRoute = backStackEntry?.destination?.route

      val tabs =
        listOf(
          ControllerTab(ControllerRoutes.CONTROL, R.string.tab_control, R.drawable.ic_control, "tab_control"),
          ControllerTab(ControllerRoutes.HOTFIX, R.string.tab_hotfix, R.drawable.ic_hotfix, "tab_hotfix"),
          ControllerTab(ControllerRoutes.NOTIFICATIONS, R.string.tab_notifications, R.drawable.ic_bell, "tab_notifications"),
          ControllerTab(ControllerRoutes.USERS, R.string.tab_users, R.drawable.ic_users, "tab_users"),
        )

      Box(modifier = Modifier.fillMaxSize().controllerRadialBackground()) {
        Scaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets.statusBars,
          bottomBar = {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .navigationBarsPadding()
                  .padding(horizontal = 16.dp, vertical = 12.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(
                modifier =
                  Modifier.widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .height(70.dp)
                    .shadow(20.dp, RoundedCornerShape(35.dp), ambientColor = ElectricViolet.copy(alpha = 0.45f), spotColor = VoidBackground)
                    .clip(RoundedCornerShape(35.dp))
                    .background(GlassCardFillStrong)
                    .border(
                      1.dp,
                      Brush.linearGradient(
                        listOf(CrispWhite.copy(alpha = 0.25f), ElectricViolet.copy(alpha = 0.45f), CrispWhite.copy(alpha = 0.12f))
                      ),
                      RoundedCornerShape(35.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                tabs.forEach { tab ->
                  val selected = currentRoute == tab.route
                  val interaction = remember { MutableInteractionSource() }
                  Box(
                    modifier =
                      Modifier.weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(29.dp))
                        .then(
                          if (selected) {
                            Modifier.background(ElectricViolet.copy(alpha = 0.28f))
                              .border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(29.dp))
                          } else {
                            Modifier
                          }
                        )
                        .clickable(interactionSource = interaction, indication = null) {
                          consoleViewModel.clearFeedback()
                          navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                          }
                        }
                        .testTag(tab.testTag),
                    contentAlignment = Alignment.Center,
                  ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                      Icon(
                        painter = painterResource(id = tab.iconRes),
                        contentDescription = stringResource(tab.labelRes),
                        tint = if (selected) SoftLavender else LightGrayText.copy(alpha = 0.72f),
                        modifier = Modifier.size(20.dp),
                      )
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(
                        text = stringResource(tab.labelRes),
                        color = if (selected) CrispWhite else LightGrayText.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                      )
                    }
                  }
                }
              }
            }
          },
        ) { innerPadding ->
          Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(navController = navController, startDestination = ControllerRoutes.CONTROL) {
              composable(ControllerRoutes.CONTROL) {
                RemoteControlScreen(
                  viewModel = consoleViewModel,
                  onSignOut = { consoleViewModel.signOut(context) },
                )
              }
              composable(ControllerRoutes.HOTFIX) {
                HotfixPublisherScreen(viewModel = consoleViewModel)
              }
              composable(ControllerRoutes.NOTIFICATIONS) {
                NotificationsSenderScreen(viewModel = consoleViewModel)
              }
              composable(ControllerRoutes.USERS) {
                UsersManagerScreen(viewModel = consoleViewModel)
              }
            }
          }
        }
      }
    }
  }
}
