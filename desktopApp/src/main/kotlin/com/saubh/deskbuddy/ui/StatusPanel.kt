@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.server.ServerState
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumOutlinedButton
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons
import kotlinx.coroutines.delay

/** "Start with Windows" switch state; null hides the card (non-Windows). */
data class StartupSetting(val enabled: Boolean, val onChange: (Boolean) -> Unit)

/** Home pane: one hero card whose content follows the server state, address chips, and the startup switch. */
@Composable
fun StatusPanel(
    state: ServerState,
    hostName: String,
    ipAddress: String,
    port: Int,
    mediaHelperAvailable: Boolean,
    startup: StartupSetting?,
    onUnpairAll: () -> Unit,
) {
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state) {
            is ServerState.Waiting -> WaitingHero()
            is ServerState.Pairing -> PairingHero(state.pin)
            is ServerState.Connected -> ConnectedHero(state.devices, onUnpairAll)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = {},
                label = { Text(hostName) },
                leadingIcon = { Icon(DeskBuddyIcons.Computer, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = CircleShape,
            )
            AssistChip(
                onClick = {},
                label = { Text("$ipAddress : $port") },
                leadingIcon = { Icon(DeskBuddyIcons.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = CircleShape,
            )
            AssistChip(
                onClick = {},
                label = { Text(if (mediaHelperAvailable) "Now playing: on" else "Now playing: unavailable") },
                leadingIcon = { Icon(DeskBuddyIcons.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = CircleShape,
            )
        }
        startup?.let { StartupCard(it) }
    }
}

@Composable
private fun HeroCard(container: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content() }
    }
}

@Composable
private fun WaitingHero() {
    val scheme = MaterialTheme.colorScheme
    HeroCard(scheme.surfaceContainerHigh) {
        LoadingIndicator(Modifier.size(72.dp))
        Text("Waiting for your phone", style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            "Open DeskBuddy on your phone on the same Wi-Fi network. This PC will show up automatically, or connect by address.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PairingHero(pin: String) {
    val scheme = MaterialTheme.colorScheme
    val total = (Protocol.PAIRING_TIMEOUT_MILLIS / 1_000).toInt()
    var secondsLeft by remember(pin) { mutableStateOf(total) }
    LaunchedEffect(pin) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }
    HeroCard(scheme.primaryContainer) {
        Text("Pairing request", style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.onPrimaryContainer)
        Text(
            pin.chunked(3).joinToString("  "),
            style = MaterialTheme.typography.displayLargeEmphasized,
            color = scheme.onPrimaryContainer,
        )
        Text("Enter this PIN on your phone", style = MaterialTheme.typography.bodyLarge, color = scheme.onPrimaryContainer)
        LinearProgressIndicator(
            progress = { secondsLeft / total.toFloat() },
            modifier = Modifier.fillMaxWidth(0.6f),
        )
        Text("Expires in ${secondsLeft}s", style = MaterialTheme.typography.labelMedium, color = scheme.onPrimaryContainer)
    }
}

@Composable
private fun ConnectedHero(devices: List<String>, onUnpairAll: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    HeroCard(scheme.surfaceContainerHigh) {
        IconAvatar(DeskBuddyIcons.Smartphone, 80.dp, cookieShape(), scheme.primary, scheme.onPrimary)
        Text("Connected", style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.primary)
        Text(devices.joinToString(", "), style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        if (devices.size > 1) {
            Text("${devices.size} phones connected", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        }
        Text(
            "Media, apps, clipboard and files are ready. Use the Share and Shortcuts panes on the left.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        MediumOutlinedButton("Unpair all devices", onUnpairAll, icon = DeskBuddyIcons.Delete)
    }
}

@Composable
private fun StartupCard(setting: StartupSetting) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Start with Windows", style = MaterialTheme.typography.titleMediumEmphasized)
                Text(
                    "Runs in the tray after you sign in, so your phone reconnects on its own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            Switch(checked = setting.enabled, onCheckedChange = setting.onChange)
        }
    }
}
