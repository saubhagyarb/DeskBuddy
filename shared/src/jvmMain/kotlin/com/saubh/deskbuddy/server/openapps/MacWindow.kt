package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab

/** A standard window as read from System Events, with its browser tabs when the app is a scriptable browser. */
data class MacWindow(
    val pid: Int,
    /** Empty when the process has no bundle (rare: bare executables). */
    val bundleId: String,
    val appName: String,
    /** Position in System Events' window list for [pid]. */
    val index: Int,
    val title: String,
    /** Position in the browser's own window list; null when not a browser or not matched. */
    val browserWindow: Int? = null,
    val tabs: List<BrowserTab> = emptyList(),
) {
    val id: String get() = "$pid:$index"
    val appId: String get() = bundleId.ifEmpty { "pid:$pid" }
}
