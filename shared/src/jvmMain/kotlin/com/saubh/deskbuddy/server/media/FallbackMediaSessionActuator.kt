package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Picks the session backend at [start] (probing the primary spawns a process, so it never runs on
 * construction) and mirrors its flows, which consumers may already be collecting.
 */
class FallbackMediaSessionActuator(
    private val choosePrimary: () -> MediaSessionActuator?,
    private val fallback: () -> MediaSessionActuator,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MediaSessionActuator {

    private val _state = MutableStateFlow(NowPlayingCommand.NONE)
    override val state: StateFlow<NowPlayingCommand> = _state
    private val _artwork = MutableStateFlow<Artwork?>(null)
    override val artwork: StateFlow<Artwork?> = _artwork
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available

    @Volatile private var active: MediaSessionActuator? = null
    @Volatile private var mirror: Job? = null

    @Synchronized
    override fun start() {
        if (mirror?.isActive == true) return
        mirror = scope.launch {
            val chosen = active ?: (runCatching { choosePrimary() }.getOrNull() ?: fallback()).also { active = it }
            if (!isActive) {
                chosen.stop() // stop() ran while the primary was being probed
                return@launch
            }
            chosen.start()
            launch { chosen.state.collect { _state.value = it } }
            launch { chosen.artwork.collect { _artwork.value = it } }
            launch { chosen.available.collect { _available.value = it } }
        }
    }

    @Synchronized
    override fun stop() {
        mirror?.cancel()
        active?.stop()
        _available.value = false
    }

    override fun refresh() {
        active?.refresh()
    }

    override fun seek(positionMs: Long): Boolean = active?.seek(positionMs) ?: false
}
