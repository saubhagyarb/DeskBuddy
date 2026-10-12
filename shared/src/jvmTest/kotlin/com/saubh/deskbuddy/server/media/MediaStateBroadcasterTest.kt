package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.protocol.AudioStateCommand
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.AudioLevels
import com.saubh.deskbuddy.server.UnsupportedMediaSessionActuator
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaStateBroadcasterTest {
    private class LevelsOnlyAudio : AudioActuator {
        var levelsCalls = 0
        override fun devices() = listOf(AudioDevice("d", "Speakers", isDefault = true))
        override fun volume(): Int = error("use levels()")
        override fun isMuted(): Boolean = error("use levels()")
        override fun setVolume(percent: Int) = Unit
        override fun setDefaultDevice(id: String) = true
        override fun levels(): AudioLevels { levelsCalls++; return AudioLevels(40, muted = false) }
    }

    @Test
    fun `commands_givenAudioActuator_readsVolumeAndMuteInOneCall`() = runBlocking {
        val audio = LevelsOnlyAudio()
        val broadcaster = MediaStateBroadcaster(UnsupportedMediaSessionActuator(), audio, volumePollMs = 10)
        val state = withTimeout(1_000) { broadcaster.commands().filterIsInstance<AudioStateCommand>().first() }
        // volume()/isMuted() throw here, so an emitted state proves levels() was used instead.
        assertEquals(40, state.volume)
        assertTrue(audio.levelsCalls >= 1)
    }
}
