@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.media.NowPlayingArtwork
import com.saubh.deskbuddy.ui.media.OutputDeviceDialog
import com.saubh.deskbuddy.ui.media.Timeline
import com.saubh.deskbuddy.ui.media.Transport
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons
import com.saubh.deskbuddy.ui.media.VolumePanel

/** Media page on black: big artwork, title, then the same timeline / transport / volume as the tab. */
@Composable
fun StandbyMediaPage(
    state: MediaUiState,
    isWide: Boolean,
    onMedia: (MediaAction) -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Int, Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onSelectDevice: (String) -> Unit,
) {
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val art: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NowPlayingArtwork(state.artwork, if (isWide) 140.dp else 240.dp, StandbyTile, Color.White.copy(alpha = 0.6f))
            Text(
                state.nowPlaying?.title ?: stringResource(R.string.media_nothing_playing),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOfNotNull(state.nowPlaying?.artist, state.nowPlaying?.album).joinToString(" • ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val controls: @Composable ColumnScope.() -> Unit = {
        Timeline(state, onSeek, Modifier.fillMaxWidth())
        Transport(state, onMedia, Modifier.fillMaxWidth())
        VolumePanel(state, onVolume, onToggleMute, { showDevices = true }, StandbyTile, Modifier.fillMaxWidth())
    }
    if (isWide) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Side: Artwork
            Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) {
                NowPlayingArtwork(state.artwork, 200.dp, StandbyTile, Color.White.copy(alpha = 0.6f))
            }

            // Right Side
            Column(
                Modifier.weight(1.2f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Titles
                Column {
                    Text(
                        state.nowPlaying?.title ?: stringResource(R.string.media_nothing_playing),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(state.nowPlaying?.artist, state.nowPlaying?.album).joinToString(" • ")
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Timeline
                Timeline(state, onSeek, Modifier.fillMaxWidth())

                // Controls row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Transport(state, onMedia)
                    
                    Spacer(Modifier.width(8.dp))
                    
                    // Compact volume and device
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconToggleButton(
                            checked = state.audio?.muted == true,
                            onCheckedChange = { onToggleMute() },
                            enabled = state.audio != null,
                        ) {
                            Icon(if (state.audio?.muted == true) DeskBuddyIcons.VolumeOff else DeskBuddyIcons.VolumeUp, null)
                        }
                        
                        var dragging by remember { mutableStateOf(false) }
                        var dragValue by remember { mutableFloatStateOf(0f) }
                        val audio = state.audio
                        Slider(
                            value = if (dragging) dragValue else (audio?.volume ?: 0).toFloat(),
                            onValueChange = {
                                dragging = true
                                dragValue = it
                                onVolume(it.toInt(), false)
                            },
                            onValueChangeFinished = {
                                dragging = false
                                onVolume(dragValue.toInt(), true)
                            },
                            valueRange = 0f..100f,
                            enabled = audio != null,
                            modifier = Modifier.weight(1f)
                        )
                        
                        if (audio != null) {
                            FilledTonalIconButton(onClick = { showDevices = true }) {
                                Icon(DeskBuddyIcons.Speaker, null)
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            art()
            controls()
        }
    }
    if (showDevices) OutputDeviceDialog(state.audio?.devices.orEmpty(), onSelectDevice) { showDevices = false }
}
