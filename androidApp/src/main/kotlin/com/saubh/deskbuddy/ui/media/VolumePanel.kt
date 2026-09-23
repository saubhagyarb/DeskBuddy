@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Mute toggle + volume slider + output device split button. Slider disabled until the PC reports audio state. */
@Composable
fun VolumePanel(
    state: MediaUiState,
    onVolume: (percent: Int, final: Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onPickDevice: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val audio = state.audio
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.toInt() else audio?.volume ?: 0
    val muteCd = stringResource(R.string.cd_mute)
    val volumeCd = stringResource(R.string.cd_volume)
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = containerColor), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconToggleButton(
                    checked = audio?.muted == true,
                    onCheckedChange = { onToggleMute() },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    enabled = audio != null,
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()).semantics { contentDescription = muteCd },
                ) {
                    Icon(
                        if (audio?.muted == true) DeskBuddyIcons.VolumeOff else DeskBuddyIcons.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
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
                    modifier = Modifier.weight(1f).semantics { contentDescription = volumeCd },
                )
                Text(
                    stringResource(R.string.media_volume_percent, shown),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    modifier = Modifier.width(44.dp),
                )
            }
            if (audio != null) {
                val chooseCd = stringResource(R.string.cd_choose_output)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.TonalLeadingButton(onClick = onPickDevice, modifier = Modifier.widthIn(max = 260.dp)) {
                                Icon(DeskBuddyIcons.Speaker, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.currentDevice?.name ?: stringResource(R.string.media_output_device),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TonalTrailingButton(
                                checked = false,
                                onCheckedChange = { onPickDevice() },
                                modifier = Modifier.semantics { contentDescription = chooseCd },
                            ) { Icon(DeskBuddyIcons.ExpandMore, contentDescription = null) }
                        },
                    )
                }
            }
        }
    }
}
