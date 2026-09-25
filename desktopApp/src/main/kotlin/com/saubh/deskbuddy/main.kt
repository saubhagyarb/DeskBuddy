package com.saubh.deskbuddy

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saubh.deskbuddy.server.StartupRegistration
import com.saubh.deskbuddy.ui.ServerScreen
import com.saubh.deskbuddy.ui.rememberAppIconPainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Runs in the tray so phones can keep connecting while the window is closed. Launched at
 * sign-in with [StartupRegistration.BACKGROUND_ARG] it starts hidden; closing the window
 * hides it, and only the tray's Quit exits.
 */
fun main(args: Array<String>) {
    val background = StartupRegistration.BACKGROUND_ARG in args
    // A second launch (e.g. from the Start menu while we sit in the tray) just shows the running window.
    if (SingleInstance.handOffToRunningInstance(show = !background)) return

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = DesktopController(scope).also { it.start() }

    application {
        var visible by remember { mutableStateOf(!background) }
        val windowState = rememberWindowState(width = 960.dp, height = 640.dp)
        val show = {
            visible = true
            windowState.isMinimized = false
        }
        val quit = {
            controller.stop()
            scope.cancel()
            exitApplication()
        }
        val appIcon = rememberAppIconPainter()
        LaunchedEffect(Unit) { controller.showRequests.collect { show() } }

        Tray(
            icon = appIcon,
            tooltip = "DeskBuddy",
            onAction = show,
            menu = {
                Item("Open DeskBuddy", onClick = show)
                Item("Quit", onClick = quit)
            },
        )
        Window(
            onCloseRequest = { visible = false },
            visible = visible,
            title = "DeskBuddy",
            icon = appIcon,
            state = windowState,
        ) {
            LaunchedEffect(visible) {
                if (visible) {
                    window.toFront()
                    window.requestFocus()
                }
            }
            ServerScreen(controller)
        }
    }
}
