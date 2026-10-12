package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.AppViewModelProvider
import com.saubh.deskbuddy.ui.components.CenteredContent
import com.saubh.deskbuddy.ui.components.ScreenPadding
import com.saubh.deskbuddy.ui.isWideWindow

@Composable
fun MediaRoute(viewModel: MediaViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MediaScreen(
        state = state,
        isWide = isWideWindow(),
        onAction = viewModel::onAction,
        onSeek = viewModel::seek,
        onVolume = viewModel::setVolume,
        onToggleMute = viewModel::toggleMute,
        onSelectDevice = viewModel::selectDevice,
    )
}

/** Full player: artwork + titles, timeline, transport, then volume and output device. Side by side on wide windows. */
@Composable
fun MediaScreen(
    state: MediaUiState,
    isWide: Boolean,
    onAction: (MediaAction) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val controls: @Composable ColumnScope.() -> Unit = {
        Timeline(state, onSeek, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        Transport(state, onAction, Modifier.fillMaxWidth())
        VolumePanel(state, onVolume, onToggleMute, { showDevices = true }, MaterialTheme.colorScheme.surfaceContainerLow, Modifier.fillMaxWidth())
    }
    if (isWide) {
        Row(Modifier.fillMaxSize().padding(ScreenPadding), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NowPlayingCard(state, Modifier.weight(1.2f), scrollable = true)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = controls,
            )
        }
    } else {
        CenteredContent(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NowPlayingCard(state, Modifier.fillMaxWidth())
                controls()
            }
        }
    }
    if (showDevices) {
        OutputDeviceDialog(state.audio?.devices.orEmpty(), onSelectDevice) { showDevices = false }
    }
}
