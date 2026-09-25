@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.standby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.ConfirmDialog
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

/** Lock, restart and shut down the PC. Restart and shut down ask first; lock is instant and harmless. */
@Composable
fun StandbyPowerButtons(onPower: (PowerAction) -> Unit, modifier: Modifier = Modifier) {
    var confirming by rememberSaveable { mutableStateOf<PowerAction?>(null) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        PowerButton(DeskBuddyIcons.Lock, stringResource(R.string.cd_lock_pc)) { onPower(PowerAction.LOCK) }
        PowerButton(DeskBuddyIcons.RestartAlt, stringResource(R.string.cd_restart_pc)) { confirming = PowerAction.RESTART }
        PowerButton(DeskBuddyIcons.Power, stringResource(R.string.cd_shutdown_pc)) { confirming = PowerAction.SHUTDOWN }
    }
    when (confirming) {
        PowerAction.RESTART -> ConfirmDialog(
            icon = DeskBuddyIcons.RestartAlt,
            title = stringResource(R.string.power_restart_title),
            text = stringResource(R.string.power_restart_text),
            confirmLabel = stringResource(R.string.power_restart_confirm),
            onConfirm = { confirming = null; onPower(PowerAction.RESTART) },
            onDismiss = { confirming = null },
        )
        PowerAction.SHUTDOWN -> ConfirmDialog(
            icon = DeskBuddyIcons.Power,
            title = stringResource(R.string.power_shutdown_title),
            text = stringResource(R.string.power_shutdown_text),
            confirmLabel = stringResource(R.string.power_shutdown_confirm),
            onConfirm = { confirming = null; onPower(PowerAction.SHUTDOWN) },
            onDismiss = { confirming = null },
        )
        PowerAction.LOCK, null -> Unit
    }
}

@Composable
private fun PowerButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.semantics { contentDescription = description },
    ) { Icon(icon, contentDescription = null) }
}
