package com.saubh.deskbuddy.server.keyboard

/** A system-wide shortcut that toggles "type on phone" while another app has focus. */
interface GlobalHotkey {
    /** False when registration failed, e.g. another app already owns the shortcut. */
    fun start(): Boolean

    fun stop()
}
