package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.Push
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteKeyboardHubTest {
    @Test
    fun `pcTyping_givenModePushes_follows`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(KeyboardModeCommand(true)))
        runCurrent()
        assertTrue(hub.pcTyping.value)
        gateway.incoming.emit(Push(KeyboardModeCommand(false)))
        runCurrent()
        assertFalse(hub.pcTyping.value)
    }

    @Test
    fun `pcTyping_givenDisconnectBeyondGrace_resetsToFalse`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(KeyboardModeCommand(true)))
        runCurrent()
        gateway.connectedState.value = false
        advanceTimeBy(5_001)
        runCurrent()
        assertFalse(hub.pcTyping.value)
    }

    @Test
    fun `pcTyping_givenShortReconnectBlip_staysOn`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        gateway.incoming.emit(Push(KeyboardModeCommand(true)))
        runCurrent()
        gateway.connectedState.value = false
        advanceTimeBy(2_000)
        gateway.connectedState.value = true
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(hub.pcTyping.value)
    }

    @Test
    fun `input_givenKeyPush_forwardsCommand`() = runTest {
        val gateway = FakeGateway()
        val hub = RemoteKeyboardHub(gateway, backgroundScope).also { runCurrent() }
        val received = mutableListOf<Command>()
        backgroundScope.launch { hub.input.collect { received += it } }
        runCurrent()
        gateway.incoming.emit(Push(KeyTextCommand("hi")))
        runCurrent()
        assertEquals(listOf<Command>(KeyTextCommand("hi")), received)
    }
}
