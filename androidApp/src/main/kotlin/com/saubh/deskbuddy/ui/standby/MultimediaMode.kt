package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R

/** Now playing on one side; volume steps and output chips on the other (stacked in portrait). */
@Composable
fun MultimediaMode(state: StandbyUiState, actions: StandbyActions, wide: Boolean, modifier: Modifier = Modifier) {
    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxWithConstraints(Modifier.weight(1f)) {
                val compact = maxHeight < 420.dp
                val artwork = if (compact) (maxHeight * 0.28f).coerceIn(56.dp, 96.dp) else 168.dp
                CenteredScrollColumn(Modifier.fillMaxSize()) { StandbyNowPlaying(state.media, actions, artworkSize = artwork, compact = compact) }
            }
            CenteredScrollColumn(Modifier.weight(1f), gap = 20.dp) { SoundPanelContent(state, actions) }
        }
    } else {
        // Artwork scales with the page height, so the page fits a phone without scrolling (the column is a safety net).
        BoxWithConstraints(modifier) {
            val artwork = (maxHeight * 0.30f).coerceIn(96.dp, 220.dp)
            CenteredScrollColumn(Modifier.fillMaxSize(), gap = 16.dp) {
                StandbyNowPlaying(state.media, actions, artworkSize = artwork)
                SoundPanelContent(state, actions)
            }
        }
    }
}

@Composable
private fun SoundPanelContent(state: StandbyUiState, actions: StandbyActions) {
    val audio = state.media.audio
    val devices = audio?.devices.orEmpty()
    VolumeStepper(audio?.volume, audio?.muted == true, actions::onVolumeStep, actions::onVolumeRelease, actions::onToggleMute)
    if (devices.isNotEmpty()) {
        Text(stringResource(R.string.media_output_device), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutputDeviceChips(devices, actions::onSelectDevice)
    }
}
