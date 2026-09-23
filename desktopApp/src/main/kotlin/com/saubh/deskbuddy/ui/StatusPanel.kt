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

/** Home pane: one hero card whose content follows the server state, plus address chips. */
@Composable
fun StatusPanel(
    state: ServerState,
    hostName: String,
    ipAddress: String,
    port: Int,
    mediaHelperAvailable: Boolean,
    onUnpairAll: () -> Unit,
) {
    Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (state) {
            is ServerState.Waiting -> WaitingHero()
            is ServerState.Pairing -> PairingHero(state.pin)
            is ServerState.Connected -> ConnectedHero(state.deviceName, onUnpairAll)
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
private fun ConnectedHero(deviceName: String, onUnpairAll: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    HeroCard(scheme.surfaceContainerHigh) {
        IconAvatar(DeskBuddyIcons.Smartphone, 80.dp, cookieShape(), scheme.primary, scheme.onPrimary)
        Text("Connected", style = MaterialTheme.typography.labelLargeEmphasized, color = scheme.primary)
        Text(deviceName, style = MaterialTheme.typography.headlineMediumEmphasized)
        Text(
            "Media, apps, clipboard and files are ready. Use the Share and Shortcuts panes on the left.",
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        MediumOutlinedButton("Unpair all devices", onUnpairAll, icon = DeskBuddyIcons.Delete)
    }
}
