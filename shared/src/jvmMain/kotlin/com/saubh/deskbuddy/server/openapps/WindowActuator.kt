package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.server.UnsupportedOsException

/** Lists the PC's open windows (and browser tabs) and asks them to close. */
interface WindowActuator {
    /** False on platforms without window control; requests are answered with UNSUPPORTED_OS. */
    val isSupported: Boolean get() = true

    /** Slow (browser tabs come from UI Automation): call off the socket and UI threads. */
    fun runningApps(): List<RunningApp>

    /** False when [windowId] is not a currently open, listable window. */
    fun closeWindow(windowId: String): Boolean

    /** False when the app has no open window. */
    fun closeApp(appId: String): Boolean

    /** Starts closing the tab and returns at once; false when the window is not an open browser window. */
    fun closeTab(windowId: String, index: Int, title: String): Boolean

    /** Brings the app's front-most window forward; false when it has none (or the OS activates on launch). */
    fun focusApp(app: DesktopApp): Boolean = false

    /** Which of [shortcuts] [running] was started from, or null. */
    fun shortcutFor(running: RunningApp, shortcuts: List<DesktopApp>): String? = null
}

class UnsupportedWindowActuator(private val osName: String) : WindowActuator {
    override val isSupported: Boolean get() = false
    override fun runningApps(): List<RunningApp> = throw UnsupportedOsException(osName)
    override fun closeWindow(windowId: String): Boolean = throw UnsupportedOsException(osName)
    override fun closeApp(appId: String): Boolean = throw UnsupportedOsException(osName)
    override fun closeTab(windowId: String, index: Int, title: String): Boolean = throw UnsupportedOsException(osName)
}
