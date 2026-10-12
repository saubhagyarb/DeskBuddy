package com.saubh.deskbuddy.ui.standby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.ui.theme.KonnektIcons

private val SHORT_PAGE = 260.dp

/** Half media, half app keys: the default when docked with a PC. Portrait: compact media on top, keys below. */
@Composable
fun MediaAppsMode(state: StandbyUiState, actions: StandbyActions, wide: Boolean, modifier: Modifier = Modifier) {
    RefreshRunningWhileVisible(actions)
    val apps: @Composable (Modifier) -> Unit = { m ->
        AppGrid(state.shortcuts, state.shortcutIcons, state.runningShortcutIds, actions::onActivateApp, m, minKey = 96.dp)
    }
    if (wide) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            // Short windows (e.g. 800×360dp) tighten the media column so the output chip still fits without scrolling.
            BoxWithConstraints(Modifier.weight(1f)) {
                val short = maxHeight < SHORT_PAGE
                CenteredScrollColumn(Modifier.fillMaxSize(), gap = if (short) 4.dp else 8.dp) {
                    CompactMedia(state, actions, artworkSize = if (short) 48.dp else 64.dp, gap = if (short) 4.dp else 8.dp)
                }
            }
            apps(Modifier.weight(1f))
        }
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CompactMedia(state, actions, artworkSize = 72.dp, gap = 8.dp)
            }
            apps(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** Compact now-playing, then the current output as a chip that expands into every output. */
@Composable
private fun ColumnScope.CompactMedia(state: StandbyUiState, actions: StandbyActions, artworkSize: Dp, gap: Dp) {
    var devicesOpen by rememberSaveable { mutableStateOf(false) }
    val audio = state.media.audio
    StandbyNowPlaying(state.media, actions, artworkSize = artworkSize, compact = true, compactGap = gap)
    val current = state.media.currentDevice
    if (current != null && audio != null) {
        AssistChip(
            onClick = { devicesOpen = !devicesOpen },
            label = { Text(current.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(KonnektIcons.Speaker, contentDescription = null) },
            trailingIcon = { Icon(KonnektIcons.ExpandMore, contentDescription = null) },
        )
        AnimatedVisibility(devicesOpen, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            OutputDeviceChips(audio.devices, onSelect = { actions.onSelectDevice(it); devicesOpen = false })
        }
    }
}
