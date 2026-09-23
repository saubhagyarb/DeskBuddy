package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Protocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Polls the desktop clipboard and emits new text the user copied locally.
 * Text written by [ShareActuator.setClipboard] (i.e. received from the phone)
 * is not re-emitted, which prevents ping-pong between the two devices.
 */
class ClipboardWatcher(
    private val share: ShareActuator,
    private val intervalMillis: Long = Protocol.CLIPBOARD_POLL_MILLIS,
) {
    fun changes(): Flow<String> = flow {
        var last = share.getClipboard()
        while (true) {
            delay(intervalMillis)
            val current = share.getClipboard() ?: continue
            if (current == last) continue
            last = current
            if (current != share.lastWrittenText) emit(current)
        }
    }
}
