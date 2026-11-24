package com.xperience.player

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xperience.player.ui.adaptive.ProvideAppWindowSizeClass
import com.xperience.player.ui.home.HomeScreen
import com.xperience.player.ui.home.HomeViewModel
import com.xperience.player.ui.splash.AnimatedSplashScreen
import com.xperience.player.ui.theme.DarkThemeColors
import com.xperience.player.ui.theme.LightThemeColors
import com.xperience.player.ui.theme.ThemeMode
import com.xperience.player.ui.theme.XperiencePlayerTheme
import com.xperience.player.widget.WidgetDeepLink
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

  @Volatile
  private var isComposeFirstFrameReady = false

  private var pendingWidgetDeepLink by mutableStateOf<WidgetDeepLink?>(null)

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    WidgetDeepLink.parse(intent)?.let { deepLink ->
      pendingWidgetDeepLink = deepLink
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    super.onCreate(savedInstanceState)

    if (savedInstanceState == null) {
      pendingWidgetDeepLink = WidgetDeepLink.parse(intent)
    }

    val isColdLaunch = savedInstanceState == null
    var splashHandoffReady by mutableStateOf(!isColdLaunch)
    var systemSplashIconSizePx by mutableStateOf<Int?>(null)
    var showAnimatedSplash by mutableStateOf(isColdLaunch)
    // Defer heavy HomeScreen composition until after frame 1 so core-splashscreen hands off immediately
    var mountHomeScreen by mutableStateOf(!isColdLaunch)

    if (isColdLaunch) {
      splashScreen.setKeepOnScreenCondition { !isComposeFirstFrameReady }
      splashScreen.setOnExitAnimationListener { splashProvider ->
        val measuredIconPx = runCatching {
          minOf(splashProvider.iconView.width, splashProvider.iconView.height).takeIf { it > 0 }
        }.getOrNull()
        if (measuredIconPx != null) {
          systemSplashIconSizePx = measuredIconPx
        }
        splashHandoffReady = true
        splashProvider.view.animate()
          .alpha(0f)
          .setDuration(200L)
          .setInterpolator(DecelerateInterpolator())
          .setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
              splashProvider.remove()
            }
          })
          .start()
      }
    }

    val isSystemDarkAtLaunch =
      (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    applyTransparentEdgeToEdge(isDark = isSystemDarkAtLaunch)

    setContent {
      val homeViewModel: HomeViewModel = viewModel()
      val themeMode by homeViewModel.themeMode.collectAsState()
      val isInitialHomeReady by homeViewModel.isInitialHomeReady.collectAsState()
      val isSystemDark = isSystemInDarkTheme()
      val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
      }
      val appColors = if (isDarkTheme) DarkThemeColors else LightThemeColors

      // Request notification permission for SystemUI Media Controls on Android 13+ once splash exits
      val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
      ) { isGranted ->
        if (isGranted && homeViewModel.playbackState.value.isPlaying) {
          homeViewModel.audioPlayerService.ensureServiceStarted()
        }
      }

      LaunchedEffect(pendingWidgetDeepLink, mountHomeScreen) {
        val deepLink = pendingWidgetDeepLink
        if (deepLink != null && mountHomeScreen) {
          pendingWidgetDeepLink = null
          WidgetDeepLink.clearFromIntent(intent)
          homeViewModel.handleWidgetDeepLink(deepLink)
        }
      }

      LaunchedEffect(showAnimatedSplash) {
        if (showAnimatedSplash && !splashHandoffReady) {
          delay(220L)
          splashHandoffReady = true
        }
        if (!showAnimatedSplash) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
              this@MainActivity,
              Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
              notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
          }
          homeViewModel.performStartupUpdateCheckIfEnabled()
        }
      }

      LaunchedEffect(splashHandoffReady) {
        if (splashHandoffReady && !mountHomeScreen) {
          // Allow the OS splash fade + initial stroke trace to run smoothly before composing HomeScreen
          delay(240L)
          mountHomeScreen = true
        }
      }

      val view = LocalView.current
      if (!view.isInEditMode) {
        DisposableEffect(isDarkTheme, view) {
          applyTransparentEdgeToEdge(isDark = isDarkTheme)
          WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
          }
          onDispose {}
        }
      }

      XperiencePlayerTheme(darkTheme = isDarkTheme) {
        ProvideAppWindowSizeClass {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = appColors.background
          ) {
            Box(modifier = Modifier.fillMaxSize()) {
              if (mountHomeScreen) {
                HomeScreen(viewModel = homeViewModel)
              }

              if (showAnimatedSplash) {
                AnimatedSplashScreen(
                  isHomeReady = isInitialHomeReady,
                  isDarkTheme = isDarkTheme,
                  systemSplashIconSizePx = systemSplashIconSizePx,
                  splashHandoffReady = splashHandoffReady,
                  onFirstFrameDrawn = { isComposeFirstFrameReady = true },
                  onSplashFinished = { showAnimatedSplash = false }
                )
              }
            }
          }
        }
      }
    }
  }

  private fun applyTransparentEdgeToEdge(isDark: Boolean) {
    val barStyle = if (isDark) {
      SystemBarStyle.dark(AndroidColor.TRANSPARENT)
    } else {
      SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    }
    enableEdgeToEdge(
      statusBarStyle = barStyle,
      navigationBarStyle = barStyle
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isStatusBarContrastEnforced = false
      window.isNavigationBarContrastEnforced = false
    }
    @Suppress("DEPRECATION")
    run {
      window.statusBarColor = AndroidColor.TRANSPARENT
      window.navigationBarColor = AndroidColor.TRANSPARENT
    }
  }
}
