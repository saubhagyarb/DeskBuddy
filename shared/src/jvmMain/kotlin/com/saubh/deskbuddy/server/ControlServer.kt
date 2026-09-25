package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopInfo
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WebSocket control server. Several phones can be connected at once; desktop-initiated
 * messages go to every authenticated phone. Only one PIN pairing runs at a time.
 */
class ControlServer(
    private val store: PairedDeviceStore,
    private val actuators: Actuators,
    private val port: Int = Protocol.PORT,
    private val desktopName: String = LanInterfaces.hostName(),
    private val desktopId: String = "",
    /** A second copy of the app asks the running one (over loopback) to show its window. */
    private val onShowRequested: () -> Unit = {},
) {
    private companion object {
        const val PUSH_TIMEOUT_MS = 5_000L
    }

    private class Connection(val socket: DefaultWebSocketServerSession, val outbound: Channel<Message>) {
        @Volatile var deviceName: String? = null
        @Volatile var token: String? = null
    }

    private val _state = MutableStateFlow<ServerState>(ServerState.Waiting)
    val state: StateFlow<ServerState> = _state

    private val connections = CopyOnWriteArrayList<Connection>()
    private val lock = Any()
    private var pairingPin: String? = null
    private var pairingOwner: Connection? = null
    private var engine: EmbeddedServer<*, *>? = null

    /**
     * Sends a desktop-initiated message to every authenticated phone. Returns false when none
     * received it. Suspends while a phone's buffer is full (file transfer backpressure), but a
     * phone that stops reading is skipped after [PUSH_TIMEOUT_MS] so it cannot stall the others.
     */
    suspend fun push(message: Message): Boolean {
        var delivered = false
        for (connection in connections) {
            if (connection.deviceName == null) continue
            val sent = withTimeoutOrNull(PUSH_TIMEOUT_MS) { runCatching { connection.outbound.send(message) }.isSuccess }
            if (sent == true) delivered = true
        }
        return delivered
    }

    fun start() {
        engine = embeddedServer(CIO, port = port) {
            install(WebSockets) {
                pingPeriodMillis = 15_000
                timeoutMillis = 30_000
            }
            routing {
                // Lets the phone's subnet sweep confirm an open port is DeskBuddy and learn our name.
                get("/info") {
                    call.respondText(WireCodec.encodeInfo(DesktopInfo(desktopName, port, desktopId)), ContentType.Application.Json)
                }
                get("/show") {
                    val local = runCatching { InetAddress.getByName(call.request.local.remoteAddress).isLoopbackAddress }.getOrDefault(false)
                    if (local) onShowRequested()
                    call.respond(if (local) HttpStatusCode.NoContent else HttpStatusCode.Forbidden)
                }
                webSocket(Protocol.PATH) {
                    // Single writer per phone: replies and pushes both go through this channel.
                    val connection = Connection(this, Channel(Channel.BUFFERED))
                    connections += connection
                    val session = ControlSession(store, actuators, listenerFor(connection), desktopName = desktopName, desktopId = desktopId)
                    val writer = launch {
                        for (message in connection.outbound) send(Frame.Text(WireCodec.encode(message)))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val reply = runCatching { session.onMessage(WireCodec.decode(frame.readText())) }
                                .getOrElse { SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL)) }
                            reply.message?.let { connection.outbound.send(it) }
                            if (reply.closeConnection) {
                                connection.outbound.close()
                                writer.join()
                                close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
                            }
                        }
                    } finally {
                        connections -= connection
                        connection.outbound.close()
                        writer.cancel()
                        synchronized(lock) {
                            if (pairingOwner === connection) {
                                pairingOwner = null
                                pairingPin = null
                            }
                        }
                        publish()
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
        connections.clear()
        _state.value = ServerState.Waiting
    }

    private fun listenerFor(connection: Connection) = object : SessionListener {
        override fun onPairingStarted(pin: String) {
            synchronized(lock) {
                pairingPin = pin
                pairingOwner = connection
            }
            publish()
        }

        override fun onPairingEnded() {
            synchronized(lock) {
                if (pairingOwner === connection) {
                    pairingPin = null
                    pairingOwner = null
                }
            }
            publish()
        }

        override fun onAuthenticated(deviceName: String, token: String) {
            if (connection.token == token) return
            connection.deviceName = deviceName
            connection.token = token
            // The same phone reconnected (e.g. after Wi-Fi dropped); retire its stale socket.
            connections.filter { it !== connection && it.token == token }.forEach { stale ->
                stale.deviceName = null
                stale.socket.launch { stale.socket.close(CloseReason(CloseReason.Codes.NORMAL, "replaced")) }
            }
            publish()
        }
    }

    private fun publish() {
        val pin = synchronized(lock) { pairingPin }
        val devices = connections.mapNotNull { it.deviceName }
        _state.value = when {
            pin != null -> ServerState.Pairing(pin)
            devices.isNotEmpty() -> ServerState.Connected(devices)
            else -> ServerState.Waiting
        }
    }
}
