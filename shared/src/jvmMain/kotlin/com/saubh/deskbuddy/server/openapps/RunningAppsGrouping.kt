package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.RunningApp

/** Pure grouping of windows into apps, so naming and ordering are testable without Win32. */
object RunningAppsGrouping {
    private val BROWSERS = setOf("chrome.exe", "msedge.exe", "brave.exe", "vivaldi.exe", "opera.exe", "firefox.exe")

    fun isBrowser(window: TopLevelWindow): Boolean = exeName(window.exePath) in BROWSERS

    /**
     * Windows arrive in Z-order (most recently used first); apps keep the order of their first window,
     * so the app the user was just in is listed first. [tabs] maps a window handle to its tab strip.
     */
    fun group(windows: List<TopLevelWindow>, tabs: Map<Long, List<BrowserTab>> = emptyMap()): List<RunningApp> =
        windows.groupBy { appId(it) }.map { (id, group) ->
            RunningApp(
                id = id,
                name = displayName(group.first()),
                windows = group.map { AppWindow(it.id, it.title, tabs[it.handle].orEmpty()) },
            )
        }

    /** The executable path identifies the program; a process we cannot open is its own app. */
    fun appId(window: TopLevelWindow): String = window.exePath.lowercase().ifEmpty { "pid:${window.processId}" }

    fun displayName(window: TopLevelWindow): String {
        if (window.appName.isNotBlank()) return window.appName.trim()
        val exe = exeName(window.exePath).removeSuffix(".exe")
        return exe.replaceFirstChar { it.uppercase() }.ifEmpty { window.title }
    }

    private fun exeName(path: String): String = path.substringAfterLast('\\').substringAfterLast('/').lowercase()
}
