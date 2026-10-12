package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/** System-wide now playing on macOS through [MediaRemoteAdapter]; restarts the stream with backoff (1 s, 2 s, 4 s). */
class MacMediaRemoteSessionActuator(
    private val adapter: MediaRemoteAdapter,
    private val clock: () -> Long = System::currentTimeMillis,
) : MediaSessionActuator {

    private companion object {
        const val MAX_RESTARTS = 3
    }

    private val _state = MutableStateFlow(NowPlayingCommand.NONE)
    override val state: StateFlow<NowPlayingCommand> = _state
    private val _artwork = MutableStateFlow<Artwork?>(null)
    override val artwork: StateFlow<Artwork?> = _artwork
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available

    private val filter = NowPlayingFilter()

    // Guarded by [lock]. [generation] is bumped by start/stop/refresh-relaunch, which abandons any reader
    // thread (and any pending backoff relaunch) that belongs to an earlier generation.
    private val lock = Any()
    private var process: Process? = null
    private var generation = 0
    private var restarts = 0
    private var stopped = false
    /** Track whose cover is in [_artwork]; null when the current track has none yet. */
    @Volatile private var artworkTrackKey: String? = null

    override fun start() {
        val gen = synchronized(lock) {
            stopped = false
            restarts = 0
            discardProcess()
            ++generation
        }
        launchProcess(gen)
    }

    override fun stop() {
        synchronized(lock) {
            stopped = true
            generation++
            discardProcess()
        }
        _available.value = false
    }

    override fun refresh() {
        val gen = synchronized(lock) {
            if (process?.isAlive == true) {
                null
            } else {
                restarts = 0
                ++generation
            }
        }
        if (gen == null) filter.reset() else launchProcess(gen)
    }

    override fun seek(positionMs: Long): Boolean = _state.value.canSeek && adapter.seek(positionMs)

    /** Caller holds [lock]. */
    private fun discardProcess() {
        process?.destroy()
        process = null
    }

    /** Check-and-set under [lock]: a no-op when stopped, superseded by a newer [gen], or a live process exists. */
    private fun launchProcess(gen: Int) {
        val started = synchronized(lock) {
            if (stopped || gen != generation || process?.isAlive == true) return
            runCatching { adapter.startStream() }.getOrNull()?.also { process = it }
        }
        if (started == null) {
            _available.value = false
            return
        }
        thread(isDaemon = true, name = "konnekt-mediaremote") { readLoop(started, gen) }
    }

    private fun readLoop(p: Process, gen: Int) {
        val stream = MediaRemoteStream(clock)
        runCatching {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach { line -> stream.accept(line)?.let { handle(p, it) } }
            }
        }
        onExit(p, gen)
    }

    private fun handle(p: Process, sample: MediaRemoteSample) {
        synchronized(lock) {
            if (process !== p) return
            restarts = 0
        }
        _available.value = true
        if (sample.trackKey != artworkTrackKey) artworkTrackKey = null
        val art = sample.artworkBase64
        val mime = sample.artworkMimeType
        if (art != null && mime != null && artworkTrackKey == null && sample.trackKey.isNotEmpty()) {
            // Cover before the state that names it, as the Windows helper does.
            _artwork.value = Artwork(MacNowPlayingSample.artworkId(sample.trackKey), mime, art)
            artworkTrackKey = sample.trackKey
        }
        val artworkId = if (artworkTrackKey == sample.trackKey) MacNowPlayingSample.artworkId(sample.trackKey) else null
        val next = sample.state.copy(artworkId = artworkId)
        if (filter.shouldPublish(next, clock())) _state.value = next
    }

    private fun onExit(p: Process, gen: Int) {
        val delayMs = synchronized(lock) {
            // Superseded by start/stop/refresh: the newer session owns the state, so leave it alone.
            if (process !== p || gen != generation) return
            process = null
            if (stopped || restarts >= MAX_RESTARTS) null else 1_000L shl restarts
        }
        _available.value = false
        _state.value = NowPlayingCommand.NONE
        filter.reset()
        artworkTrackKey = null
        if (delayMs == null) return
        Thread.sleep(delayMs)
        val retryGen = synchronized(lock) {
            if (stopped || gen != generation) return
            restarts++
            gen
        }
        launchProcess(retryGen)
    }
}
