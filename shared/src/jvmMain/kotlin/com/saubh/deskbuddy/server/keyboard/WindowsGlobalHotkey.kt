package com.saubh.deskbuddy.server.keyboard

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Ctrl+Alt+K via `RegisterHotKey` on a dedicated message-loop thread. [onPressed] runs on that thread.
 * Receiving WM_HOTKEY also lets this process take the foreground, so the overlay can grab focus.
 */
class WindowsGlobalHotkey(private val onPressed: () -> Unit) : GlobalHotkey {
    companion object {
        const val LABEL = "Ctrl+Alt+K"
        private const val HOTKEY_ID = 0x4442
        private const val MOD_ALT = 0x0001
        private const val MOD_CONTROL = 0x0002
        private const val MOD_NOREPEAT = 0x4000
        private const val VK_K = 0x4B
        private const val WM_HOTKEY = 0x0312
        private const val WM_QUIT = 0x0012
        private const val START_TIMEOUT_MS = 2_000L
    }

    @Volatile
    private var threadId = 0

    /** False when registration failed, e.g. another app already owns Ctrl+Alt+K. */
    override fun start(): Boolean {
        val registered = CompletableFuture<Boolean>()
        thread(isDaemon = true, name = "deskbuddy-hotkey") {
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val ok = User32.INSTANCE.RegisterHotKey(null, HOTKEY_ID, MOD_CONTROL or MOD_ALT or MOD_NOREPEAT, VK_K)
            registered.complete(ok)
            if (!ok) return@thread
            val msg = WinUser.MSG()
            while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) runCatching { onPressed() }
            }
            User32.INSTANCE.UnregisterHotKey(null, HOTKEY_ID)
        }
        return runCatching { registered.get(START_TIMEOUT_MS, TimeUnit.MILLISECONDS) }.getOrDefault(false)
    }

    override fun stop() {
        val id = threadId
        if (id != 0) User32.INSTANCE.PostThreadMessage(id, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0))
    }
}
