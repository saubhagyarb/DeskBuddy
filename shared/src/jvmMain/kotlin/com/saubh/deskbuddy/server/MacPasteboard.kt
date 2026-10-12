package com.saubh.deskbuddy.server

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * `[[NSPasteboard generalPasteboard] changeCount]`: bumps on every clipboard change, so the watcher
 * only reads text when it moved (the macOS twin of [WindowsClipboardSequence]). Both messages take
 * no arguments, so calling `objc_msgSend` through JNA is safe on arm64 as well as x86_64.
 */
object MacPasteboard {
    internal interface ObjC : Library {
        fun objc_getClass(name: String): Pointer?
        fun sel_registerName(name: String): Pointer
        fun objc_msgSend(receiver: Pointer, selector: Pointer): Pointer?
    }

    internal interface ObjCLong : Library {
        fun objc_msgSend(receiver: Pointer, selector: Pointer): Long
    }

    private val objc: ObjC? by lazy { runCatching { Native.load("objc", ObjC::class.java) }.getOrNull() }
    private val objcLong: ObjCLong? by lazy { runCatching { Native.load("objc", ObjCLong::class.java) }.getOrNull() }

    /** AppKit must be loaded for NSPasteboard to exist, so a miss is retried rather than cached. */
    @Volatile
    private var pasteboard: Pointer? = null

    private fun pasteboard(runtime: ObjC): Pointer? = pasteboard ?: runtime.objc_getClass("NSPasteboard")
        ?.let { runtime.objc_msgSend(it, runtime.sel_registerName("generalPasteboard")) }
        ?.also { pasteboard = it }

    fun current(): Long? = runCatching {
        val runtime = objc ?: return null
        val longs = objcLong ?: return null
        val board = pasteboard(runtime) ?: return null
        longs.objc_msgSend(board, runtime.sel_registerName("changeCount"))
    }.getOrNull()
}
