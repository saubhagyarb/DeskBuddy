@file:OptIn(ExperimentalLayoutApi::class)

package com.saubh.deskbuddy.ui.shell

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.saubh.deskbuddy.ui.apps.AppsRoute
import com.saubh.deskbuddy.ui.home.HomeRoute
import com.saubh.deskbuddy.ui.isWideWindow
import com.saubh.deskbuddy.ui.keyboard.KeyboardRoute
import com.saubh.deskbuddy.ui.media.MediaRoute
import com.saubh.deskbuddy.ui.share.ShareRoute

/**
 * Connected half of the app: adaptive navigation (bottom bar on phones, rail on wide windows),
 * and the PC top bar. Reports whether the Keyboard destination is showing so the app root can keep
 * auto-standby from interrupting it.
 */
@Composable
fun ConnectedShell(
    pcName: String,
    reconnectAttempt: Int?,
    snackbarHostState: SnackbarHostState,
    onDisconnect: () -> Unit,
    onOpenStandby: () -> Unit,
    onKeyboardVisible: (Boolean) -> Unit,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    LaunchedEffect(destination) { onKeyboardVisible(destination == Destination.KEYBOARD) }

    val stateHolder = rememberSaveableStateHolder()
    // While typing, the phone keyboard covers the bottom bar anyway: drop it so the key bar sits right on the keyboard.
    val navigationType = if (WindowInsets.isImeVisible) NavigationSuiteType.None else shellNavigationType()
    NavigationSuiteScaffold(
        navigationSuiteType = navigationType,
        navigationItems = {
            Destination.entries.forEach { entry ->
                NavigationSuiteItem(
                    selected = destination == entry,
                    onClick = { destination = entry },
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.labelRes)) },
                )
            }
        },
    ) {
        Scaffold(
            topBar = { ShellTopBar(pcName, reconnectAttempt, onStandby = onOpenStandby, onDisconnect = onDisconnect) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                Crossfade(targetState = destination, label = "destination") { target ->
                    stateHolder.SaveableStateProvider(target.name) {
                        DestinationContent(target, onNavigate = { destination = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationContent(destination: Destination, onNavigate: (Destination) -> Unit) {
    when (destination) {
        Destination.HOME -> HomeRoute(onNavigate = onNavigate)
        Destination.MEDIA -> MediaRoute()
        Destination.APPS -> AppsRoute()
        Destination.SHARE -> ShareRoute()
        Destination.KEYBOARD -> KeyboardRoute()
    }
}

/**
 * Library default (bar on compact, rail on medium/expanded width), except that a wide but short
 * window such as a phone in landscape gets a rail too: a bottom bar would eat scarce height.
 */
@Composable
private fun shellNavigationType(): NavigationSuiteType {
    val default = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfo())
    val isBar = default == NavigationSuiteType.ShortNavigationBarCompact ||
        default == NavigationSuiteType.ShortNavigationBarMedium ||
        default == NavigationSuiteType.NavigationBar
    return if (isBar && isWideWindow()) NavigationSuiteType.WideNavigationRailCollapsed else default
}
