package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.MediaArtworkCommand
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Turns actuator state into the stream of pushes the phone needs: now-playing on change,
 * artwork on change, audio state polled and diffed. [resendAll] re-emits everything.
 */
class MediaStateBroadcaster(
    private val mediaSession: MediaSessionActuator,
    private val audio: AudioActuator,
    private val volumePollMs: Long = 1_000,
    private val devicePollMs: Long = 5_000,
) {
    private val resend = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    private val audioResend = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    @Volatile private var devices: List<AudioDevice> = emptyList()
    @Volatile private var devicesAtMs = 0L

    fun resendAll() {
        mediaSession.refresh()
        resend.tryEmit(Unit)
    }

    /** The phone changed volume or the default device: refresh the device list now, not at the next 5 s poll. */
    fun audioChanged() {
        devicesAtMs = 0L
        audioResend.tryEmit(Unit)
    }

    fun commands(): Flow<Command> = merge(
        mediaSession.state,
        resend.map { mediaSession.state.value },
        merge(mediaSession.artwork, resend.map { mediaSession.artwork.value })
            .filterNotNull()
            .map { MediaArtworkCommand(it.artworkId, it.mimeType, it.base64) },
        audioChanges(),
        merge(resend, audioResend).map { snapshot(cachedDevices()) }.filterNotNull(),
    )

    private fun cachedDevices(): List<AudioDevice> {
        val now = System.currentTimeMillis()
        if (now - devicesAtMs >= devicePollMs) {
            devices = runCatching { audio.devices() }.getOrDefault(devices)
            devicesAtMs = now
        }
        return devices
    }

    private fun snapshot(devices: List<AudioDevice>): AudioStateCommand? =
        runCatching { AudioStateCommand(devices, audio.volume(), audio.isMuted()) }.getOrNull()

    private fun audioChanges(): Flow<AudioStateCommand> = flow {
        var last: AudioStateCommand? = null
        while (true) {
            val state = snapshot(cachedDevices())
            if (state != null && state != last) {
                emit(state)
                last = state
            }
            delay(volumePollMs)
        }
    }
}
