package com.saubh.deskbuddy.server

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/**
 * Clipboard access through AWT, which works on Windows and X11 (and Wayland via XWayland).
 * [inbox] receives text the phone shared explicitly.
 */
class AwtShareActuator(private val inbox: (String) -> Unit = {}) : ShareActuator {

    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    @Volatile
    override var lastWrittenText: String? = null
        private set

    override fun setClipboard(text: String) {
        lastWrittenText = text
        clipboard.setContents(StringSelection(text), null)
    }

    override fun getClipboard(): String? = runCatching {
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()

    override fun onTextShared(text: String) {
        setClipboard(text)
        inbox(text)
    }
}
