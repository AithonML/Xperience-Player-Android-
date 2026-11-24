package com.xperience.player.ui.ambient

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Unwraps nested [ContextWrapper] chains safely to locate the hosting [Activity],
 * returning `null` in non-Activity contexts (such as Compose Preview).
 */
internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * Manages full-screen landscape Ambient Mode window chrome:
 * - Locks orientation to [ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE] while active.
 * - Hides status & navigation bars via [WindowInsetsControllerCompat] with transient swipe-to-reveal.
 * - Keeps the display awake via [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON].
 * - Re-applies immersive bars on `ON_RESUME` (e.g., after dismissing notification shade / app switch).
 * - Restores original orientation, system bar visibility, appearance, and screen-on flag cleanly in
 *   [DisposableEffect] teardown — while guarding against [Activity.isChangingConfigurations] so an
 *   Activity recreation triggered by the landscape switch itself never bounces back to portrait.
 */
@Composable
internal fun AmbientImmersiveSystemEffect(
    enabled: Boolean = true
) {
    if (!enabled) return

    val view = LocalView.current
    if (view.isInEditMode) return

    val context = LocalContext.current
    val activity = context.findHostActivity() ?: return
    val lifecycleOwner = LocalLifecycleOwner.current

    // Persist pre-Ambient window state across configuration changes (e.g., portrait -> landscape recreation).
    val savedOrientation = rememberSaveable { activity.requestedOrientation }
    val hadKeepScreenOn = rememberSaveable {
        (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
    }
    val initialInsetsSnapshot = rememberSaveable {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        intArrayOf(
            controller.systemBarsBehavior,
            if (controller.isAppearanceLightStatusBars) 1 else 0,
            if (controller.isAppearanceLightNavigationBars) 1 else 0
        )
    }

    DisposableEffect(activity, view, lifecycleOwner) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, view)

        fun applyImmersiveChrome() {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveChrome()

        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyImmersiveChrome()
            }
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(resumeObserver)

            // Do not revert orientation or window chrome during a config-change recreation
            // (such as entering landscape when MainActivity recreates), preventing oscillation loops.
            if (!activity.isChangingConfigurations) {
                runCatching {
                    if (!hadKeepScreenOn) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    val restoreController = WindowCompat.getInsetsController(window, view)
                    restoreController.systemBarsBehavior = initialInsetsSnapshot[0]
                    restoreController.isAppearanceLightStatusBars = initialInsetsSnapshot[1] == 1
                    restoreController.isAppearanceLightNavigationBars = initialInsetsSnapshot[2] == 1
                    restoreController.show(WindowInsetsCompat.Type.systemBars())

                    if (activity.requestedOrientation != savedOrientation) {
                        activity.requestedOrientation = savedOrientation
                    }
                }
            }
        }
    }
}
