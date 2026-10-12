package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** Pure `xdotool` command lines (X11). */
object XdotoolArgs {
    private val MODIFIER_ORDER = listOf(KeyModifier.CTRL, KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.META)
    private const val BASE_TIMEOUT_MS = 2_000L
    private const val TYPE_DELAY_MS = 1
    private const val PER_CHAR_BUDGET_MS = 5L

    /** xdotool waits 12 ms per character by default; 1 ms keeps a 1000-char chunk well inside [timeoutMs]. */
    fun forText(text: String): List<String> = listOf("xdotool", "type", "--clearmodifiers", "--delay", TYPE_DELAY_MS.toString(), "--", text)

    /** Time to let one `xdotool type` run: a fixed start-up budget plus a margin per character. */
    fun timeoutMs(text: String): Long = BASE_TIMEOUT_MS + text.length * PER_CHAR_BUDGET_MS

    fun forKey(key: Key, modifiers: Set<KeyModifier>): List<String> {
        val mods = MODIFIER_ORDER.filter { it in modifiers }.map {
            when (it) {
                KeyModifier.CTRL -> "ctrl"
                KeyModifier.ALT -> "alt"
                KeyModifier.SHIFT -> "shift"
                KeyModifier.META -> "super"
            }
        }
        return listOf("xdotool", "key", "--clearmodifiers", (mods + keysym(key)).joinToString("+"))
    }

    private fun keysym(key: Key): String = when (key) {
        Key.ENTER -> "Return"
        Key.BACKSPACE -> "BackSpace"
        Key.DELETE -> "Delete"
        Key.TAB -> "Tab"
        Key.ESCAPE -> "Escape"
        Key.SPACE -> "space"
        Key.UP -> "Up"
        Key.DOWN -> "Down"
        Key.LEFT -> "Left"
        Key.RIGHT -> "Right"
        Key.HOME -> "Home"
        Key.END -> "End"
        Key.PAGE_UP -> "Prior"
        Key.PAGE_DOWN -> "Next"
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) key.name.removePrefix("DIGIT_") else key.name.lowercase()
    }
}
