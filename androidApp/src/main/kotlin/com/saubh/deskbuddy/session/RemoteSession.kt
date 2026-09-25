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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Connection lifecycle: discovery, pairing, reconnect with backoff, and authenticated command
 * sending to one PC at a time. Every paired PC is remembered; [AutoConnector] uses that list to
 * reconnect without the user. Feature controllers observe [incoming] and call [send].
 */
class RemoteSession(
    private val scope: CoroutineScope,
    private val prefs: PairedDesktopPrefs,
    private val discovery: DesktopDiscovery,
    private val scanner: SubnetScanner,
    private val deviceName: String,
    private val client: DeskBuddyClient = DeskBuddyClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaGateway {
    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val SCAN_INITIAL_DELAY_MS = 3_000L
        const val SCAN_INTERVAL_MS = 15_000L
        const val SCAN_STALE_MS = 60_000L
    }

    private val _uiState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    private val _errors = MutableSharedFlow<ErrorKind>(extraBufferCapacity = 8)
    val errors: SharedFlow<ErrorKind> = _errors.asSharedFlow()

    /** Desktop-initiated messages: [Push] and [AppCatalog]. */
    private val _incoming = MutableSharedFlow<Message>(extraBufferCapacity = 256)
    override val incoming: SharedFlow<Message> = _incoming.asSharedFlow()

    override val connected: Flow<Boolean> = uiState.map { it is ConnectionUiState.Connected }.distinctUntilChanged()

    val savedDesktops: StateFlow<List<SavedDesktop>> =
        prefs.saved.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** PCs currently visible on the network (mDNS plus subnet sweep), whatever the UI state. */
    private val _discovered = MutableStateFlow<List<DiscoveredDesktop>>(emptyList())
    val discovered: StateFlow<List<DiscoveredDesktop>> = _discovered.asStateFlow()

    private var discoveryJob: Job? = null
    private var sessionJob: Job? = null

    @Volatile
    private var current: SavedDesktop? = null

    @Volatile
    private var endSession = false

    /** Set by an explicit Disconnect so auto-connect does not immediately undo it. */
    @Volatile
    var autoConnectPaused = false
        private set

    /** The subnet sweep is costly, so it only runs while the connect screen is on screen. */
    @Volatile
    var sweepAllowed = false

    private enum class SessionOutcome { FAILED, DROPPED, ENDED }

    val isConnected: Boolean get() = _uiState.value is ConnectionUiState.Connected

    /** Idle or discovering: nothing connected and no attempt in progress. */
    val isSearching: Boolean
        get() = _uiState.value.let { it is ConnectionUiState.Idle || it is ConnectionUiState.Discovering }

    private var mdnsFound: List<DiscoveredDesktop> = emptyList()
    private var scanFound: List<DiscoveredDesktop> = emptyList()
    private var scannedAtMs = 0L

    /** mDNS results as they arrive, plus a subnet sweep when mDNS stays silent. Idempotent. */
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
                    val stale = scanFound.isEmpty() || clock() - scannedAtMs > SCAN_STALE_MS
                    if (sweepAllowed && isSearching && mdnsFound.isEmpty() && stale) {
                        scanFound = scanner.scan()
                        scannedAtMs = clock()
                        publishDiscovered()
                    }
                    delay(SCAN_INTERVAL_MS)
                }
            }
        }
    }

    private fun publishDiscovered() {
        val all = (mdnsFound + scanFound).distinctBy { it.host }
        _discovered.value = all
        if (isSearching) _uiState.value = ConnectionUiState.Discovering(all)
    }

    /** Connects to an address picked by the user; a PC paired before reuses its token. */
    fun connect(name: String, host: String, port: Int) {
        val known = savedDesktops.value.firstOrNull { it.host == host }
            ?: savedDesktops.value.firstOrNull { it.isSamePc(discovered.value.firstOrNull { d -> d.host == host }?.id, name) }
        connect(known?.copy(host = host, port = port) ?: SavedDesktop(name, host, port, token = ""), quiet = false)
    }

    /** [quiet]: a failed first attempt is not reported (auto-connect probes in the background). */
    fun connect(target: SavedDesktop, quiet: Boolean) {
        sessionJob?.cancel()
        endSession = false
        autoConnectPaused = false
        current = target
        sessionJob = scope.launch { sessionLoop(target, quiet) }
    }

    fun forget(desktop: SavedDesktop) {
        scope.launch { prefs.forget(desktop) }
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

    /** User-initiated: also pauses auto-connect until the user connects again. */
    fun disconnect() {
        autoConnectPaused = true
        sessionJob?.cancel()
        sessionJob = null
        scope.launch { client.disconnect() }
        goIdle()
    }

    private fun goIdle() {
        _uiState.value = ConnectionUiState.Idle
        publishDiscovered()
    }

    private suspend fun sessionLoop(target: SavedDesktop, quiet: Boolean) {
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
                        if (!quiet) _errors.tryEmit(ErrorKind.CONNECTION_FAILED)
                        goIdle()
                        return
                    }
                    attempt++
                }
                SessionOutcome.DROPPED -> attempt++
                SessionOutcome.ENDED -> return
            }
        }
        _errors.tryEmit(ErrorKind.CONNECTION_LOST)
        goIdle()
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
                incomingFrames.collect { onMessage(it) }
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
            remember(target)
            _uiState.value = ConnectionUiState.Connected(target.name)
        }
    }

    /** Records the address that just worked and bumps the PC to the front of the auto-connect order. */
    private suspend fun remember(desktop: SavedDesktop) {
        val updated = desktop.copy(lastUsedAt = clock())
        current = updated
        prefs.save(updated)
    }

    private suspend fun onMessage(message: Message) {
        when (message) {
            is PairSuccess -> {
                val target = current ?: return
                remember(
                    target.copy(
                        token = message.token,
                        name = message.desktopName.ifEmpty { target.name },
                        id = message.desktopId.ifEmpty { target.id },
                    ),
                )
                _uiState.value = ConnectionUiState.Connected(current?.name ?: target.name)
            }
            is PairFailure -> {
                if (message.attemptsLeft <= 0) {
                    endSession = true
                    _errors.tryEmit(ErrorKind.PAIRING_REJECTED)
                    goIdle()
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
                // This PC no longer knows our token (e.g. "Unpair all"): forget it and re-pair on this connection.
                current?.let { prefs.forget(it) }
                current = current?.copy(token = "")
                _uiState.value = ConnectionUiState.Pairing(attemptsLeft = 3)
                client.send(PairRequest(deviceName = deviceName))
            }
            ErrorCode.BUSY -> {
                endSession = true
                _errors.tryEmit(ErrorKind.DESKTOP_BUSY)
                goIdle()
            }
            ErrorCode.UNSUPPORTED_OS -> _errors.tryEmit(ErrorKind.UNSUPPORTED_OS)
            ErrorCode.NOT_FOUND -> _errors.tryEmit(ErrorKind.APP_NOT_FOUND)
            ErrorCode.TRANSFER_FAILED -> _errors.tryEmit(ErrorKind.TRANSFER_FAILED)
            else -> _errors.tryEmit(ErrorKind.INTERNAL)
        }
    }
}
