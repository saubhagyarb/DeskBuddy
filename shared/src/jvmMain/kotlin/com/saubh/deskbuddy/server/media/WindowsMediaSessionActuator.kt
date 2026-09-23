package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Runs the bundled PowerShell helper as a child process and mirrors its JSON lines
 * into flows. Restarted with backoff (1s, 2s, 4s) if it exits unexpectedly.
 */
class WindowsMediaSessionActuator(
    private val scriptResource: String = "/deskbuddy/media-session.ps1",
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaSessionActuator {

    private companion object {
        const val MAX_RESTARTS = 3
        const val RESULT_TIMEOUT_SECONDS = 2L
    }

    private val _state = MutableStateFlow(NowPlayingCommand.NONE)
    override val state: StateFlow<NowPlayingCommand> = _state
    private val _artwork = MutableStateFlow<Artwork?>(null)
    override val artwork: StateFlow<Artwork?> = _artwork
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available

    private val filter = NowPlayingFilter()
    private val results = LinkedBlockingQueue<HelperResult>()
    private val lock = Any()
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var restarts = 0
    @Volatile private var stopped = false

    override fun start() {
        stopped = false
        restarts = 0
        launchProcess()
    }

    override fun stop() {
        stopped = true
        send("quit")
        synchronized(lock) {
            process?.destroy()
            process = null
            writer = null
        }
        _available.value = false
    }

    override fun refresh() {
        val alive = synchronized(lock) { process?.isAlive == true }
        if (alive) {
            send("refresh")
        } else {
            restarts = 0
            launchProcess()
        }
    }

    override fun seek(positionMs: Long): Boolean {
        results.clear()
        if (!send("seek ${positionMs.coerceAtLeast(0)}")) return false
        return results.poll(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)?.ok ?: false
    }

    private fun send(command: String): Boolean = synchronized(lock) {
        val w = writer ?: return false
        runCatching { w.write(command); w.newLine(); w.flush() }.isSuccess
    }

    private fun launchProcess() {
        if (stopped) return
        val started = runCatching {
            val script = extractScript()
            ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        }.getOrNull()
        if (started == null) {
            _available.value = false
            return
        }
        synchronized(lock) {
            process = started
            writer = started.outputStream.bufferedWriter(StandardCharsets.UTF_8)
        }
        thread(isDaemon = true, name = "deskbuddy-media-helper") { readLoop(started) }
    }

    private fun readLoop(p: Process) {
        runCatching {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines -> lines.forEach(::handle) }
        }
        onExit(p)
    }

    private fun handle(line: String) {
        when (val event = MediaHelperProtocol.parse(line)) {
            is HelperState -> {
                _available.value = true
                val next = event.toNowPlaying()
                if (filter.shouldPublish(next, clock())) _state.value = next
            }
            is HelperArtwork -> _artwork.value = Artwork(event.artworkId, event.mimeType, event.data)
            is HelperResult -> results.offer(event)
            null -> Unit
        }
    }

    private fun onExit(p: Process) {
        synchronized(lock) {
            if (process === p) {
                process = null
                writer = null
            }
        }
        _available.value = false
        _state.value = NowPlayingCommand.NONE
        filter.reset()
        if (stopped || restarts >= MAX_RESTARTS) return
        Thread.sleep(1_000L shl restarts)
        restarts++
        launchProcess()
    }

    private fun extractScript(): Path {
        val bytes = javaClass.getResourceAsStream(scriptResource)?.use { it.readBytes() }
            ?: error("Missing helper resource $scriptResource")
        val file = Files.createTempFile("deskbuddy-media-", ".ps1")
        Files.write(file, bytes)
        file.toFile().deleteOnExit()
        return file
    }
}
