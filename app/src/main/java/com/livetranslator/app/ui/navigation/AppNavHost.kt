package com.livetranslator.app.ui.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.livetranslator.app.LiveTranslatorApp
import com.livetranslator.app.R
import com.livetranslator.app.ui.screens.AboutScreen
import com.livetranslator.app.ui.screens.HistoryScreen
import com.livetranslator.app.ui.screens.HomeScreen
import com.livetranslator.app.ui.screens.OnboardingScreen
import com.livetranslator.app.ui.screens.OverlaySettingsScreen
import com.livetranslator.app.ui.screens.SettingsScreen
import com.livetranslator.app.ui.screens.SplashScreen
import com.livetranslator.app.ui.theme.ElectricViolet
import com.livetranslator.app.ui.theme.LavenderWhite
import com.livetranslator.app.ui.theme.MutedViolet
import com.livetranslator.app.ui.theme.VioletOutline
import com.livetranslator.app.ui.theme.VoidBackground
import com.livetranslator.app.ui.theme.VoidSurface
import com.livetranslator.app.ui.theme.VoidSurfaceVariant
import com.livetranslator.app.ui.viewmodel.HistoryViewModel
import com.livetranslator.app.ui.viewmodel.LiveTranslatorViewModelFactory
import com.livetranslator.app.ui.viewmodel.MainViewModel
import com.livetranslator.app.ui.viewmodel.OverlaySettingsViewModel
import com.livetranslator.app.ui.viewmodel.SettingsViewModel

private data class BottomTab(
  val route: String,
  val labelRes: Int,
  val iconRes: Int,
  val testTag: String,
)

@Composable
fun AppNavHost(app: LiveTranslatorApp) {
  val factory = LiveTranslatorViewModelFactory(app)
  val mainViewModel: MainViewModel = viewModel(factory = factory)
  val overlaySettingsViewModel: OverlaySettingsViewModel = viewModel(factory = factory)
  val historyViewModel: HistoryViewModel = viewModel(factory = factory)
  val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

  val settings by mainViewModel.settings.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = backStackEntry?.destination?.route
  val focusManager = LocalFocusManager.current
  val textToolbar = LocalTextToolbar.current

  LaunchedEffect(currentRoute) {
    focusManager.clearFocus(force = true)
    textToolbar.hide()
  }

  val tabs =
    listOf(
      BottomTab(Routes.HOME, R.string.nav_home, R.drawable.ic_translate, "nav_home"),
      BottomTab(
        Routes.OVERLAY_SETTINGS,
        R.string.nav_overlay,
        R.drawable.ic_bubble,
        "nav_overlay",
      ),
      BottomTab(Routes.HISTORY, R.string.nav_history, R.drawable.ic_history, "nav_history"),
      BottomTab(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings, "nav_settings"),
    )

  val showBottomBar = currentRoute in tabs.map { it.route }

  Scaffold(
    containerColor = VoidBackground,
    contentWindowInsets = WindowInsets.statusBars,
    bottomBar = {
      if (showBottomBar) {
        NavigationBar(
          containerColor = VoidSurface,
          contentColor = LavenderWhite,
          windowInsets = WindowInsets.navigationBars,
          modifier = Modifier.border(1.dp, VioletOutline),
        ) {
          tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
              selected = selected,
              onClick = {
                navController.navigate(tab.route) {
                  popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              modifier = Modifier.testTag(tab.testTag),
              icon = {
                Icon(
                  painter = painterResource(id = tab.iconRes),
                  contentDescription = stringResource(tab.labelRes),
                  modifier = Modifier.size(22.dp),
                )
              },
              label = { Text(text = stringResource(tab.labelRes)) },
              colors =
                NavigationBarItemDefaults.colors(
                  selectedIconColor = LavenderWhite,
                  selectedTextColor = LavenderWhite,
                  indicatorColor = ElectricViolet.copy(alpha = 0.28f),
                  unselectedIconColor = MutedViolet,
                  unselectedTextColor = MutedViolet,
                ),
            )
          }
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
            onNavigateToSettings = {
              navController.navigate(Routes.SETTINGS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
              }
            },
            onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
          )
        }

        composable(Routes.OVERLAY_SETTINGS) {
          OverlaySettingsScreen(viewModel = overlaySettingsViewModel)
        }

        composable(Routes.HISTORY) {
          HistoryScreen(viewModel = historyViewModel)
        }

        composable(Routes.SETTINGS) {
          SettingsScreen(
            viewModel = settingsViewModel,
            onNavigateToAbout = { navController.navigate(Routes.ABOUT) },
          )
        }

        composable(Routes.ABOUT) {
          AboutScreen(onBack = { navController.popBackStack() })
        }
      }
    }
  }
}
