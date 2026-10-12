package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.server.Osascript
import kotlin.concurrent.thread

/**
 * Runs the bundled `mac-windows.js` (JXA). Listing and closing windows use System Events UI
 * scripting (Accessibility permission); tabs use each browser's dictionary (Automation permission).
 */
object MacWindowScript {
    private const val RESOURCE = "/deskbuddy/mac-windows.js"
    private const val LIST_TIMEOUT_MS = 15_000L
    private const val ACTION_TIMEOUT_MS = 5_000L

    private val script: String by lazy {
        MacWindowScript::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
            ?: error("Missing helper resource $RESOURCE")
    }

    fun list(): List<MacWindow> = MacWindowList.parse(run(listOf("list"), LIST_TIMEOUT_MS).orEmpty())

    fun closeWindow(window: MacWindow): Boolean =
        run(listOf("closeWindow", window.pid.toString(), window.index.toString(), window.title), ACTION_TIMEOUT_MS) == "true"

    /** Fire-and-forget: quitting can wait on a "save changes?" sheet far longer than any timeout. */
    fun quitApp(appId: String) {
        thread(isDaemon = true, name = "konnekt-quit-app") { run(listOf("quitApp", appId), ACTION_TIMEOUT_MS) }
    }

    /** Fire-and-forget: the phone re-lists afterwards to see the result. */
    fun closeTab(window: MacWindow, index: Int, title: String) {
        val browserWindow = window.browserWindow ?: return
        thread(isDaemon = true, name = "konnekt-close-tab") {
            run(listOf("closeTab", window.bundleId, browserWindow.toString(), index.toString(), title), ACTION_TIMEOUT_MS)
        }
    }

    private fun run(args: List<String>, timeoutMs: Long): String? =
        Osascript.run(script, args, Osascript.Language.JAVASCRIPT, timeoutMs)
}
