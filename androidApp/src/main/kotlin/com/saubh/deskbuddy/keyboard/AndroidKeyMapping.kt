package com.saubh.deskbuddy.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier

/** Protocol keys → Android key codes / meta state, and the editor action Enter should trigger. */
object AndroidKeyMapping {
    fun keyCode(key: Key): Int = when (key) {
        Key.ENTER -> KeyEvent.KEYCODE_ENTER
        Key.BACKSPACE -> KeyEvent.KEYCODE_DEL
        Key.DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
        Key.TAB -> KeyEvent.KEYCODE_TAB
        Key.ESCAPE -> KeyEvent.KEYCODE_ESCAPE
        Key.SPACE -> KeyEvent.KEYCODE_SPACE
        Key.UP -> KeyEvent.KEYCODE_DPAD_UP
        Key.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        Key.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        Key.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        Key.HOME -> KeyEvent.KEYCODE_MOVE_HOME
        Key.END -> KeyEvent.KEYCODE_MOVE_END
        Key.PAGE_UP -> KeyEvent.KEYCODE_PAGE_UP
        Key.PAGE_DOWN -> KeyEvent.KEYCODE_PAGE_DOWN
        // Key's letters and digits are contiguous, as are Android's KEYCODE_A..Z and KEYCODE_0..9.
        else -> if (key.ordinal >= Key.DIGIT_0.ordinal) {
            KeyEvent.KEYCODE_0 + (key.ordinal - Key.DIGIT_0.ordinal)
        } else {
            KeyEvent.KEYCODE_A + (key.ordinal - Key.A.ordinal)
        }
    }

    fun metaState(modifiers: Set<KeyModifier>): Int = modifiers.fold(0) { meta, modifier ->
        meta or when (modifier) {
            KeyModifier.CTRL -> KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            KeyModifier.ALT -> KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
            KeyModifier.SHIFT -> KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            KeyModifier.META -> KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        }
    }

    /** The editor action Enter should perform (Send, Search, Go…), or null to send a plain Enter key. */
    fun enterAction(imeOptions: Int): Int? {
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        return action.takeUnless { it == EditorInfo.IME_ACTION_NONE || it == EditorInfo.IME_ACTION_UNSPECIFIED }
    }
}
