package com.saubh.deskbuddy.server

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary

/** `GetClipboardSequenceNumber`: bumps on every clipboard change, so the watcher only reads text when it moved. */
object WindowsClipboardSequence {
    internal interface User32Clipboard : StdCallLibrary {
        fun GetClipboardSequenceNumber(): Int
    }

    private val user32: User32Clipboard? by lazy {
        runCatching { Native.load("user32", User32Clipboard::class.java) }.getOrNull()
    }

    fun current(): Long? = user32?.GetClipboardSequenceNumber()?.toLong()?.and(0xFFFF_FFFFL)
}
