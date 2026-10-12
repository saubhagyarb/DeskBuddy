package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.server.HostOs
import java.awt.event.KeyEvent

/**
 * AWT key events → protocol commands. Characters come from KEY_TYPED, so the OS keyboard layout,
 * dead keys and AltGr are already applied. A Ctrl/Alt + letter press is held back until its
 * KEY_TYPED shows whether it produced text (AltGr, which Windows reports as Ctrl+Alt) or a combo.
 *
 * With [mac], shortcuts follow Mac habits on their way to Android: ⌘ and ⌃ both act as Ctrl,
 * ⌥ + letter types its character (⌥ is the Mac's AltGr), and ⌥ + arrow/Backspace/Delete
 * becomes Ctrl, Android's word-wise modifier.
 */
class AwtKeyTranslator(private val mac: Boolean = HostOs.isMac()) {
    private var pending: KeyPressCommand? = null
    private var pendingKeyCode = 0

    fun translate(raw: RawKey): List<Command> {
        val out = mutableListOf<Command>()
        when (raw.type) {
            RawKey.Type.PRESSED -> {
                flush(out)
                val special = SPECIAL[raw.keyCode]
                val letter = letterOrDigit(raw.keyCode)
                when {
                    special != null -> out += KeyPressCommand(special, modifiers(raw, special))
                    letter != null && (raw.ctrl || raw.alt || (mac && raw.meta)) -> {
                        pending = KeyPressCommand(letter, modifiers(raw, letter))
                        pendingKeyCode = raw.keyCode
                    }
                }
            }
            RawKey.Type.TYPED -> {
                val held = pending
                pending = null
                val printable = raw.char != KeyEvent.CHAR_UNDEFINED && !raw.char.isISOControl()
                val altGr = raw.altGraph || (raw.ctrl && raw.alt) || (mac && raw.alt && !raw.meta)
                when {
                    printable && (held == null || altGr) -> out += KeyTextCommand(raw.char.toString())
                    held != null -> out += held
                }
            }
            RawKey.Type.RELEASED -> if (pending != null && raw.keyCode == pendingKeyCode) flush(out)
        }
        return out
    }

    fun reset() {
        pending = null
    }

    private fun flush(out: MutableList<Command>) {
        pending?.let { out += it }
        pending = null
    }

    private fun modifiers(raw: RawKey, key: Key): Set<KeyModifier> = buildSet {
        if (mac) {
            if (raw.ctrl || raw.meta || (raw.alt && key in WORD_WISE)) add(KeyModifier.CTRL)
            if (raw.alt && key !in WORD_WISE) add(KeyModifier.ALT)
        } else {
            if (raw.ctrl) add(KeyModifier.CTRL)
            if (raw.alt) add(KeyModifier.ALT)
        }
        if (raw.shift) add(KeyModifier.SHIFT)
    }

    private fun letterOrDigit(keyCode: Int): Key? = when (keyCode) {
        in KeyEvent.VK_A..KeyEvent.VK_Z -> Key.forChar('A' + (keyCode - KeyEvent.VK_A))
        in KeyEvent.VK_0..KeyEvent.VK_9 -> Key.forChar('0' + (keyCode - KeyEvent.VK_0))
        else -> null
    }

    private companion object {
        /** Keys where Mac ⌥ means "by word", which Android spells Ctrl. */
        val WORD_WISE = setOf(Key.LEFT, Key.RIGHT, Key.BACKSPACE, Key.DELETE)

        val SPECIAL = mapOf(
            KeyEvent.VK_ENTER to Key.ENTER,
            KeyEvent.VK_BACK_SPACE to Key.BACKSPACE,
            KeyEvent.VK_DELETE to Key.DELETE,
            KeyEvent.VK_TAB to Key.TAB,
            KeyEvent.VK_UP to Key.UP,
            KeyEvent.VK_DOWN to Key.DOWN,
            KeyEvent.VK_LEFT to Key.LEFT,
            KeyEvent.VK_RIGHT to Key.RIGHT,
            KeyEvent.VK_HOME to Key.HOME,
            KeyEvent.VK_END to Key.END,
            KeyEvent.VK_PAGE_UP to Key.PAGE_UP,
            KeyEvent.VK_PAGE_DOWN to Key.PAGE_DOWN,
        )
    }
}
