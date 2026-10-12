package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer

/**
 * CoreGraphics `CGEventPost` through plain C calls (no Objective-C). Text is attached as a
 * Unicode string, so it arrives exactly whatever keyboard layout the Mac uses. macOS drops
 * synthetic events silently unless this app is allowed under System Settings → Privacy &
 * Security → Accessibility; [isTrusted] reports whether it is.
 */
class MacKeyboardActuator : KeyboardActuator {
    @Suppress("FunctionName")
    internal interface ApplicationServices : Library {
        fun CGEventSourceCreate(stateId: Int): Pointer?
        fun CGEventCreateKeyboardEvent(source: Pointer?, virtualKey: Short, keyDown: Boolean): Pointer?
        /** `UniChar` is 16-bit; JNA maps `CharArray` to 32-bit `wchar_t` on macOS, so units go as shorts. */
        fun CGEventKeyboardSetUnicodeString(event: Pointer, length: NativeLong, string: ShortArray)
        fun CGEventSetFlags(event: Pointer, flags: Long)
        fun CGEventPost(tap: Int, event: Pointer)
        fun CFRelease(ref: Pointer)

        /** C `Boolean` is one byte; a wider JNA return type would read garbage high bytes. */
        fun AXIsProcessTrusted(): Byte
    }

    companion object {
        private const val HID_SYSTEM_STATE = 1
        private const val HID_EVENT_TAP = 0

        /** Loaded on first use, so loading this class on Windows or Linux never touches it. */
        internal val native: ApplicationServices by lazy {
            Native.load("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices", ApplicationServices::class.java)
        }

        /** False until the user grants Accessibility access; posted keys are dropped meanwhile. */
        fun isTrusted(): Boolean = runCatching { native.AXIsProcessTrusted().toInt() != 0 }.getOrDefault(false)
    }

    override fun type(text: String) {
        MacKeyPlan.textChunks(text).forEach { chunk ->
            val chars = ShortArray(chunk.length) { chunk[it].code.toShort() }
            post(keyCode = 0, keyDown = true) { native.CGEventKeyboardSetUnicodeString(it, NativeLong(chars.size.toLong()), chars) }
            post(keyCode = 0, keyDown = false) { native.CGEventKeyboardSetUnicodeString(it, NativeLong(chars.size.toLong()), chars) }
        }
    }

    override fun press(key: Key, modifiers: Set<KeyModifier>) {
        val stroke = MacKeyPlan.forKey(key, modifiers)
        // Flags on both events, so the key-up does not read as a bare key to apps that track modifiers.
        post(stroke.keyCode, keyDown = true) { native.CGEventSetFlags(it, stroke.flags) }
        post(stroke.keyCode, keyDown = false) { native.CGEventSetFlags(it, stroke.flags) }
    }

    private fun post(keyCode: Int, keyDown: Boolean, configure: (Pointer) -> Unit) {
        val source = native.CGEventSourceCreate(HID_SYSTEM_STATE)
        try {
            val event = native.CGEventCreateKeyboardEvent(source, keyCode.toShort(), keyDown) ?: error("CGEventCreateKeyboardEvent failed")
            try {
                configure(event)
                native.CGEventPost(HID_EVENT_TAP, event)
            } finally {
                native.CFRelease(event)
            }
        } finally {
            source?.let(native::CFRelease)
        }
    }
}
