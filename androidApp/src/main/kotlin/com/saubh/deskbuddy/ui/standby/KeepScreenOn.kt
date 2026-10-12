package com.saubh.deskbuddy.ui.standby

import android.os.Build
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True full screen while in composition: keeps the screen on, hides the status and navigation bars (a swipe
 * shows them transiently, with light icons for the dark standby) and lets the window use the camera cutout.
 * The hide is re-applied on configuration changes, on resume and whenever the window regains focus (a dialog,
 * popup or the shade can bring the bars back). Everything is restored on dispose.
 */
@Composable
fun KeepScreenOnImmersive() {
    val activity = LocalActivity.current ?: return
    val window = activity.window
    val controller = remember(activity) { WindowCompat.getInsetsController(window, window.decorView) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, controller, lifecycle) {
        val lightStatusBars = controller.isAppearanceLightStatusBars
        val lightNavigationBars = controller.isAppearanceLightNavigationBars
        val behavior = controller.systemBarsBehavior
        val cutoutMode = window.cutoutMode()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.useShortEdgeCutout()
        controller.hideBars()
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus -> if (hasFocus) controller.hideBars() }
        window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        val resumeObserver = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) controller.hideBars() }
        lifecycle.addObserver(resumeObserver)
        onDispose {
            lifecycle.removeObserver(resumeObserver)
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            cutoutMode?.let { window.setCutoutMode(it) }
            controller.isAppearanceLightStatusBars = lightStatusBars
            controller.isAppearanceLightNavigationBars = lightNavigationBars
            controller.systemBarsBehavior = behavior
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    // enableEdgeToEdge re-applies dark icons on configuration changes that keep the activity (multi-window, resize),
    // and a rotation or resize can bring the bars back.
    val config = LocalConfiguration.current
    LaunchedEffect(controller, config) { controller.hideBars() }
}

private fun WindowInsetsControllerCompat.hideBars() {
    isAppearanceLightStatusBars = false
    isAppearanceLightNavigationBars = false
    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    hide(WindowInsetsCompat.Type.systemBars())
}

private fun Window.cutoutMode(): Int? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) attributes.layoutInDisplayCutoutMode else null

/** Short-edge cutout use, unless the window already lays out into every cutout (enableEdgeToEdge on API 30+). */
private fun Window.useShortEdgeCutout() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val mode = attributes.layoutInDisplayCutoutMode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS) return
    setCutoutMode(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES)
}

private fun Window.setCutoutMode(mode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || attributes.layoutInDisplayCutoutMode == mode) return
    attributes = attributes.apply { layoutInDisplayCutoutMode = mode }
}
