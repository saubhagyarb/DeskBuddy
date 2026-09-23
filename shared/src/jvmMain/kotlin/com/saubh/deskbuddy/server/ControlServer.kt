package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.DesktopInfo
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.WireCodec
import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ControlServer(
    private val store: PairedDeviceStore,
    private val actuators: Actuators,
    private val port: Int = Protocol.PORT,
    private val desktopName: String = LanInterfaces.hostName(),
) {
    private val _state = MutableStateFlow<ServerState>(ServerState.Waiting)
    val state: StateFlow<ServerState> = _state

    private val sessionActive = AtomicBoolean(false)
    private val outbound = AtomicReference<SendChannel<Message>?>(null)
    private var engine: EmbeddedServer<*, *>? = null

    private val listener = object : SessionListener {
        override fun onPairingStarted(pin: String) {
            _state.value = ServerState.Pairing(pin)
        }

        override fun onPairingEnded() {
            if (_state.value is ServerState.Pairing) _state.value = ServerState.Waiting
        }

        override fun onAuthenticated(deviceName: String) {
            _state.value = ServerState.Connected(deviceName)
        }
    }

    /**
     * Sends a desktop-initiated message to the authenticated phone. Returns false
     * (without queueing) when no authenticated session exists. Suspends when the
     * outbound buffer is full, which gives file transfers natural backpressure.
     */
    suspend fun push(message: Message): Boolean {
        if (_state.value !is ServerState.Connected) return false
        val channel = outbound.get() ?: return false
        return runCatching { channel.send(message) }.isSuccess
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
                    call.respondText(WireCodec.encodeInfo(DesktopInfo(desktopName, port)), ContentType.Application.Json)
                }
                webSocket(Protocol.PATH) {
                    if (!sessionActive.compareAndSet(false, true)) {
                        send(Frame.Text(WireCodec.encode(Ack(ok = false, error = ErrorCode.BUSY))))
                        close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "busy"))
                        return@webSocket
                    }
                    val session = ControlSession(store, actuators, listener)
                    // Single writer: replies and pushes both go through this channel.
                    val channel = Channel<Message>(Channel.BUFFERED)
                    outbound.set(channel)
                    val writer = launch {
                        for (message in channel) send(Frame.Text(WireCodec.encode(message)))
                    }
                    try {
                        for (frame in incoming) {
                            if (frame !is Frame.Text) continue
                            val reply = runCatching { session.onMessage(WireCodec.decode(frame.readText())) }
                                .getOrElse { SessionReply(Ack(ok = false, error = ErrorCode.INTERNAL)) }
                            reply.message?.let { channel.send(it) }
                            if (reply.closeConnection) {
                                channel.close()
                                writer.join()
                                close(CloseReason(CloseReason.Codes.NORMAL, "closed"))
                            }
                        }
                    } finally {
                        outbound.compareAndSet(channel, null)
                        channel.close()
                        writer.cancel()
                        sessionActive.set(false)
                        _state.value = ServerState.Waiting
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
        _state.value = ServerState.Waiting
    }
}
