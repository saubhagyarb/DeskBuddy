package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.theme.KonnektIcons

const val VOLUME_STEP = 2

/**
 * A round − / + key: steps on press-down, repeats while held, ticks once per step. The stronger
 * Confirm tick fires once on reaching 0 % / 100 %; further repeats at the limit stay silent.
 */
@Composable
fun VolumeStepButton(
    icon: ImageVector,
    contentDescription: String,
    delta: Int,
    volume: Int?,
    onStep: (Int) -> Unit,
    onRelease: () -> Unit,
    size: Dp = 64.dp,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val atLimit = remember { BooleanArray(1) }
    val dim = if (enabled) 1f else 0.38f
    Box(
        Modifier
            .size(size)
            .repeatingPress(
                interaction,
                onStep = {
                    val next = volume?.let { (it + delta).coerceIn(0, 100) }
                    if (next == 0 || next == 100) {
                        if (!atLimit[0]) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        atLimit[0] = true
                    } else {
                        atLimit[0] = false
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    onStep(delta)
                },
                onRelease = {
                    atLimit[0] = false
                    onRelease()
                },
                enabled = enabled,
            )
            .pressScale(interaction)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = dim))
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = dim)) }
}

@Composable
fun VolumeStepper(
    volume: Int?,
    muted: Boolean,
    onStep: (Int) -> Unit,
    onRelease: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        VolumeStepButton(KonnektIcons.VolumeDown, stringResource(R.string.cd_volume_down), -VOLUME_STEP, volume, onStep, onRelease)
        Text(
            text = volume?.let { stringResource(R.string.media_volume_percent, it) } ?: "–",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        VolumeStepButton(KonnektIcons.VolumeUp, stringResource(R.string.cd_volume_up), VOLUME_STEP, volume, onStep, onRelease)
        FilledTonalIconToggleButton(checked = muted, onCheckedChange = { onToggleMute() }) {
            Icon(KonnektIcons.VolumeOff, contentDescription = stringResource(R.string.cd_mute))
        }
    }
}
