package com.saubh.deskbuddy

import com.saubh.deskbuddy.protocol.AppCatalog
import com.saubh.deskbuddy.protocol.ClipboardCommand
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.TextShareCommand
import com.saubh.deskbuddy.server.ActuatorFactory
import com.saubh.deskbuddy.server.ClipboardWatcher
import com.saubh.deskbuddy.server.ControlServer
import com.saubh.deskbuddy.server.DeskBuddyAdvertiser
import com.saubh.deskbuddy.server.DesktopIdentity
import com.saubh.deskbuddy.server.FileSender
import com.saubh.deskbuddy.server.PairedDeviceStore
import com.saubh.deskbuddy.server.ServerState
import com.saubh.deskbuddy.server.StartupRegistration
import com.saubh.deskbuddy.server.media.MediaStateBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Path

/** Owns the server and everything the desktop window can do; UI observes its flows. */
class DesktopController(private val scope: CoroutineScope) {

    sealed class InboxItem {
        abstract val receivedAt: Long
        data class Text(val text: String, override val receivedAt: Long) : InboxItem()
        data class File(val path: Path, override val receivedAt: Long) : InboxItem()
    }

    private val store = PairedDeviceStore()
    private val actuators = ActuatorFactory.create(
        onTextShared = { addToInbox(InboxItem.Text(it, System.currentTimeMillis())) },
        onFileReceived = { addToInbox(InboxItem.File(it, System.currentTimeMillis())) },
    )
    private val _showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Another launch of the app asked this one to show its window. */
    val showRequests: SharedFlow<Unit> = _showRequests.asSharedFlow()

    private val server = ControlServer(
        store = store,
        actuators = actuators,
        desktopId = DesktopIdentity().id,
        onShowRequested = { _showRequests.tryEmit(Unit) },
    )
    private val startup = StartupRegistration()
    val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val _startWithWindows = MutableStateFlow(false)
    val startWithWindows: StateFlow<Boolean> = _startWithWindows.asStateFlow()
    private val advertiser = DeskBuddyAdvertiser()
    private val fileSender = FileSender(server::push)
    private val broadcaster = MediaStateBroadcaster(actuators.mediaSession, actuators.audio)

    val serverState: StateFlow<ServerState> = server.state
    val mediaHelperAvailable: StateFlow<Boolean> = actuators.mediaSession.available
    val catalog: StateFlow<AppCatalog> = actuators.apps.catalog
    val autoSyncClipboard = MutableStateFlow(true)
    val receivedDirectory: Path = actuators.files.directory
    val port: Int = Protocol.PORT
    val hostName: String = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Unknown")
    val ipAddress: String = lanIpAddress()

    private val _inbox = MutableStateFlow<List<InboxItem>>(emptyList())
    val inbox: StateFlow<List<InboxItem>> = _inbox.asStateFlow()

    private val _transfer = MutableStateFlow<FileSender.Progress?>(null)
    val transfer: StateFlow<FileSender.Progress?> = _transfer.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var transferJob: Job? = null

    fun start() {
        server.start()
        if (isWindows) _startWithWindows.value = runCatching { startup.sync() }.getOrDefault(false)
        // mDNS failure must not kill the app — manual IP entry still works.
        runCatching { advertiser.start() }
        actuators.apps.current()
        scope.launch {
            ClipboardWatcher(actuators.share).changes().collect { text ->
                if (autoSyncClipboard.value) server.push(Push(ClipboardCommand(text)))
            }
        }
        scope.launch { catalog.drop(1).collect { server.push(it) } }
        actuators.onStateRequested = broadcaster::resendAll
        actuators.onAudioChanged = broadcaster::audioChanged
        actuators.mediaSession.start()
        scope.launch { broadcaster.commands().collect { server.push(Push(it)) } }
    }

    fun stop() {
        transferJob?.cancel()
        actuators.mediaSession.stop()
        advertiser.stop()
        server.stop()
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        scope.launch {
            if (!server.push(Push(TextShareCommand(text)))) notify("No phone is connected.")
        }
    }

    fun sendFile(path: Path) {
        if (transferJob?.isActive == true) {
            notify("A file is already being sent.")
            return
        }
        transferJob = scope.launch {
            val sent = fileSender.send(path) { _transfer.value = it }
            _transfer.value = null
            notify(if (sent) "Sent ${path.fileName}." else "Could not send ${path.fileName}. Is the phone connected?")
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        _transfer.value = null
    }

    fun refreshCatalog() = actuators.apps.refresh()

    fun addShortcut(id: String) {
        actuators.apps.addShortcut(id)
    }

    fun addCustomShortcut(path: Path) {
        actuators.apps.addCustom(path)
    }

    fun removeShortcut(id: String) = actuators.apps.removeShortcut(id)

    fun copyToClipboard(text: String) = actuators.share.setClipboard(text)

    fun unpairAll() = store.removeAll()

    fun setStartWithWindows(enabled: Boolean) {
        if (startup.setEnabled(enabled)) {
            _startWithWindows.value = enabled
        } else {
            notify("Could not change the Windows startup setting.")
        }
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun openReceivedFolder() {
        runCatching {
            java.nio.file.Files.createDirectories(receivedDirectory)
            Desktop.getDesktop().open(receivedDirectory.toFile())
        }.onFailure { notify("Could not open $receivedDirectory") }
    }

    private fun addToInbox(item: InboxItem) {
        _inbox.update { (listOf(item) + it).sortedByDescending { entry -> entry.receivedAt } }
    }

    private fun notify(message: String) {
        _notice.value = message
    }

    /** Best-effort LAN IP: routing trick, no packets are actually sent. */
    private fun lanIpAddress(): String = runCatching {
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 80)
            socket.localAddress.hostAddress ?: "unknown"
        }
    }.getOrDefault("unknown")
}
