package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** One `KEYBDINPUT`: virtual key, scan code (UTF-16 unit for Unicode input) and flags. */
data class KeyInput(val vk: Int, val scan: Int, val flags: Int)

/** Pure builder of `SendInput` batches, so key mapping and ordering are testable without Win32. */
object SendInputPlan {
    const val KEYEVENTF_EXTENDEDKEY = 0x0001
    const val KEYEVENTF_KEYUP = 0x0002
    const val KEYEVENTF_UNICODE = 0x0004

    private val MODIFIER_ORDER = listOf(KeyModifier.CTRL, KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.META)

    /** Each UTF-16 unit as a Unicode down/up pair; surrogate pairs are sent as two units, as Windows expects. */
    fun forText(text: String): List<KeyInput> = text.flatMap { unit ->
        listOf(KeyInput(0, unit.code, KEYEVENTF_UNICODE), KeyInput(0, unit.code, KEYEVENTF_UNICODE or KEYEVENTF_KEYUP))
    }

    /** Modifiers down, key down/up, modifiers up in reverse: one batch, so no other input interleaves. */
    fun forKey(key: Key, modifiers: Set<KeyModifier>): List<KeyInput> {
        val mods = MODIFIER_ORDER.filter { it in modifiers }.map(::vkOf)
        val (vk, extended) = vkOf(key)
        val flag = if (extended) KEYEVENTF_EXTENDEDKEY else 0
        return mods.map { (mvk, mext) -> KeyInput(mvk, 0, if (mext) KEYEVENTF_EXTENDEDKEY else 0) } +
            KeyInput(vk, 0, flag) + KeyInput(vk, 0, flag or KEYEVENTF_KEYUP) +
            mods.reversed().map { (mvk, mext) -> KeyInput(mvk, 0, (if (mext) KEYEVENTF_EXTENDEDKEY else 0) or KEYEVENTF_KEYUP) }
    }

    private fun vkOf(modifier: KeyModifier): Pair<Int, Boolean> = when (modifier) {
        KeyModifier.CTRL -> 0x11 to false
        KeyModifier.ALT -> 0x12 to false
        KeyModifier.SHIFT -> 0x10 to false
        KeyModifier.META -> 0x5B to true
    }

    private fun vkOf(key: Key): Pair<Int, Boolean> = when (key) {
        Key.ENTER -> 0x0D to false
        Key.BACKSPACE -> 0x08 to false
        Key.DELETE -> 0x2E to true
        Key.TAB -> 0x09 to false
        Key.ESCAPE -> 0x1B to false
        Key.SPACE -> 0x20 to false
        Key.UP -> 0x26 to true
        Key.DOWN -> 0x28 to true
        Key.LEFT -> 0x25 to true
        Key.RIGHT -> 0x27 to true
        Key.HOME -> 0x24 to true
        Key.END -> 0x23 to true
        Key.PAGE_UP -> 0x21 to true
        Key.PAGE_DOWN -> 0x22 to true
        // Letters and digits are contiguous in Key, as are VK_A..VK_Z and VK_0..VK_9.
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) {
            0x30 + (key.ordinal - Key.DIGIT_0.ordinal) to false
        } else {
            0x41 + (key.ordinal - Key.A.ordinal) to false
        }
    }
}
