package com.saubh.deskbuddy.apps

import android.util.Base64
import com.saubh.deskbuddy.protocol.AddShortcutCommand
import com.saubh.deskbuddy.protocol.AppCatalog
import com.saubh.deskbuddy.protocol.AppCatalogRequestCommand
import com.saubh.deskbuddy.protocol.LaunchAppCommand
import com.saubh.deskbuddy.protocol.RemoveShortcutCommand
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors the desktop's app catalog and sends launch / pin / unpin commands. */
class AppShortcutsController(
    private val session: RemoteSession,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            session.incoming.filterIsInstance<AppCatalog>().collect { catalog ->
                val icons = withContext(Dispatchers.Default) {
                    catalog.icons.mapNotNull { (id, b64) ->
                        runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()?.let { id to it }
                    }.toMap()
                }
                _state.value = AppsUiState(catalog.apps, catalog.shortcutIds, icons, loaded = true)
            }
        }
        scope.launch {
            session.uiState
                .distinctUntilChangedBy { it is ConnectionUiState.Connected }
                .collect { if (it is ConnectionUiState.Connected) refresh() }
        }
    }

    fun refresh() {
        scope.launch { session.send(AppCatalogRequestCommand) }
    }

    fun launch(appId: String) {
        scope.launch { session.send(LaunchAppCommand(appId)) }
    }

    fun pin(appId: String) {
        scope.launch { session.send(AddShortcutCommand(appId)) }
    }

    fun unpin(appId: String) {
        scope.launch { session.send(RemoveShortcutCommand(appId)) }
    }
}
