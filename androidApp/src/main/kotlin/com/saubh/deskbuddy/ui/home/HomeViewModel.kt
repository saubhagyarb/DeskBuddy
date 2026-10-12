package com.saubh.deskbuddy.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.apps.AppShortcutsController
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.power.PowerController
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.session.RemoteSession
import com.saubh.deskbuddy.share.ClipboardShareController
import com.saubh.deskbuddy.share.FileTransferController
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Dashboard state and its quick actions. */
class HomeViewModel(
    session: RemoteSession,
    private val media: MediaController,
    private val apps: AppShortcutsController,
    private val share: ClipboardShareController,
    private val files: FileTransferController,
    private val power: PowerController,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(session.uiState, session.savedDesktops, media.state, apps.state, files.progress) { link, saved, media, apps, transfer ->
            HomeUiState(
                pcName = (link as? ConnectionUiState.Connected)?.desktopName ?: saved.firstOrNull()?.name.orEmpty(),
                reconnectAttempt = (link as? ConnectionUiState.Reconnecting)?.attempt,
                media = media,
                shortcuts = apps.shortcuts,
                shortcutIcons = apps.icons,
                transfer = transfer,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Lock is instant; restart and shut down start the cancellable countdown. */
    fun power(action: PowerAction) =
        if (action == PowerAction.LOCK) power.send(action) else power.startCountdown(action, uiState.value.pcName)

    fun playPause() = media.send(MediaAction.PLAY_PAUSE)

    fun sendClipboard() = share.sendClipboard()

    fun pullClipboard() = share.pullClipboard()

    fun sendFile(uri: Uri) = files.sendFile(uri)

    fun cancelTransfer() = files.cancelSend()

    fun launch(appId: String) = apps.launch(appId)
}
