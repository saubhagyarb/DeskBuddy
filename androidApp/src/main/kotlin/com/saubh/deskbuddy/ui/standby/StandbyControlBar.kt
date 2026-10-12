package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Lock · Vol − · Play/Pause · Vol + on every PC mode. Disabled (dimmed) while the PC is away. */
@Composable
fun StandbyControlBar(
    isPlaying: Boolean,
    volume: Int?,
    enabled: Boolean,
    onLock: () -> Unit,
    onPlayPause: () -> Unit,
    onVolumeStep: (Int) -> Unit,
    onVolumeRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onLock, enabled = enabled) {
                Icon(KonnektIcons.Lock, contentDescription = stringResource(R.string.cd_lock_pc))
            }
            VolumeStepButton(KonnektIcons.VolumeDown, stringResource(R.string.cd_volume_down), -VOLUME_STEP, volume,
                onStep = onVolumeStep, onRelease = onVolumeRelease, size = 52.dp, enabled = enabled)
            FilledIconButton(onClick = onPlayPause, enabled = enabled, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (isPlaying) KonnektIcons.Pause else KonnektIcons.Play,
                    contentDescription = stringResource(if (isPlaying) R.string.cd_pause else R.string.cd_play),
                )
            }
            VolumeStepButton(KonnektIcons.VolumeUp, stringResource(R.string.cd_volume_up), VOLUME_STEP, volume,
                onStep = onVolumeStep, onRelease = onVolumeRelease, size = 52.dp, enabled = enabled)
        }
    }
}
