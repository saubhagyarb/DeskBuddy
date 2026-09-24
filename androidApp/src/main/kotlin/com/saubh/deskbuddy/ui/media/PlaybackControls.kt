@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.media

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.media.MediaUiState
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Seek slider with elapsed / total labels. Local value while dragging; seeks on release. */
@Composable
fun Timeline(state: MediaUiState, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    val duration = state.durationMs
    val range = 0f..maxOf(duration.toFloat(), 1f)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragValue.toLong() else state.positionMs
    val seekCd = stringResource(R.string.cd_seek)
    Column(modifier) {
        Slider(
            value = (if (dragging) dragValue else state.positionMs.toFloat()).coerceIn(range),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeek(dragValue.toLong())
            },
            valueRange = range,
            enabled = state.canSeek,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = seekCd },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = MaterialTheme.typography.labelMedium
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(formatClock(if (duration > 0) shown else -1), style = style, color = color)
            Text(formatClock(if (duration > 0) duration else -1), style = style, color = color)
        }
    }
}

/** Previous / play-pause (hero) / next as a connected expressive button group. */
@Composable
fun Transport(state: MediaUiState, onMedia: (MediaAction) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        ButtonGroup(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val prev = remember { MutableInteractionSource() }
            val play = remember { MutableInteractionSource() }
            val next = remember { MutableInteractionSource() }
            TonalControl(DeskBuddyIcons.SkipPrevious, R.string.cd_previous, prev, Modifier.animateWidth(prev)) {
                onMedia(MediaAction.PREVIOUS)
            }
            HeroControl(
                if (state.isPlaying) DeskBuddyIcons.Pause else DeskBuddyIcons.Play,
                R.string.cd_play_pause,
                play,
                Modifier.animateWidth(play),
            ) { onMedia(MediaAction.PLAY_PAUSE) }
            TonalControl(DeskBuddyIcons.SkipNext, R.string.cd_next, next, Modifier.animateWidth(next)) {
                onMedia(MediaAction.NEXT)
            }
        }
    }
}

@Composable
private fun HeroControl(icon: ImageVector, cdRes: Int, interaction: MutableInteractionSource, modifier: Modifier, onClick: () -> Unit) {
    val cd = stringResource(cdRes)
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = modifier.size(IconButtonDefaults.mediumContainerSize()).semantics { contentDescription = cd },
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.mediumIconSize)) }
}

@Composable
private fun TonalControl(icon: ImageVector, cdRes: Int, interaction: MutableInteractionSource, modifier: Modifier, onClick: () -> Unit) {
    val cd = stringResource(cdRes)
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        interactionSource = interaction,
        modifier = modifier.size(IconButtonDefaults.smallContainerSize()).semantics { contentDescription = cd },
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(IconButtonDefaults.smallIconSize)) }
}
