package com.saubh.deskbuddy.server

interface ShareActuator {
    /** Last text this actuator wrote to the clipboard, so watchers can ignore the echo. */
    val lastWrittenText: String?

    fun setClipboard(text: String)

    /** Current clipboard text, or null when the clipboard holds no text or is unavailable. */
    fun getClipboard(): String?

    /** Text explicitly shared by the phone: goes to the clipboard and to the desktop inbox. */
    fun onTextShared(text: String)
}
