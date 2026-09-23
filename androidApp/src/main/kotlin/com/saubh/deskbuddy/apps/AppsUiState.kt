package com.saubh.deskbuddy.apps

import com.saubh.deskbuddy.protocol.DesktopApp

data class AppsUiState(
    val apps: List<DesktopApp> = emptyList(),
    val shortcutIds: List<String> = emptyList(),
    /** Decoded PNG bytes per shortcut id, when the PC could extract an icon. */
    val icons: Map<String, ByteArray> = emptyMap(),
    val loaded: Boolean = false,
) {
    val shortcuts: List<DesktopApp>
        get() = shortcutIds.mapNotNull { id -> apps.firstOrNull { it.id == id } }

    fun isShortcut(id: String) = id in shortcutIds
}
