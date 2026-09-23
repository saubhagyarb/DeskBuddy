package com.saubh.deskbuddy.protocol

object Protocol {
    const val PORT = 8765
    const val PATH = "/control"

    /** mDNS service type. Android NSD wants no trailing "local." — JmDNS adds "local." itself. */
    const val SERVICE_TYPE = "_deskbuddy._tcp."
    const val PAIRING_TIMEOUT_MILLIS = 60_000L
    const val MAX_PIN_ATTEMPTS = 3

    /** Raw bytes per file chunk before base64 encoding. */
    const val FILE_CHUNK_BYTES = 32 * 1024

    /** How often the desktop polls its clipboard for changes to push to the phone. */
    const val CLIPBOARD_POLL_MILLIS = 1_000L

    /** Folder name (under Downloads) where received files are saved on both platforms. */
    const val RECEIVED_FOLDER_NAME = "DeskBuddy"
}
