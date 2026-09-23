package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.client.DeskBuddyClient
import com.saubh.deskbuddy.client.DesktopDiscovery
import com.saubh.deskbuddy.client.DiscoveredDesktop
import com.saubh.deskbuddy.client.SubnetScanner
import com.saubh.deskbuddy.prefs.PairedDesktopPrefs
import com.saubh.deskbuddy.prefs.SavedDesktop
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.AppCatalog
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.PairAttempt
import com.saubh.deskbuddy.protocol.PairFailure
import com.saubh.deskbuddy.protocol.PairRequest
import com.saubh.deskbuddy.protocol.PairSuccess
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.ui.ConnectionUiState
import com.saubh.deskbuddy.ui.ErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connection lifecycle for one paired desktop: discovery, pairing, reconnect
 * with backoff, and authenticated command sending. Feature controllers observe
 * [incoming] for desktop-initiated messages and call [send].
 */
class RemoteSession(
    private val scope: CoroutineScope,
    private val prefs: PairedDesktopPrefs,
    private val discovery: DesktopDiscovery,
    private val scanner: SubnetScanner,
    private val deviceName: String,
    private val client: DeskBuddyClient = DeskBuddyClient(),
) : MediaGateway {
    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val SCAN_INITIAL_DELAY_MS = 3_000L
        const val SCAN_INTERVAL_MS = 15_000L
    }

    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<ErrorKind>(extraBufferCapacity = 8)
    val errors: SharedFlow<ErrorKind> = _errors.asSharedFlow()

    /** Desktop-initiated messages: [Push] and [AppCatalog]. */
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<Message> = _incoming.asSharedFlow()

    override val connected: Flow<Boolean> = uiState.map { it is ConnectionUiState.Connected }.distinctUntilChanged()

    val savedDesktop: StateFlow<SavedDesktop?> =
        prefs.saved.stateIn(scope, SharingStarted.Eagerly, null)

    private var discoveryJob: Job? = null
    private var sessionJob: Job? = null

    @Volatile
    private var current: SavedDesktop? = null

    @Volatile
    private var endSession = false

    private enum class SessionOutcome { FAILED, DROPPED, ENDED }

    val isConnected: Boolean get() = _uiState.value is ConnectionUiState.Connected

    private var mdnsFound: List<DiscoveredDesktop> = emptyList()
    private var scanFound: List<DiscoveredDesktop> = emptyList()

    /** mDNS results as they arrive, plus a subnet sweep when mDNS stays silent. */
    fun startDiscovery() {
        if (discoveryJob != null) return
        discoveryJob = scope.launch {
            launch {
                discovery.discover()
                    .catch { /* discovery failure is non-fatal; the sweep and manual IP still work */ }
                    .collect { desktops ->
                        mdnsFound = desktops
                        publishDiscovered()
                    }
            }
            launch {
                delay(SCAN_INITIAL_DELAY_MS)
                while (true) {
                    if (isSearching && mdnsFound.isEmpty() && scanFound.isEmpty()) {
                        scanFound = scanner.scan()
                        publishDiscovered()
                    }
                    delay(SCAN_INTERVAL_MS)
                }
            }
        }
    }

    private val isSearching: Boolean
        get() = _uiState.value.let { it is ConnectionUiState.Idle || it is ConnectionUiState.Discovering }

    private fun publishDiscovered() {
        if (!isSearching) return
        _uiState.value = ConnectionUiState.Discovering((mdnsFound + scanFound).distinctBy { it.host })
    }

    fun connect(name: String, host: String, port: Int, token: String?) {
        sessionJob?.cancel()
        endSession = false
        val target = SavedDesktop(name, host, port, token.orEmpty())
        current = target
        sessionJob = scope.launch { sessionLoop(target) }
    }

    fun reconnectSaved() {
        val saved = savedDesktop.value ?: return
        connect(saved.name, saved.host, saved.port, saved.token)
    }

    fun submitPin(pin: String) {
        scope.launch { client.send(PairAttempt(pin)) }
    }

    /** Sends an authenticated command. Returns false (and reports) when not connected. */
    override suspend fun send(command: Command): Boolean {
        val token = current?.token?.takeIf { it.isNotEmpty() && isConnected }
        if (token == null) {
            reportError(ErrorKind.NOT_CONNECTED)
            return false
        }
        client.send(Envelope(token, command))
        return true
    }

    fun reportError(kind: ErrorKind) {
        _errors.tryEmit(kind)
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        scope.launch { client.disconnect() }
        _uiState.value = ConnectionUiState.Idle
    }

    private suspend fun sessionLoop(target: SavedDesktop) {
        var attempt = 0
        while (attempt <= MAX_RECONNECT_ATTEMPTS) {
            if (attempt == 0) {
                _uiState.value = ConnectionUiState.Connecting(target.name)
            } else {
                _uiState.value = ConnectionUiState.Reconnecting(attempt)
                delay(RECONNECT_BASE_DELAY_MS shl (attempt - 1)) // 1s, 2s, 4s
            }
            when (connectOnce(target)) {
                SessionOutcome.FAILED -> {
                    if (attempt == 0) {
                        _errors.tryEmit(ErrorKind.CONNECTION_FAILED)
                        _uiState.value = ConnectionUiState.Idle
                        return
                    }
                    attempt++
                }
                SessionOutcome.DROPPED -> attempt++
                SessionOutcome.ENDED -> return
            }
        }
        _errors.tryEmit(ErrorKind.CONNECTION_LOST)
        _uiState.value = ConnectionUiState.Idle
    }

    private suspend fun connectOnce(target: SavedDesktop): SessionOutcome {
        val incomingFrames = try {
            client.connect(target.host, target.port)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SessionOutcome.FAILED
        }
        try {
            onSocketOpened()
            // Frame decoding (incl. base64 file chunks) stays off the main thread.
            withContext(Dispatchers.Default) {
                incomingFrames.collect { onMessage(it, target) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // fall through — treated as a drop
        }
        return if (endSession) SessionOutcome.ENDED else SessionOutcome.DROPPED
    }

    private suspend fun onSocketOpened() {
        val target = current ?: return
        if (target.token.isEmpty()) {
            _uiState.value = ConnectionUiState.Pairing(attemptsLeft = 3)
            client.send(PairRequest(deviceName = deviceName))
        } else {
            _uiState.value = ConnectionUiState.Connected(target.name)
        }
    }

    private suspend fun onMessage(message: Message, target: SavedDesktop) {
        when (message) {
            is PairSuccess -> {
                val updated = target.copy(token = message.token)
                current = updated
                prefs.save(updated)
                _uiState.value = ConnectionUiState.Connected(updated.name)
            }
            is PairFailure -> {
                if (message.attemptsLeft <= 0) {
                    endSession = true
                    _errors.tryEmit(ErrorKind.PAIRING_REJECTED)
                    _uiState.value = ConnectionUiState.Idle
                } else {
                    _uiState.value = ConnectionUiState.Pairing(message.attemptsLeft)
                }
            }
            is Ack -> handleAck(message)
            is Push, is AppCatalog -> _incoming.emit(message)
            else -> Unit
        }
    }

    private suspend fun handleAck(ack: Ack) {
        if (ack.ok) return
        when (ack.error) {
            ErrorCode.AUTH_REQUIRED -> {
                // Stored token no longer valid — re-pair on this same connection.
                current = current?.copy(token = "")
                prefs.clear()
                _uiState.value = ConnectionUiState.Pairing(attemptsLeft = 3)
                client.send(PairRequest(deviceName = deviceName))
            }
            ErrorCode.BUSY -> {
                endSession = true
                _errors.tryEmit(ErrorKind.DESKTOP_BUSY)
                _uiState.value = ConnectionUiState.Idle
            }
            ErrorCode.UNSUPPORTED_OS -> _errors.tryEmit(ErrorKind.UNSUPPORTED_OS)
            ErrorCode.NOT_FOUND -> _errors.tryEmit(ErrorKind.APP_NOT_FOUND)
            ErrorCode.TRANSFER_FAILED -> _errors.tryEmit(ErrorKind.TRANSFER_FAILED)
            else -> _errors.tryEmit(ErrorKind.INTERNAL)
        }
    }
}
