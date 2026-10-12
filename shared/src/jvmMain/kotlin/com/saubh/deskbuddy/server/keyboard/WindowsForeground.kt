package com.saubh.deskbuddy.server.keyboard

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef

/**
 * Windows only lets a process take the foreground right after it received input. The hotkey
 * grants that, but by the time the overlay window exists the user has released Ctrl+Alt and the
 * grant is gone, so `toFront()` silently fails and keys keep going to the other app. Briefly
 * attaching to the foreground thread's input queue makes `SetForegroundWindow` succeed.
 */
object WindowsForeground {
    /** No-op off Windows or when the window has no native peer yet. */
    fun force(window: java.awt.Window) {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window) ?: return)
            val user32 = User32.INSTANCE
            val foreground = user32.GetForegroundWindow()
            if (foreground == hwnd) return
            val foregroundThread = user32.GetWindowThreadProcessId(foreground, null)
            val ownThread = Kernel32.INSTANCE.GetCurrentThreadId()
            val attached = foregroundThread != 0 && foregroundThread != ownThread &&
                user32.AttachThreadInput(WinDef.DWORD(ownThread.toLong()), WinDef.DWORD(foregroundThread.toLong()), true)
            try {
                user32.BringWindowToTop(hwnd)
                user32.SetForegroundWindow(hwnd)
                user32.SetFocus(hwnd)
            } finally {
                if (attached) {
                    user32.AttachThreadInput(WinDef.DWORD(ownThread.toLong()), WinDef.DWORD(foregroundThread.toLong()), false)
                }
            }
        }
    }
}
