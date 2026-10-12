package com.saubh.deskbuddy.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.components.InfoCard
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** What docking still needs from the user before it can open standby on its own. */
data class DockSetup(val needsOverlay: Boolean = false, val needsBackground: Boolean = false) {
    val ready: Boolean get() = !needsOverlay && !needsBackground
}

/** Shown on Home until both permissions are granted; each button opens the matching system screen. */
@Composable
fun DockSetupCard(
    setup: DockSetup,
    onAllowOverlay: () -> Unit,
    onAllowBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoCard(
        text = stringResource(R.string.home_dock_text),
        title = stringResource(R.string.home_dock_title),
        icon = KonnektIcons.Smartphone,
        modifier = modifier,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (setup.needsOverlay) {
                FilledTonalButton(onClick = onAllowOverlay) { Text(stringResource(R.string.home_dock_allow_overlay)) }
            }
            if (setup.needsBackground) {
                FilledTonalButton(onClick = onAllowBackground) { Text(stringResource(R.string.home_dock_allow_background)) }
            }
        }
    }
}
