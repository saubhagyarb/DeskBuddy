package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.server.HostOs
import java.awt.Desktop

/**
 * A tray app on macOS is not the active application, so its new overlay window opens behind the
 * app the user is in and never gets key events. Asking AppKit to activate the app fixes that.
 */
object MacForeground {
    /** No-op off macOS or where the AWT Desktop API is unavailable. */
    fun activate() {
        if (!HostOs.isMac()) return
        runCatching {
            if (!Desktop.isDesktopSupported()) return
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) desktop.requestForeground(true)
        }
    }
}
