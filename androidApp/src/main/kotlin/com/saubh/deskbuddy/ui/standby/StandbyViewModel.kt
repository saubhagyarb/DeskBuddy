package com.saubh.deskbuddy.ui.standby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saubh.deskbuddy.apps.AppsUiState
import com.saubh.deskbuddy.apps.RunningAppsController
import com.saubh.deskbuddy.media.MediaController
import com.saubh.deskbuddy.power.PowerController
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.ConnectionUiState
import com.saubh.deskbuddy.ui.standby.clock.ClockFace
import com.saubh.deskbuddy.ui.standby.clock.ClockFaceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Standby's mode (via [StandbyModePolicy]) plus everything its four pages show; works with or without a PC. */
class StandbyViewModel(
    private val connection: StateFlow<ConnectionUiState>,
    savedDesktops: StateFlow<List<SavedDesktop>>,
    private val media: MediaController,
    apps: StateFlow<AppsUiState>,
    private val activateApp: (String) -> Unit,
    private val running: RunningAppsController,
    private val power: PowerController,
    private val faces: ClockFaceStore,
) : ViewModel(), StandbyActions {

    private data class Link(val connected: Boolean, val pcName: String, val reconnectAttempt: Int?)
    private data class Header(val modeState: StandbyModeState, val entered: Boolean, val face: ClockFace, val link: Link)

    private val modeState = MutableStateFlow(StandbyModeState())
    private val entered = MutableStateFlow(false)

    private val link = combine(connection, savedDesktops) { state, saved ->
        Link(
            connected = state is ConnectionUiState.Connected,
            pcName = (state as? ConnectionUiState.Connected)?.desktopName ?: saved.maxByOrNull { it.lastUsedAt }?.name.orEmpty(),
            reconnectAttempt = (state as? ConnectionUiState.Reconnecting)?.attempt,
        )
    }

    val uiState: StateFlow<StandbyUiState> =
        combine(
            combine(modeState, entered, faces.face, link, ::Header),
            media.state,
            apps,
            running.state,
        ) { header, mediaState, appsState, runningState ->
            StandbyUiState(
                entered = header.entered,
                mode = header.modeState.mode,
                clockFace = header.face,
                connected = header.link.connected,
                pcName = header.link.pcName,
                reconnectAttempt = header.link.reconnectAttempt,
                media = mediaState,
                shortcuts = appsState.shortcuts,
                shortcutIcons = appsState.icons,
                runningShortcutIds = runningState.apps.mapNotNull { it.shortcutId }.toSet(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StandbyUiState())

    init {
        viewModelScope.launch {
            connection.map { it is ConnectionUiState.Connected }.distinctUntilChanged().drop(1).collect { connected ->
                modeState.update { StandbyModePolicy.onConnectionChanged(it, connected) }
            }
        }
    }

    override fun onEnter() {
        if (entered.value) return // a rotation re-enters; the user's pick sticks until standby really exits
        modeState.value = StandbyModePolicy.onEnter(connection.value is ConnectionUiState.Connected)
        entered.value = true
    }

    override fun onLeave() {
        entered.value = false
    }

    override fun onSelectMode(mode: StandbyMode) = modeState.update { StandbyModePolicy.onUserSelect(it, mode) }

    override fun onSelectFace(face: ClockFace) {
        viewModelScope.launch { faces.save(face) }
    }

    override fun onMedia(action: MediaAction) = media.send(action)

    override fun onSeek(positionMs: Long) = media.seek(positionMs)

    /** With a known volume, steps are absolute (and clamp); before the PC reports audio, they are media keys. */
    override fun onVolumeStep(delta: Int) {
        val audio = media.state.value.audio
        if (audio == null) {
            media.send(if (delta > 0) MediaAction.VOLUME_UP else MediaAction.VOLUME_DOWN)
        } else {
            media.setVolume(audio.volume + delta, final = false)
        }
    }

    override fun onVolumeRelease() {
        media.state.value.audio?.let { media.setVolume(it.volume, final = true) }
    }

    override fun onToggleMute() = media.toggleMute()

    override fun onSelectDevice(id: String) = media.selectDevice(id)

    override fun onActivateApp(appId: String) = activateApp(appId)

    override fun onLockPc() = power.send(PowerAction.LOCK)

    override fun onRefreshRunning() = running.refresh()

    /** Restart and shut down run the cancellable countdown, named after the PC this link is on right now. */
    override fun onPower(action: PowerAction) {
        val pcName = (connection.value as? ConnectionUiState.Connected)?.desktopName ?: uiState.value.pcName
        power.startCountdown(action, pcName)
    }
}
