package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Artwork(val artworkId: String, val mimeType: String, val base64: String)

/** Read-side of the OS media session (now playing) plus the one write it needs: seek. */
interface MediaSessionActuator {
    val state: StateFlow<NowPlayingCommand>
    val artwork: StateFlow<Artwork?>
    /** False while the backing helper is not running (non-Windows, PowerShell missing, crashed). */
    val available: StateFlow<Boolean>
    /** Returns false when there is no session or it cannot seek. Never throws. */
    fun seek(positionMs: Long): Boolean
    /** Re-emit current state and artwork (and restart the helper if it died). */
    fun refresh()
    fun start()
    fun stop()
}

class UnsupportedMediaSessionActuator : MediaSessionActuator {
    override val state = MutableStateFlow(NowPlayingCommand.NONE)
    override val artwork = MutableStateFlow<Artwork?>(null)
    override val available = MutableStateFlow(false)
    override fun seek(positionMs: Long): Boolean = false
    override fun refresh() = Unit
    override fun start() = Unit
    override fun stop() = Unit
}
