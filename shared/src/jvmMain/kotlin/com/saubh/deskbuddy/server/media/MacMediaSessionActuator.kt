package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.MediaSessionActuator
import com.saubh.deskbuddy.server.Osascript
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Now playing on macOS. The system-wide MediaRemote API is closed to other apps since macOS 15.4,
 * so a bundled JXA loop reads Music and Spotify instead (each asks for Automation permission once).
 * Cover art is fetched here on track change; the helper is restarted with backoff (1s, 2s, 4s).
 */
class MacMediaSessionActuator(
    private val scriptResource: String = "/deskbuddy/mac-now-playing.js",
    private val artworkFetcher: MacArtworkFetcher = MacArtworkFetcher(),
    private val runJxa: (String, List<String>) -> String? = { script, args ->
        Osascript.run(script, args, Osascript.Language.JAVASCRIPT)
    },
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaSessionActuator {

    private companion object {
        const val MAX_RESTARTS = 3
        val PLAYERS = setOf("Music", "Spotify")

        /** Never launches the player: a closed app has nothing to seek. */
        const val SEEK_SCRIPT = """
function run(argv) {
  var app = Application(argv[0]);
  if (!app.running()) return 'false';
  app.playerPosition = Number(argv[1]) / 1000;
  return 'true';
}
"""
    }

    private val _state = MutableStateFlow(NowPlayingCommand.NONE)
    override val state: StateFlow<NowPlayingCommand> = _state
    private val _artwork = MutableStateFlow<Artwork?>(null)
    override val artwork: StateFlow<Artwork?> = _artwork
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available

    private val filter = NowPlayingFilter()
    private val lock = Any()
    private var process: Process? = null
    private var restarts = 0
    @Volatile private var stopped = false
    @Volatile private var currentApp: String? = null
    /** Track whose artwork was last looked up; null forces a new lookup on the next sample. */
    @Volatile private var artworkTrackKey: String? = null
    @Volatile private var artworkId: String? = null

    override fun start() {
        stopped = false
        restarts = 0
        launchProcess()
    }

    override fun stop() {
        stopped = true
        synchronized(lock) {
            process?.destroy()
            process = null
        }
        _available.value = false
    }

    override fun refresh() {
        val alive = synchronized(lock) { process?.isAlive == true }
        if (alive) {
            // A missing cover (e.g. the network was down) is retried; the state itself re-arrives every second.
            if (_artwork.value == null) artworkTrackKey = null
            filter.reset()
        } else {
            restarts = 0
            launchProcess()
        }
    }

    override fun seek(positionMs: Long): Boolean {
        val app = currentApp?.takeIf { it in PLAYERS } ?: return false
        if (_state.value.status == PlaybackStatus.NONE) return false
        return runJxa(SEEK_SCRIPT, listOf(app, positionMs.coerceAtLeast(0).toString())) == "true"
    }

    private fun launchProcess() {
        if (stopped) return
        val started = runCatching { Osascript.start(readScript(), language = Osascript.Language.JAVASCRIPT) }.getOrNull()
        if (started == null) {
            _available.value = false
            return
        }
        synchronized(lock) { process = started }
        thread(isDaemon = true, name = "deskbuddy-mac-now-playing") { readLoop(started) }
    }

    private fun readLoop(p: Process) {
        runCatching {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines -> lines.forEach(::handle) }
        }
        onExit(p)
    }

    private fun handle(line: String) {
        val sample = MacNowPlayingSample.parse(line) ?: return
        _available.value = true
        currentApp = sample.state.app
        if (sample.trackKey != artworkTrackKey) {
            artworkTrackKey = sample.trackKey
            artworkId = if (sample.trackKey.isEmpty()) null else loadArtwork(sample)
        }
        val next = sample.state.toNowPlaying().copy(artworkId = artworkId)
        if (filter.shouldPublish(next, clock())) _state.value = next
    }

    /** Publishes the cover before the state that references it, as the Windows helper does. */
    private fun loadArtwork(sample: MacNowPlayingSample): String? {
        val (mime, base64) = runCatching { artworkFetcher.fetch(sample.state.app.orEmpty(), sample.artworkUrl) }.getOrNull()
            ?: return null
        return MacNowPlayingSample.artworkId(sample.trackKey).also { id -> _artwork.value = Artwork(id, mime, base64) }
    }

    private fun onExit(p: Process) {
        synchronized(lock) {
            if (process === p) process = null
        }
        _available.value = false
        _state.value = NowPlayingCommand.NONE
        filter.reset()
        artworkTrackKey = null
        if (stopped || restarts >= MAX_RESTARTS) return
        Thread.sleep(1_000L shl restarts)
        restarts++
        launchProcess()
    }

    private fun readScript(): String =
        javaClass.getResourceAsStream(scriptResource)?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
            ?: error("Missing helper resource $scriptResource")
}
