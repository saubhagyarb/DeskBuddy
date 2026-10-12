package com.saubh.deskbuddy.ui.apps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.components.ContentMaxWidth
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.theme.KonnektIcons

enum class AppsPane { OPEN, LAUNCH }

@Composable
fun AppsRoute(viewModel: AppsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pane by rememberSaveable { mutableStateOf(AppsPane.OPEN) }
    // Window lists go stale quickly: re-read whenever the pane is shown.
    LaunchedEffect(pane) { if (pane == AppsPane.OPEN) viewModel.refreshRunning() }
    AppsScreen(
        state = state,
        pane = pane,
        onPaneChange = { pane = it },
        onRefreshRunning = viewModel::refreshRunning,
        onCloseApp = viewModel::closeApp,
        onCloseWindow = viewModel::closeWindow,
        onCloseTab = viewModel::closeTab,
        onLaunch = viewModel::launch,
        onSetPinned = viewModel::setPinned,
        onRefreshInstalled = viewModel::refreshInstalled,
    )
}

/** "Open now" lists the PC's windows and browser tabs with close buttons; "Launch" starts apps. */
@Composable
fun AppsScreen(
    state: AppsScreenState,
    pane: AppsPane,
    onPaneChange: (AppsPane) -> Unit,
    onRefreshRunning: () -> Unit,
    onCloseApp: (RunningApp) -> Unit,
    onCloseWindow: (AppWindow) -> Unit,
    onCloseTab: (AppWindow, BrowserTab) -> Unit,
    onLaunch: (String) -> Unit,
    onSetPinned: (String, Boolean) -> Unit,
    onRefreshInstalled: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        val panes = listOf(
            AppsPane.OPEN to stringResource(R.string.apps_pane_open),
            AppsPane.LAUNCH to stringResource(R.string.apps_pane_launch),
        )
        SingleChoiceSegmentedButtonRow(
            Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp),
        ) {
            panes.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = pane == value,
                    onClick = { onPaneChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, panes.size),
                    icon = {
                        SegmentedButtonDefaults.Icon(active = pane == value) {
                            Icon(if (value == AppsPane.OPEN) KonnektIcons.Layers else KonnektIcons.Apps, contentDescription = null)
                        }
                    },
                    label = { Text(label) },
                )
            }
        }
        when (pane) {
            AppsPane.OPEN -> OpenWindowsPane(state.running, onRefreshRunning, onCloseApp, onCloseWindow, onCloseTab)
            AppsPane.LAUNCH -> LaunchPane(state.installed, onLaunch, onSetPinned, onRefreshInstalled)
        }
    }
}
