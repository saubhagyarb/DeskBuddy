package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.RunningApp

/**
 * Open windows via Win32, browser tabs via UI Automation. Only windows that are listed right now
 * can be closed, so the phone can never post `WM_CLOSE` to an arbitrary handle.
 */
class WindowsWindowActuator(
    private val listWindows: () -> List<TopLevelWindow> = Win32Windows::list,
    private val postClose: (Long) -> Boolean = Win32Windows::close,
    private val listTabs: (List<Long>) -> Map<Long, List<BrowserTab>> = BrowserTabs::list,
    private val startCloseTab: (Long, Int, String) -> Unit = BrowserTabs::close,
    private val targets: ShortcutTargets = ShortcutTargets(),
    private val focusWindow: (Long) -> Boolean = Win32Windows::focus,
) : WindowActuator {

    override fun focusApp(app: DesktopApp): Boolean {
        val exe = targets.target(app.id) ?: return false
        val window = listWindows().firstOrNull { RunningAppsGrouping.appId(it) == exe } ?: return false
        // A refused foreground only flashes the taskbar; still report it focused so the phone never launches a duplicate.
        focusWindow(window.handle)
        return true
    }

    override fun shortcutFor(running: RunningApp, shortcuts: List<DesktopApp>): String? =
        shortcuts.firstOrNull { targets.target(it.id) == running.id }?.id

    override fun runningApps(): List<RunningApp> {
        val windows = listWindows()
        val browserHandles = windows.filter(RunningAppsGrouping::isBrowser).map { it.handle }
        val tabs = runCatching { listTabs(browserHandles) }.getOrDefault(emptyMap())
        return RunningAppsGrouping.group(windows, tabs)
    }

    override fun closeWindow(windowId: String): Boolean {
        val window = listed(windowId) ?: return false
        return postClose(window.handle)
    }

    override fun closeApp(appId: String): Boolean {
        val windows = listWindows().filter { RunningAppsGrouping.appId(it) == appId }
        windows.forEach { postClose(it.handle) }
        return windows.isNotEmpty()
    }

    override fun closeTab(windowId: String, index: Int, title: String): Boolean {
        val window = listed(windowId)?.takeIf(RunningAppsGrouping::isBrowser) ?: return false
        startCloseTab(window.handle, index, title)
        return true
    }

    private fun listed(windowId: String): TopLevelWindow? = listWindows().firstOrNull { it.id == windowId }
}
