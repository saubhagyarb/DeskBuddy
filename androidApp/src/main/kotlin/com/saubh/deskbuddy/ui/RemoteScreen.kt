@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.components.IconAvatar
import com.saubh.deskbuddy.ui.components.cookieShape
import com.saubh.deskbuddy.ui.media.MediaTab
import com.saubh.deskbuddy.ui.standby.StandbyScreen
import com.saubh.deskbuddy.ui.theme.DeskBuddyIcons

internal enum class RemoteTab(val labelRes: Int, val icon: ImageVector) {
    MEDIA(R.string.tab_media, DeskBuddyIcons.MusicNote),
    APPS(R.string.tab_apps, DeskBuddyIcons.Apps),
    SHARE(R.string.tab_share, DeskBuddyIcons.ContentPaste),
}

/**
 * Connected screen. The navigation area adapts to the window: a bottom bar on compact
 * phones, a rail on wider windows (landscape, foldables, tablets, desktop).
 */
@Composable
fun RemoteScreen(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    val title = (state as? ConnectionUiState.Connected)?.desktopName ?: stringResource(R.string.app_name)
    val subtitle = when (state) {
        is ConnectionUiState.Reconnecting -> stringResource(R.string.reconnecting, state.attempt)
        else -> stringResource(R.string.status_connected)
    }
    val isWide = isWideWindow()
    var tab by rememberSaveable { mutableStateOf(RemoteTab.MEDIA) }
    
    val autoStandby by viewModel.autoStandby.isAutoStandby.collectAsStateWithLifecycle()
    var userStandby by rememberSaveable { mutableStateOf(false) }
    var suppressAutoStandby by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(autoStandby) {
        if (!autoStandby) suppressAutoStandby = false
    }

    val standby = userStandby || (autoStandby && !suppressAutoStandby)

    if (standby) {
        StandbyScreen(viewModel) {
            userStandby = false
            if (autoStandby) suppressAutoStandby = true
        }
        return
    }

    RemoteShell(
        title = title,
        subtitle = subtitle,
        selectedTab = tab,
        onTabSelected = { tab = it },
        onStandby = { userStandby = true },
        onDisconnect = viewModel::disconnect,
    ) { TabContent(tab, isWide, viewModel) }
}

/**
 * Stateless connected-screen chrome: adaptive navigation (bottom bar on compact windows,
 * rail on wider ones), a top bar that hides on scroll, and the Standby FAB.
 */
@Composable
internal fun RemoteShell(
    title: String,
    subtitle: String,
    selectedTab: RemoteTab,
    onTabSelected: (RemoteTab) -> Unit,
    onStandby: () -> Unit,
    onDisconnect: () -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteType = remoteNavigationType(),
        navigationItems = {
            RemoteTab.entries.forEach { entry ->
                NavigationSuiteItem(
                    selected = selectedTab == entry,
                    onClick = { onTabSelected(entry) },
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        },
    ) {
        // Each tab owns its app bar state: hides on scroll down, returns on scroll up.
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = { RemoteTopBar(title, subtitle, scrollBehavior, onDisconnect) },
            floatingActionButton = {
                MediumExtendedFloatingActionButton(onClick = onStandby) {
                    Icon(DeskBuddyIcons.Nightlight, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.standby))
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}

@Composable
private fun RemoteTopBar(
    title: String,
    subtitle: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val disconnectCd = stringResource(R.string.cd_disconnect)
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLargeEmphasized) },
        subtitle = { Text(subtitle, color = scheme.primary) },
        navigationIcon = {
            Box(Modifier.padding(start = 12.dp, end = 4.dp)) {
                IconAvatar(DeskBuddyIcons.Computer, 40.dp, cookieShape(), scheme.primaryContainer, scheme.onPrimaryContainer)
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = onDisconnect,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = disconnectCd },
            ) {
                Icon(DeskBuddyIcons.Power, contentDescription = null)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun TabContent(tab: RemoteTab, isWide: Boolean, viewModel: ConnectionViewModel) {
    when (tab) {
        RemoteTab.MEDIA -> {
            val media by viewModel.media.state.collectAsStateWithLifecycle()
            MediaTab(
                state = media,
                isWide = isWide,
                onMedia = viewModel::sendMedia,
                onSeek = viewModel.media::seek,
                onVolume = viewModel.media::setVolume,
                onToggleMute = viewModel.media::toggleMute,
                onSelectDevice = viewModel.media::selectDevice,
            )
        }
        RemoteTab.APPS -> {
            val apps by viewModel.apps.state.collectAsStateWithLifecycle()
            AppsTab(
                state = apps,
                onLaunch = viewModel.apps::launch,
                onPin = viewModel.apps::pin,
                onUnpin = viewModel.apps::unpin,
                onRefresh = viewModel.apps::refresh,
            )
        }
        RemoteTab.SHARE -> {
            val autoSync by viewModel.share.autoSync.collectAsStateWithLifecycle()
            val inbox by viewModel.share.inbox.collectAsStateWithLifecycle()
            val transfer by viewModel.files.progress.collectAsStateWithLifecycle()
            ShareTab(
                autoSync = autoSync,
                inbox = inbox,
                transfer = transfer,
                onAutoSyncChange = viewModel.share::setAutoSync,
                onSendClipboard = viewModel.share::sendClipboard,
                onPullClipboard = viewModel.share::pullClipboard,
                onSendText = viewModel.share::sendText,
                onSendFile = viewModel.files::sendFile,
                onCancelTransfer = viewModel.files::cancelSend,
                onCopy = viewModel.share::copyToClipboard,
                onClearInbox = viewModel.share::clearInbox,
            )
        }
    }
}

/**
 * Library default (bar on compact, rail on medium/expanded width), except that a wide but short
 * window such as a phone in landscape gets a rail too: a bottom bar would eat scarce height.
 */
@Composable
private fun remoteNavigationType(): NavigationSuiteType {
    val default = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    val isBar = default == NavigationSuiteType.ShortNavigationBarCompact ||
        default == NavigationSuiteType.ShortNavigationBarMedium ||
        default == NavigationSuiteType.NavigationBar
    return if (isBar && isWideWindow()) NavigationSuiteType.WideNavigationRailCollapsed else default
}
