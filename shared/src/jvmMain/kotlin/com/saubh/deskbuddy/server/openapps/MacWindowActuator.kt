package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.RunningApp

/**
 * Open windows via System Events, browser tabs via each browser's scripting dictionary (Chrome,
 * Edge, Brave, Vivaldi, Opera, Safari; Firefox has none). Only windows listed right now can be
 * closed. Needs Accessibility for KonneKt and Automation permission for System Events and each browser.
 */
class MacWindowActuator(
    private val listWindows: () -> List<MacWindow> = MacWindowScript::list,
    private val pressClose: (MacWindow) -> Boolean = MacWindowScript::closeWindow,
    private val startQuit: (String) -> Unit = MacWindowScript::quitApp,
    private val startCloseTab: (MacWindow, Int, String) -> Unit = MacWindowScript::closeTab,
) : WindowActuator {

    override fun runningApps(): List<RunningApp> = MacWindowList.group(listWindows())

    override fun shortcutFor(running: RunningApp, shortcuts: List<DesktopApp>): String? =
        shortcuts.firstOrNull { it.name.equals(running.name, ignoreCase = true) }?.id

    override fun closeWindow(windowId: String): Boolean {
        val window = listed(windowId) ?: return false
        return pressClose(window)
    }

    /** Asks the app to quit, as ⌘Q would (it may ask to save), so every window of it closes. */
    override fun closeApp(appId: String): Boolean {
        if (listWindows().none { it.appId == appId }) return false
        startQuit(appId)
        return true
    }

    override fun closeTab(windowId: String, index: Int, title: String): Boolean {
        val window = listed(windowId)?.takeIf { it.browserWindow != null } ?: return false
        startCloseTab(window, index, title)
        return true
    }

    private fun listed(windowId: String): MacWindow? = listWindows().firstOrNull { it.id == windowId }
}
