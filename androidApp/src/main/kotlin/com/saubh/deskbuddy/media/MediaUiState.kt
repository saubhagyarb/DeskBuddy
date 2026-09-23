package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus

data class MediaUiState(
    val nowPlaying: NowPlayingCommand? = null,
    val receivedAtMs: Long = 0L,
    /** Interpolated while playing; the UI shows this, not [NowPlayingCommand.positionMs]. */
    val positionMs: Long = 0L,
    val artwork: ByteArray? = null,
    val artworkId: String? = null,
    /** Null until the PC reports audio state: device picker hidden, slider disabled. */
    val audio: AudioStateCommand? = null,
) {
    val status: PlaybackStatus get() = nowPlaying?.status ?: PlaybackStatus.NONE
    val isPlaying: Boolean get() = status == PlaybackStatus.PLAYING
    val durationMs: Long get() = nowPlaying?.durationMs ?: 0L
    val canSeek: Boolean get() = nowPlaying?.canSeek == true && status != PlaybackStatus.NONE && durationMs > 0
    val currentDevice: AudioDevice? get() = audio?.devices?.firstOrNull { it.isDefault }
}
