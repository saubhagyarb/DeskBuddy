@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.MediumButton
import com.saubh.deskbuddy.ui.components.MediumTonalButton
import com.saubh.deskbuddy.ui.components.SectionTitle
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

@Composable
fun ConnectScreen(
    state: ConnectionUiState,
    saved: List<SavedDesktop>,
    onReconnect: (SavedDesktop) -> Unit,
    onForget: (SavedDesktop) -> Unit,
    onConnect: (name: String, host: String, port: Int) -> Unit,
    onSubmitPin: (String) -> Unit,
    onCancelPairing: () -> Unit,
    onStartDiscovery: () -> Unit,
    onVisibleChange: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) { onStartDiscovery() }
    DisposableEffect(Unit) {
        onVisibleChange(true)
        onDispose { onVisibleChange(false) }
    }
    var forgetting by remember { mutableStateOf<SavedDesktop?>(null) }
    val desktops = (state as? ConnectionUiState.Discovering)?.desktops.orEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val fullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
    // Centre the content in a readable column on very wide windows; the whole window still scrolls.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val sideGutter = maxOf(16.dp, (windowWidth - CONTENT_MAX_WIDTH) / 2)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.connect_subtitle)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        // Discovered PCs flow into columns on wide windows; everything else spans the full width.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = DESKTOP_ROW_MIN_WIDTH),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = sideGutter,
                end = sideGutter,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (saved.isNotEmpty()) {
                item(span = fullSpan) { SectionTitle(stringResource(R.string.saved_pcs)) }
                items(saved, key = { it.host + it.token }, span = { GridItemSpan(maxLineSpan) }) { pc ->
                    SavedPcCard(pc, highlighted = pc == saved.first(), onConnect = { onReconnect(pc) }, onForget = { forgetting = pc })
                }
            }
            item(span = fullSpan) {
                SectionTitle(stringResource(R.string.discovered_desktops)) {
                    if (desktops.isEmpty()) LoadingIndicator(Modifier.size(32.dp))
                }
            }
            if (desktops.isEmpty()) {
                item(span = fullSpan) { SearchingCard() }
            } else {
                items(desktops, key = { it.host }) { desktop ->
                    DesktopRow(desktop) { onConnect(desktop.name, desktop.host, desktop.port) }
                }
            }
            item(span = fullSpan) {
                ManualConnectCard { host -> onConnect(host, host, Protocol.PORT) }
            }
            if (state is ConnectionUiState.Connecting) {
                item(span = fullSpan) { ConnectingRow(state.target) }
            }
        }
    }

    if (state is ConnectionUiState.Pairing) {
        PinDialog(attemptsLeft = state.attemptsLeft, onSubmit = onSubmitPin, onDismiss = onCancelPairing)
    }
    forgetting?.let { pc ->
        ForgetPcDialog(pc.name, onConfirm = { onForget(pc); forgetting = null }, onDismiss = { forgetting = null })
    }
}

@Composable
private fun SavedPcCard(saved: SavedDesktop, highlighted: Boolean, onConnect: () -> Unit, onForget: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = if (highlighted) scheme.primaryContainer else scheme.surfaceContainerHigh
    val content = if (highlighted) scheme.onPrimaryContainer else scheme.onSurface
    val forgetCd = stringResource(R.string.cd_forget_pc, saved.name)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconAvatar(DeskBuddyIcons.Computer, 64.dp, cookieShape(), scheme.primary, scheme.onPrimary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.saved_pc), style = MaterialTheme.typography.labelLargeEmphasized)
                    Text(saved.name, style = MaterialTheme.typography.headlineSmallEmphasized)
                    Text(saved.host, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = onForget, modifier = Modifier.semantics { contentDescription = forgetCd }) {
                    Icon(DeskBuddyIcons.Delete, contentDescription = null)
                }
            }
            Text(stringResource(R.string.saved_pc_auto_hint), style = MaterialTheme.typography.bodyMedium)
            MediumButton(
                text = stringResource(R.string.reconnect),
                onClick = onConnect,
                icon = DeskBuddyIcons.Link,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DesktopRow(desktop: DiscoveredDesktop, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconAvatar(DeskBuddyIcons.Wifi, 48.dp, CircleShape, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Column(Modifier.weight(1f)) {
                Text(desktop.name, style = MaterialTheme.typography.titleMedium)
                Text("${desktop.host}:${desktop.port}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            Icon(DeskBuddyIcons.Link, contentDescription = null, tint = scheme.primary)
        }
    }
}

@Composable
private fun SearchingCard() {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.searching_pcs), style = MaterialTheme.typography.bodyLargeEmphasized)
            Text(
                stringResource(R.string.no_desktops_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ManualConnectCard(onConnect: (String) -> Unit) {
    var manualIp by rememberSaveable { mutableStateOf("") }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.manual_title), style = MaterialTheme.typography.titleMediumEmphasized)
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text(stringResource(R.string.manual_ip_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            MediumTonalButton(
                text = stringResource(R.string.connect),
                onClick = { onConnect(manualIp.trim()) },
                enabled = manualIp.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectingRow(target: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    ) {
        ContainedLoadingIndicator()
        Text(stringResource(R.string.connecting_to, target), style = MaterialTheme.typography.bodyLargeEmphasized)
        Spacer(Modifier.height(0.dp))
    }
}

/** Narrowest discovered-PC row; wide windows show several side by side. */
private val DESKTOP_ROW_MIN_WIDTH = 340.dp

/** Widest the connect content grows before it is centred. */
private val CONTENT_MAX_WIDTH = 1040.dp
