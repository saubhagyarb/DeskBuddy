package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/** What feature controllers need from the session; lets them be unit-tested with a fake. */
interface MediaGateway {
    val incoming: SharedFlow<Message>
    val connected: Flow<Boolean>
    suspend fun send(command: Command): Boolean
}
