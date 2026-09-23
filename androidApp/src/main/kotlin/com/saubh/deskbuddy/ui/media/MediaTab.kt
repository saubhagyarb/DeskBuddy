package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.FabClearance

/** Now playing card + timeline + transport + volume/output. Landscape puts the card beside the controls. */
@Composable
fun MediaTab(
    state: MediaUiState,
    isWide: Boolean,
    onMedia: (MediaAction) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val controls: @Composable ColumnScope.() -> Unit = {
        Timeline(state, onSeek, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        Transport(state, onMedia, Modifier.fillMaxWidth())
        VolumePanel(state, onVolume, onToggleMute, { showDevices = true }, scheme.surfaceContainerLow, Modifier.fillMaxWidth())
        Spacer(Modifier.height(FabClearance))
    }
    if (isWide) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NowPlayingCard(state, Modifier.weight(1.2f), scrollable = true)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = controls,
            )
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NowPlayingCard(state, Modifier.fillMaxWidth())
            controls()
        }
    }
    if (showDevices) {
        OutputDeviceSheet(state.audio?.devices.orEmpty(), onSelectDevice) { showDevices = false }
    }
}
