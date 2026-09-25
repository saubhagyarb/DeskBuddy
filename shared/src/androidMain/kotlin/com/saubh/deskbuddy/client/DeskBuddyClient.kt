package com.saubh.deskbuddy.client

import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

class DeskBuddyClient {

    private val http = HttpClient(CIO) {
        install(WebSockets) {
            // Notices a PC that vanished without closing the socket (sleep, power loss, Wi-Fi drop).
            pingIntervalMillis = 10_000
        }
    }
    private var session: DefaultClientWebSocketSession? = null

    /**
     * Opens the socket. Collect the returned flow to receive messages; it
     * completes (or throws) when the connection closes.
     */
    suspend fun connect(host: String, port: Int = Protocol.PORT): Flow<Message> {
        val ws = http.webSocketSession(host = host, port = port, path = Protocol.PATH)
        session = ws
        return flow {
            for (frame in ws.incoming) {
                if (frame is Frame.Text) WireCodec.decodeOrNull(frame.readText())?.let { emit(it) }
            }
        }.onCompletion { session = null }
    }

    suspend fun send(message: Message) {
        session?.send(Frame.Text(WireCodec.encode(message)))
    }

    suspend fun disconnect() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
        session = null
    }
}
