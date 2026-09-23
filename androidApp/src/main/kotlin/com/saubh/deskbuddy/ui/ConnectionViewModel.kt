package com.saubh.deskbuddy.ui

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.apps.AppShortcutsController
import com.saubh.deskbuddy.client.DesktopDiscovery
import com.saubh.deskbuddy.client.SubnetScanner
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.prefs.PairedDesktopPrefs
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.share.ClipboardShareController
import com.saubh.deskbuddy.share.FileTransferController
import com.saubh.deskbuddy.share.PendingShare
import com.saubh.deskbuddy.share.ReceivedFileStore
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Thin facade over the session and the feature controllers; UI talks only to this. */
class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val session = RemoteSession(
        scope = viewModelScope,
        prefs = PairedDesktopPrefs(app),
        discovery = DesktopDiscovery(app),
        scanner = SubnetScanner(app),
        deviceName = Build.MODEL,
    )

    val share = ClipboardShareController(
        session = session,
        clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager,
        scope = viewModelScope,
    )

    val files = FileTransferController(
        session = session,
        resolver = app.contentResolver,
        store = ReceivedFileStore(app),
        cacheDir = app.cacheDir,
        scope = viewModelScope,
        onReceived = share::addToInbox,
    )

    val apps = AppShortcutsController(session, viewModelScope)

    val media = MediaController(session, viewModelScope)

    val uiState: StateFlow<ConnectionUiState> = session.uiState
    val errors: SharedFlow<ErrorKind> = session.errors
    val savedDesktop: StateFlow<SavedDesktop?> = session.savedDesktop

    private val pendingShares = ArrayDeque<PendingShare>()

    init {
        viewModelScope.launch {
            uiState.collect { if (it is ConnectionUiState.Connected) flushPendingShares() }
        }
    }

    fun startDiscovery() = session.startDiscovery()

    fun connect(name: String, host: String, port: Int, token: String?) =
        session.connect(name, host, port, token)

    fun reconnectSaved() = session.reconnectSaved()

    fun submitPin(pin: String) = session.submitPin(pin)

    fun disconnect() = session.disconnect()

    fun sendMedia(action: MediaAction) {
        viewModelScope.launch { session.send(MediaCommand(action)) }
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

    override fun onCleared() = share.release()
}
