package com.saubh.deskbuddy.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.apps.AppShortcutsController
import com.saubh.deskbuddy.apps.AppsUiState
import com.saubh.deskbuddy.apps.RunningAppsController
import com.saubh.deskbuddy.apps.RunningAppsState
import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.RunningApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AppsScreenState(
    val installed: AppsUiState = AppsUiState(),
    val running: RunningAppsState = RunningAppsState(),
)

/** Apps screen: what is open on the PC (and closing it), and what can be launched. */
class AppsViewModel(
    private val catalog: AppShortcutsController,
    private val running: RunningAppsController,
) : ViewModel() {

    val uiState: StateFlow<AppsScreenState> =
        combine(catalog.state, running.state, ::AppsScreenState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsScreenState())

    fun launch(appId: String) = catalog.launch(appId)

    fun setPinned(appId: String, pinned: Boolean) = if (pinned) catalog.pin(appId) else catalog.unpin(appId)

    fun refreshInstalled() = catalog.refresh()

    fun refreshRunning() = running.refresh()

    fun closeApp(app: RunningApp) = running.closeApp(app)

    fun closeWindow(window: AppWindow) = running.closeWindow(window)

    fun closeTab(window: AppWindow, tab: BrowserTab) = running.closeTab(window, tab)
}
