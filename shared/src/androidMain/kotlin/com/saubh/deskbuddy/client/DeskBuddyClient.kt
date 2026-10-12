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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

class DeskBuddyClient {

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
    }

    private val http = HttpClient(CIO) {
        install(WebSockets) {
            // Notices a PC that vanished without closing the socket (sleep, power loss, Wi-Fi drop).
            pingIntervalMillis = 10_000
        }
    }
    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    /**
     * Opens the socket. Collect the returned flow to receive messages; it
     * completes (or throws) when the connection closes. Throws [IOException] when the handshake
     * does not finish in [HANDSHAKE_TIMEOUT_MS]: a socket lost mid-handshake (e.g. the phone cut the
     * app's traffic in the background) would otherwise leave the caller "Connecting" forever.
     */
    suspend fun connect(host: String, port: Int = Protocol.PORT): Flow<Message> {
        val ws = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { http.webSocketSession(host = host, port = port, path = Protocol.PATH) }
            ?: throw IOException("No handshake from $host:$port within $HANDSHAKE_TIMEOUT_MS ms")
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
