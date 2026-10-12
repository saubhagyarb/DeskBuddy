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
    /** Cheap "clipboard changed" counter (Windows sequence number); null means unknown, so read every tick. */
    private val changeToken: () -> Long? = { null },
) {
    fun changes(): Flow<String> = flow {
        var token = changeToken()
        var last = share.getClipboard()
        while (true) {
            delay(intervalMillis)
            val now = changeToken()
            if (now != null && now == token) continue
            // Advance the token only after a successful read: the copying app often still holds the
            // clipboard for a moment, and a skipped change would otherwise be lost for good.
            val current = share.getClipboard() ?: continue
            token = now
            if (current == last) continue
            last = current
            if (current != share.lastWrittenText) emit(current)
        }
    }
}
