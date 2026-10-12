package com.saubh.deskbuddy

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGateway : MediaGateway {
    override val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val connectedState = MutableStateFlow(true)
    override val connected: Flow<Boolean> = connectedState
    val sent = mutableListOf<Command>()

    /** Number of upcoming sends that throw, as a socket closing under the sender does. */
    var failNextSends = 0
    override suspend fun send(command: Command): Boolean {
        if (failNextSends > 0) {
            failNextSends--
            throw IllegalStateException("socket closed")
        }
        sent += command
        return true
    }
}
