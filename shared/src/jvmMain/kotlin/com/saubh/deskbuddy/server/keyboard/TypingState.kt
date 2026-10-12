package com.saubh.deskbuddy.server.keyboard

/** What the PC's typing-to-phone overlay shows. */
sealed interface TypingState {
    data object Idle : TypingState
    data class Active(val phoneName: String) : TypingState
    /** Hotkey pressed with no phone connected; shown briefly. */
    data object NoPhone : TypingState
    /** The phone dropped while typing; shown briefly. */
    data object PhoneDisconnected : TypingState
}

enum class ExitReason { HOTKEY, ESCAPE, FOCUS_LOST, PHONE_DISCONNECTED, QUIT }
