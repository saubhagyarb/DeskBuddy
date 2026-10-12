package com.saubh.deskbuddy.server.keyboard

import java.awt.event.KeyEvent

/** The parts of an AWT `KeyEvent` the translator needs; a plain value so tests need no AWT components. */
data class RawKey(
    val type: Type,
    val keyCode: Int,
    val char: Char = KeyEvent.CHAR_UNDEFINED,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val altGraph: Boolean = false,
    /** ⌘ on a Mac; ignored elsewhere. */
    val meta: Boolean = false,
) {
    enum class Type { PRESSED, RELEASED, TYPED }
}
