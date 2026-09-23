package com.saubh.deskbuddy

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.saubh.deskbuddy.ui.ServerScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

fun main() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val controller = DesktopController(scope).also { it.start() }

    application {
        Window(
            onCloseRequest = {
                controller.stop()
                scope.cancel()
                exitApplication()
            },
            title = "DeskBuddy",
            state = rememberWindowState(width = 960.dp, height = 640.dp),
        ) {
            ServerScreen(controller)
        }
    }
}
