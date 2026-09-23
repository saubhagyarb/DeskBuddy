package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.MediaSeekCommand
import com.saubh.deskbuddy.protocol.MediaStateRequestCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.Push
import com.saubh.deskbuddy.protocol.SetAudioDeviceCommand
import com.saubh.deskbuddy.protocol.SetVolumeCommand
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Mirrors the desktop's media/audio pushes and sends seek, volume and output-device commands. */
class MediaController(
    private val gateway: MediaGateway,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val decode: (String) -> ByteArray? = ::decodeBase64,
    private val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private companion object {
        const val TICK_MS = 500L
        const val VOLUME_THROTTLE_MS = 100L
        const val AUDIO_HOLD_MS = 1_000L
    }

    private val _state = MutableStateFlow(MediaUiState())
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var volumeSentAt = Long.MIN_VALUE / 2
    private var volumeHoldUntil = 0L
    private var muteHoldUntil = 0L

    /** Artwork that arrived before the now-playing push naming it (the helper sends artwork first). */
    private var pendingArtwork: Pair<String, ByteArray>? = null

    init {
        scope.launch { gateway.incoming.filterIsInstance<Push>().collect { onPush(it.command) } }
        scope.launch { gateway.connected.collect { if (it) request() } }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                _state.update { s -> if (s.isPlaying) s.copy(positionMs = interpolate(s, clock())) else s }
            }
        }
    }

    fun request() {
        scope.launch { gateway.send(MediaStateRequestCommand) }
    }

    fun seek(positionMs: Long) {
        val upper = _state.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = positionMs.coerceIn(0, upper)
        val now = clock()
        _state.update { s -> s.copy(positionMs = target, receivedAtMs = now, nowPlaying = s.nowPlaying?.copy(positionMs = target)) }
        scope.launch { gateway.send(MediaSeekCommand(target)) }
    }

    /** Throttled while dragging; [final] forces a send. Either way the PC's stale pushes are ignored briefly. */
    fun setVolume(percent: Int, final: Boolean) {
        val volume = percent.coerceIn(0, 100)
        val now = clock()
        volumeHoldUntil = now + AUDIO_HOLD_MS
        _state.update { s -> s.copy(audio = s.audio?.copy(volume = volume)) }
        if (!final && now - volumeSentAt < VOLUME_THROTTLE_MS) return
        volumeSentAt = now
        scope.launch { gateway.send(SetVolumeCommand(volume)) }
    }

    fun toggleMute() {
        muteHoldUntil = clock() + AUDIO_HOLD_MS
        _state.update { s -> s.copy(audio = s.audio?.let { it.copy(muted = !it.muted) }) }
        scope.launch { gateway.send(MediaCommand(MediaAction.MUTE_TOGGLE)) }
    }

    fun selectDevice(id: String) {
        _state.update { s ->
            s.copy(audio = s.audio?.let { audio -> audio.copy(devices = audio.devices.map { it.copy(isDefault = it.id == id) }) })
        }
        scope.launch { gateway.send(SetAudioDeviceCommand(id)) }
    }

    private suspend fun onPush(command: Command) {
        when (command) {
            is NowPlayingCommand -> onNowPlaying(command)
            is MediaArtworkCommand -> onArtwork(command)
            is AudioStateCommand -> onAudioState(command)
            else -> Unit
        }
    }

    private fun onNowPlaying(command: NowPlayingCommand) {
        val pending = pendingArtwork?.takeIf { it.first == command.artworkId }
        _state.update { s ->
            val sameArt = command.artworkId != null && command.artworkId == s.artworkId
            s.copy(
                nowPlaying = command,
                receivedAtMs = clock(),
                positionMs = command.positionMs,
                artwork = when {
                    sameArt -> s.artwork
                    pending != null -> pending.second
                    else -> null
                },
                artworkId = if (sameArt || pending != null) command.artworkId else null,
            )
        }
        if (pending != null) pendingArtwork = null
    }

    private suspend fun onArtwork(command: MediaArtworkCommand) {
        val bytes = withContext(decodeDispatcher) { decode(command.data) } ?: return
        if (_state.value.nowPlaying?.artworkId == command.artworkId) {
            _state.update { s -> s.copy(artwork = bytes, artworkId = command.artworkId) }
        } else {
            pendingArtwork = command.artworkId to bytes
        }
    }

    private fun onAudioState(command: AudioStateCommand) {
        val now = clock()
        _state.update { s ->
            val local = s.audio
            s.copy(
                audio = command.copy(
                    volume = if (local != null && now < volumeHoldUntil) local.volume else command.volume,
                    muted = if (local != null && now < muteHoldUntil) local.muted else command.muted,
                ),
            )
        }
    }

    private fun interpolate(s: MediaUiState, now: Long): Long =
        PlaybackClock.positionAt(now, s.receivedAtMs, s.nowPlaying?.positionMs ?: 0, s.durationMs, s.isPlaying)
}

private fun decodeBase64(data: String): ByteArray? =
    runCatching { android.util.Base64.decode(data, android.util.Base64.DEFAULT) }.getOrNull()
