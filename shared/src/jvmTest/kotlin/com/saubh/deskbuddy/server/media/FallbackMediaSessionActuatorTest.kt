package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.server.Artwork
import com.saubh.deskbuddy.server.MediaSessionActuator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeSession : MediaSessionActuator {
    override val state = MutableStateFlow(NowPlayingCommand.NONE)
    override val artwork = MutableStateFlow<Artwork?>(null)
    override val available = MutableStateFlow(false)
    var started = false
    var startCount = 0
    var seekedTo: Long? = null
    override fun seek(positionMs: Long): Boolean { seekedTo = positionMs; return true }
    override fun refresh() = Unit
    override fun start() { started = true; startCount++; available.value = true }
    override fun stop() { started = false }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FallbackMediaSessionActuatorTest {
    @Test
    fun `start_givenNoPrimary_startsFallbackAndMirrorsIt`() = runTest {
        val fallback = FakeSession()
        val actuator = FallbackMediaSessionActuator({ null }, { fallback }, backgroundScope)
        actuator.start()
        runCurrent()
        assertTrue(fallback.started)
        fallback.state.value = NowPlayingCommand(title = "Song", status = PlaybackStatus.PLAYING)
        runCurrent()
        assertEquals("Song", actuator.state.value.title)
        assertTrue(actuator.available.value)
    }

    @Test
    fun `start_givenWorkingPrimary_neverBuildsFallback`() = runTest {
        val primary = FakeSession()
        var fallbackBuilt = false
        val actuator = FallbackMediaSessionActuator({ primary }, { fallbackBuilt = true; FakeSession() }, backgroundScope)
        actuator.start()
        runCurrent()
        assertTrue(primary.started)
        assertFalse(fallbackBuilt)
        assertTrue(actuator.seek(42))
        assertEquals(42L, primary.seekedTo)
    }

    @Test
    fun `seek_beforeStart_returnsFalse`() = runTest {
        assertFalse(FallbackMediaSessionActuator({ null }, { FakeSession() }, backgroundScope).seek(1))
    }

    @Test
    fun `stop_givenCalledBeforeProbeCompletes_neverStartsBackend`() = runTest {
        val fallback = FakeSession()
        val actuator = FallbackMediaSessionActuator({ null }, { fallback }, backgroundScope)
        actuator.start()
        actuator.stop()
        runCurrent()
        assertFalse(fallback.started)
        assertEquals(0, fallback.startCount)
    }

    @Test
    fun `start_givenCalledTwice_startsOnlyOneBackend`() = runTest {
        val built = mutableListOf<FakeSession>()
        val actuator = FallbackMediaSessionActuator({ null }, { FakeSession().also { built += it } }, backgroundScope)
        actuator.start()
        actuator.start()
        runCurrent()
        actuator.start()
        runCurrent()
        assertEquals(1, built.size)
        assertEquals(1, built.single().startCount)
    }
}
