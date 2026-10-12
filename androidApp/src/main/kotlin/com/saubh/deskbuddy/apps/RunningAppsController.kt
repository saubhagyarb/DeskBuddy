package com.saubh.deskbuddy.apps

import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.CloseAppCommand
import com.saubh.deskbuddy.protocol.CloseTabCommand
import com.saubh.deskbuddy.protocol.CloseWindowCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.protocol.RunningAppsCommand
import com.saubh.deskbuddy.protocol.RunningAppsRequestCommand
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Mirrors the PC's open windows and browser tabs and asks them to close. Closes are applied to the
 * list at once (optimistically) and the PC is re-listed shortly after to show what really closed:
 * an app may ask to save first, so a window can survive its close request.
 */
class RunningAppsController(
    private val gateway: MediaGateway,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private companion object {
        /** Time for the PC to act on a close before re-listing. */
        const val RELIST_DELAY_MS = 1_200L

        /** Listing can take seconds (UI Automation); give up on the spinner after this. */
        const val LOADING_TIMEOUT_MS = 15_000L
    }

    private val _state = MutableStateFlow(RunningAppsState())
    val state: StateFlow<RunningAppsState> = _state.asStateFlow()

    private var relistJob: Job? = null
    private var loadingTimeout: Job? = null

    init {
        scope.launch {
            gateway.incoming.filterIsInstance<Push>().collect { push -> onPush(push.command) }
        }
        scope.launch { gateway.connected.collect { if (!it) _state.value = RunningAppsState() } }
    }

    fun refresh() {
        _state.update { it.copy(loading = true) }
        loadingTimeout?.cancel()
        loadingTimeout = scope.launch {
            delay(LOADING_TIMEOUT_MS)
            _state.update { it.copy(loading = false) }
        }
        scope.launch { if (!gateway.send(RunningAppsRequestCommand)) _state.update { it.copy(loading = false) } }
    }

    fun closeApp(app: RunningApp) {
        _state.update { s -> s.copy(apps = s.apps.filterNot { it.id == app.id }) }
        sendThenRelist(CloseAppCommand(app.id))
    }

    fun closeWindow(window: AppWindow) {
        _state.update { s -> s.copy(apps = s.apps.withoutWindow(window.id)) }
        sendThenRelist(CloseWindowCommand(window.id))
    }

    fun closeTab(window: AppWindow, tab: BrowserTab) {
        _state.update { s -> s.copy(apps = s.apps.withoutTab(window.id, tab.index)) }
        sendThenRelist(CloseTabCommand(window.id, tab.index, tab.title))
    }

    private fun sendThenRelist(command: Command) {
        scope.launch { gateway.send(command) }
        relistJob?.cancel()
        relistJob = scope.launch {
            delay(RELIST_DELAY_MS)
            refresh()
        }
    }

    private fun onPush(command: Command) {
        if (command !is RunningAppsCommand) return
        loadingTimeout?.cancel()
        _state.value = RunningAppsState(apps = command.apps, loading = false, updatedAtMs = clock())
    }
}

/** Removes the window, and its app when that was the app's last window. */
private fun List<RunningApp>.withoutWindow(windowId: String): List<RunningApp> = mapNotNull { app ->
    val windows = app.windows.filterNot { it.id == windowId }
    if (windows.isEmpty()) null else app.copy(windows = windows)
}

/** Removes the tab and renumbers the rest, as the browser's tab strip will. */
private fun List<RunningApp>.withoutTab(windowId: String, index: Int): List<RunningApp> = map { app ->
    app.copy(
        windows = app.windows.map { window ->
            if (window.id != windowId) {
                window
            } else {
                window.copy(tabs = window.tabs.filterNot { it.index == index }.mapIndexed { i, tab -> tab.copy(index = i) })
            }
        },
    )
}
