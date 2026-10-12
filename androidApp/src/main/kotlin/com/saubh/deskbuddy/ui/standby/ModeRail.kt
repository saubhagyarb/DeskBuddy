package com.saubh.deskbuddy.ui.standby

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.theme.KonnektIcons

private val DEFAULT_ITEM = 56.dp

val StandbyMode.icon: ImageVector
    get() = when (this) {
        StandbyMode.CLOCK -> KonnektIcons.Schedule
        StandbyMode.MEDIA_APPS -> KonnektIcons.Dashboard
        StandbyMode.MULTIMEDIA -> KonnektIcons.GraphicEq
        StandbyMode.DEVELOPMENT -> KonnektIcons.Code
    }

val StandbyMode.labelRes: Int
    get() = when (this) {
        StandbyMode.CLOCK -> R.string.standby_mode_clock
        StandbyMode.MEDIA_APPS -> R.string.standby_mode_media_apps
        StandbyMode.MULTIMEDIA -> R.string.standby_mode_multimedia
        StandbyMode.DEVELOPMENT -> R.string.standby_mode_development
    }

/**
 * Four mode buttons on a translucent pill (vertical in landscape, horizontal in portrait, 4 × [itemSize] + 8dp);
 * the highlight slides between them on a slightly bouncy spring, read in the placement phase.
 */
@Composable
fun ModeRail(
    current: StandbyMode,
    onSelect: (StandbyMode) -> Unit,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    itemSize: Dp = DEFAULT_ITEM,
) {
    val offset = animateDpAsState(itemSize * current.ordinal, spring(dampingRatio = 0.8f), label = "rail")
    val railCd = stringResource(R.string.cd_standby_modes)
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
            .padding(4.dp)
            .semantics { contentDescription = railCd },
    ) {
        Box(
            Modifier
                .offset {
                    val px = offset.value.roundToPx()
                    if (vertical) IntOffset(0, px) else IntOffset(px, 0)
                }
                .size(itemSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        val items: @Composable () -> Unit = {
            StandbyMode.entries.forEach { mode -> RailItem(mode, mode == current, itemSize) { onSelect(mode) } }
        }
        if (vertical) Column { items() } else Row { items() }
    }
}

@Composable
private fun RailItem(mode: StandbyMode, selected: Boolean, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(mode.labelRes)
    Box(
        Modifier
            .size(size)
            .clickable(interaction, indication = null, role = Role.Tab, onClick = onClick)
            .pressScale(interaction)
            .clip(CircleShape)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            mode.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
