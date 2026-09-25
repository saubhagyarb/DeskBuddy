package com.saubh.deskbuddy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.appGraph
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.protocol.PowerCommand
import com.saubh.deskbuddy.share.PendingShare
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Thin facade over the app-wide session and feature controllers; UI talks only to this. */
class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = app.appGraph
    private val session = graph.session

    val share = graph.share
    val files = graph.files
    val apps = graph.apps
    val media = graph.media
    val autoStandby = graph.autoStandby

    val uiState: StateFlow<ConnectionUiState> = session.uiState
    val errors: SharedFlow<ErrorKind> = session.errors
    val savedDesktops: StateFlow<List<SavedDesktop>> = session.savedDesktops

    private val pendingShares = ArrayDeque<PendingShare>()

    init {
        viewModelScope.launch {
            uiState.collect { if (it is ConnectionUiState.Connected) flushPendingShares() }
        }
    }

    fun startDiscovery() = session.startDiscovery()

    /** The connect screen is showing: allow the subnet sweep and try paired PCs right away. */
    fun setConnectScreenVisible(visible: Boolean) {
        session.sweepAllowed = visible
        if (visible) graph.autoConnector.nudge()
    }

    fun connect(name: String, host: String, port: Int) = session.connect(name, host, port)

    fun reconnect(desktop: SavedDesktop) = session.connect(desktop, quiet = false)

    fun forget(desktop: SavedDesktop) = session.forget(desktop)

    fun submitPin(pin: String) = session.submitPin(pin)

    fun disconnect() = session.disconnect()

    fun sendMedia(action: MediaAction) {
        viewModelScope.launch { session.send(MediaCommand(action)) }
    }

    fun sendPower(action: PowerAction) {
        viewModelScope.launch { session.send(PowerCommand(action)) }
    }

    /** Content from the system share sheet. Sent now if connected, otherwise once connected. */
    fun share(pending: PendingShare) {
        viewModelScope.launch {
            val stored = when (pending) {
                is PendingShare.Text -> pending
                is PendingShare.File -> files.stashForLater(pending.uri)?.let { PendingShare.File(it) }
            }
            if (stored == null) {
                session.reportError(ErrorKind.FILE_UNREADABLE)
                return@launch
            }
            if (session.isConnected) dispatch(stored) else pendingShares.addLast(stored)
        }
    }

    private fun flushPendingShares() {
        while (pendingShares.isNotEmpty()) dispatch(pendingShares.removeFirst())
    }

    private fun dispatch(pending: PendingShare) {
        when (pending) {
            is PendingShare.Text -> share.sendText(pending.text)
            is PendingShare.File -> files.sendFile(pending.uri)
        }
    }
}
