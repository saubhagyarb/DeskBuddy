package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.server.UnsupportedOsException

/** Types into whichever window has keyboard focus on this PC. */
interface KeyboardActuator {
    fun type(text: String)
    fun press(key: Key, modifiers: Set<KeyModifier>)
}

class UnsupportedKeyboardActuator(private val osName: String) : KeyboardActuator {
    override fun type(text: String) = throw UnsupportedOsException(osName)
    override fun press(key: Key, modifiers: Set<KeyModifier>) = throw UnsupportedOsException(osName)
}
