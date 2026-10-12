package com.saubh.deskbuddy.apps

import com.saubh.deskbuddy.protocol.ActivateAppCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.LaunchAppCommand

data class AppsUiState(
    val apps: List<DesktopApp> = emptyList(),
    val shortcutIds: List<String> = emptyList(),
    /** Decoded PNG bytes per shortcut id, when the PC could extract an icon. */
    val icons: Map<String, ByteArray> = emptyMap(),
    val loaded: Boolean = false,
    val canActivate: Boolean = false,
) {
    val shortcuts: List<DesktopApp>
        get() = shortcutIds.mapNotNull { id -> apps.firstOrNull { it.id == id } }

    fun isShortcut(id: String) = id in shortcutIds

    /** Older desktops answer `app_activate` with a generic error, so they only ever get launch. */
    fun openCommandFor(appId: String): Command = if (canActivate) ActivateAppCommand(appId) else LaunchAppCommand(appId)
}
