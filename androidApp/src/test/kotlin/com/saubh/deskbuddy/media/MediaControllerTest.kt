package com.saubh.deskbuddy.media

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.protocol.Push
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MediaControllerTest {
    private val playing = NowPlayingCommand(title = "Song", status = PlaybackStatus.PLAYING, positionMs = 1_000, durationMs = 60_000, canSeek = true)

    private fun TestScope.controller(gateway: FakeGateway) =
        MediaController(gateway, backgroundScope, clock = { currentTime }, decodeDispatcher = StandardTestDispatcher(testScheduler))
            .also { runCurrent() }

    @Test
    fun `state_givenPlayingAndNoPushes_doesNotEmitOverTime`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        gateway.incoming.emit(Push(playing))
        runCurrent()
        var emissions = 0
        backgroundScope.launch { controller.state.drop(1).collect { emissions++ } }
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, emissions)
    }

    @Test
    fun `positionAt_givenPlaying_interpolatesFromPush`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        gateway.incoming.emit(Push(playing))
        runCurrent()
        val pushedAt = currentTime
        assertEquals(3_000, controller.state.value.positionAt(pushedAt + 2_000))
    }

    @Test
    fun `seek_givenTarget_rebasesPositionAtSeekTime`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(gateway)
        gateway.incoming.emit(Push(playing))
        runCurrent()
        advanceTimeBy(10_000)
        controller.seek(30_000)
        assertEquals(30_000, controller.state.value.positionAt(currentTime))
    }
}
