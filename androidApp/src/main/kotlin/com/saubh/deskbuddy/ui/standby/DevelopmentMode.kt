package com.saubh.deskbuddy.ui.standby

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

private const val RUNNING_REFRESH_MS = 10_000L

@Composable
fun DevelopmentMode(state: StandbyUiState, actions: StandbyActions, wide: Boolean, modifier: Modifier = Modifier) {
    RefreshRunningWhileVisible(actions)
    AppGrid(state.shortcuts, state.shortcutIcons, state.runningShortcutIds, actions::onActivateApp, modifier, minKey = if (wide) 128.dp else 104.dp)
}

/**
 * Running dots stay fresh only while a page that shows them is on screen and the app is resumed:
 * each refresh makes the PC walk UI Automation, so a stopped or backgrounded app must not keep asking.
 */
@Composable
fun RefreshRunningWhileVisible(actions: StandbyActions) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(actions, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                actions.onRefreshRunning()
                delay(RUNNING_REFRESH_MS)
            }
        }
    }
}
