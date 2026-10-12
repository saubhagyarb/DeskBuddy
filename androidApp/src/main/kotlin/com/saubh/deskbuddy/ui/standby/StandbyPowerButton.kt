package com.saubh.deskbuddy.ui.standby

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/**
 * Restart and shut down from docked standby. Both start the cancellable countdown, whose dialog shows
 * over standby. [expanded] is hoisted so the auto-hiding chrome stays up while the menu is open.
 */
@Composable
fun StandbyPowerButton(
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPower: (PowerAction) -> Unit,
) {
    Box {
        FilledTonalIconButton(onClick = { onExpandedChange(true) }, enabled = enabled) {
            Icon(KonnektIcons.Power, contentDescription = stringResource(R.string.cd_standby_power))
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { onExpandedChange(false) }) {
            PowerItem(R.string.power_restart, PowerAction.RESTART, onExpandedChange, onPower)
            PowerItem(R.string.power_shutdown, PowerAction.SHUTDOWN, onExpandedChange, onPower)
        }
    }
}

@Composable
private fun PowerItem(@StringRes label: Int, action: PowerAction, onExpandedChange: (Boolean) -> Unit, onPower: (PowerAction) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        onClick = {
            onExpandedChange(false)
            onPower(action)
        },
    )
}
