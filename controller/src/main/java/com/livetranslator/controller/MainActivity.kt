package com.livetranslator.controller

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.livetranslator.controller.ui.ControllerNavHost
import com.livetranslator.controller.ui.theme.ControllerTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
    val app = application as ControllerApp
    setContent {
      ControllerTheme {
        ControllerNavHost(app = app)
      }
    }
  }
}
