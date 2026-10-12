package com.saubh.deskbuddy.server.openapps

/** A window the user could switch to with Alt+Tab, as read from Win32. */
data class TopLevelWindow(
    val handle: Long,
    val title: String,
    val processId: Int,
    /** Full executable path, or empty when the process could not be opened (e.g. elevated). */
    val exePath: String,
    /** File description from the executable's version info ("Google Chrome"), or empty. */
    val appName: String,
) {
    val id: String get() = handle.toString()
}
