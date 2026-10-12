package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** One CGEvent key stroke: macOS virtual key code (kVK_*) and the CGEventFlags held with it. */
data class MacKeyStroke(val keyCode: Int, val flags: Long)

/**
 * Pure mapping of phone keys to macOS key events, so it is testable without CoreGraphics.
 * The phone speaks Windows shortcuts: Ctrl becomes ⌘ (Ctrl+C copies), except with the
 * word-wise editing keys, where Windows Ctrl means ⌥ on a Mac. Meta (the Windows key) becomes ⌃.
 */
object MacKeyPlan {
    const val FLAG_SHIFT = 0x20000L
    const val FLAG_CONTROL = 0x40000L
    const val FLAG_OPTION = 0x80000L
    const val FLAG_COMMAND = 0x100000L

    /** CGEventKeyboardSetUnicodeString accepts at most 20 UTF-16 units per event. */
    const val MAX_UNICODE_UNITS = 20

    private val WORD_WISE = setOf(Key.LEFT, Key.RIGHT, Key.BACKSPACE, Key.DELETE)

    /** kVK_ANSI_* for A..Z and 0..9 (physical positions on an ANSI keyboard, not contiguous). */
    private val LETTERS = intArrayOf(
        0x00, 0x0B, 0x08, 0x02, 0x0E, 0x03, 0x05, 0x04, 0x22, 0x26, 0x28, 0x25, 0x2E, // A..M
        0x2D, 0x1F, 0x23, 0x0C, 0x0F, 0x01, 0x11, 0x20, 0x09, 0x0D, 0x07, 0x10, 0x06, // N..Z
    )
    private val DIGITS = intArrayOf(0x1D, 0x12, 0x13, 0x14, 0x15, 0x17, 0x16, 0x1A, 0x1C, 0x19)

    fun forKey(key: Key, modifiers: Set<KeyModifier>): MacKeyStroke =
        MacKeyStroke(keyCode(key), modifiers.fold(0L) { flags, modifier -> flags or flagOf(modifier, key) })

    /** [text] in event-sized pieces; a surrogate pair is never split across two events. */
    fun textChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + MAX_UNICODE_UNITS, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }

    private fun flagOf(modifier: KeyModifier, key: Key): Long = when (modifier) {
        KeyModifier.CTRL -> if (key in WORD_WISE) FLAG_OPTION else FLAG_COMMAND
        KeyModifier.ALT -> FLAG_OPTION
        KeyModifier.SHIFT -> FLAG_SHIFT
        KeyModifier.META -> FLAG_CONTROL
    }

    fun keyCode(key: Key): Int = when (key) {
        Key.ENTER -> 0x24
        Key.BACKSPACE -> 0x33
        Key.DELETE -> 0x75
        Key.TAB -> 0x30
        Key.ESCAPE -> 0x35
        Key.SPACE -> 0x31
        Key.UP -> 0x7E
        Key.DOWN -> 0x7D
        Key.LEFT -> 0x7B
        Key.RIGHT -> 0x7C
        Key.HOME -> 0x73
        Key.END -> 0x77
        Key.PAGE_UP -> 0x74
        Key.PAGE_DOWN -> 0x79
        // Letters and digits are contiguous in Key.
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) {
            DIGITS[key.ordinal - Key.DIGIT_0.ordinal]
        } else {
            LETTERS[key.ordinal - Key.A.ordinal]
        }
    }
}
