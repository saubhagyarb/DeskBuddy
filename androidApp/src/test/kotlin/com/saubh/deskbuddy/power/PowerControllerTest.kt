package com.saubh.deskbuddy.power

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.protocol.PowerCommand
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PowerControllerTest {
    private val gateway = FakeGateway()

    private fun TestScope.controller() = PowerController(gateway, backgroundScope).also { runCurrent() }

    private fun powerCommands() = gateway.sent.filterIsInstance<PowerCommand>()

    @Test
    fun `startCountdown_givenTenSecondsPass_sendsOnceThenShowsResultThenIdles`() = runTest {
        val power = controller()
        power.startCountdown(PowerAction.SHUTDOWN, "Desk")
        runCurrent()
        assertEquals(PowerCountdownState.Counting(PowerAction.SHUTDOWN, "Desk", 10), power.countdown.value)
        advanceTimeBy(9_999); runCurrent()
        assertTrue(powerCommands().isEmpty())
        advanceTimeBy(1); runCurrent()
        assertEquals(listOf(PowerCommand(PowerAction.SHUTDOWN)), powerCommands())
        assertEquals(PowerCountdownState.Finished(PowerAction.SHUTDOWN, "Desk", PowerOutcome.SENT), power.countdown.value)
        advanceTimeBy(PowerController.RESULT_VISIBLE_MS); runCurrent()
        assertEquals(PowerCountdownState.Idle, power.countdown.value)
    }

    @Test
    fun `startCountdown_givenThreeSeconds_showsSevenLeft`() = runTest {
        val power = controller()
        power.startCountdown(PowerAction.RESTART, "Desk")
        advanceTimeBy(3_000); runCurrent()
        assertEquals(7, (power.countdown.value as PowerCountdownState.Counting).secondsLeft)
    }

    @Test
    fun `cancelCountdown_givenMidway_neverSends`() = runTest {
        val power = controller()
        power.startCountdown(PowerAction.RESTART, "Desk")
        advanceTimeBy(5_000); runCurrent()
        power.cancelCountdown(); runCurrent()
        assertEquals(PowerCountdownState.Finished(PowerAction.RESTART, "Desk", PowerOutcome.CANCELLED), power.countdown.value)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(powerCommands().isEmpty())
        assertEquals(PowerCountdownState.Idle, power.countdown.value)
    }

    @Test
    fun `connectionLost_givenCounting_endsNotConnectedWithoutSending`() = runTest {
        val power = controller()
        power.startCountdown(PowerAction.SHUTDOWN, "Desk")
        advanceTimeBy(2_000); runCurrent()
        gateway.connectedState.value = false; runCurrent()
        assertEquals(PowerOutcome.NOT_CONNECTED, (power.countdown.value as PowerCountdownState.Finished).outcome)
        gateway.connectedState.value = true
        advanceTimeBy(30_000); runCurrent()
        assertTrue(powerCommands().isEmpty())
    }

    @Test
    fun `startCountdown_givenAlreadyCounting_ignoresSecondRequest`() = runTest {
        val power = controller()
        power.startCountdown(PowerAction.SHUTDOWN, "Desk")
        advanceTimeBy(4_000); runCurrent()
        power.startCountdown(PowerAction.RESTART, "Desk"); runCurrent()
        assertEquals(PowerCountdownState.Counting(PowerAction.SHUTDOWN, "Desk", 6), power.countdown.value)
    }

    @Test
    fun `send_givenLock_isInstant`() = runTest {
        val power = controller()
        power.send(PowerAction.LOCK); runCurrent()
        assertEquals(listOf(PowerCommand(PowerAction.LOCK)), powerCommands())
    }

    @Test
    fun `cancelCountdown_givenSendInFlight_stillReportsSentAndSendsOnce`() = runTest {
        val slow = SuspendingGateway()
        val power = PowerController(slow, backgroundScope).also { runCurrent() }
        power.startCountdown(PowerAction.SHUTDOWN, "Desk")
        advanceTimeBy(10_000); runCurrent()
        assertEquals(PowerCountdownState.Sending(PowerAction.SHUTDOWN, "Desk"), power.countdown.value)
        power.cancelCountdown(); slow.connectedState.value = false; runCurrent()
        assertEquals(PowerCountdownState.Sending(PowerAction.SHUTDOWN, "Desk"), power.countdown.value)
        slow.release.complete(Unit); runCurrent()
        assertEquals(PowerCountdownState.Finished(PowerAction.SHUTDOWN, "Desk", PowerOutcome.SENT), power.countdown.value)
        assertEquals<List<Command>>(listOf(PowerCommand(PowerAction.SHUTDOWN)), slow.sent)
    }

    @Test
    fun `startCountdown_givenNotConnected_finishesNotConnectedAndNeverSends`() = runTest {
        gateway.connectedState.value = false
        val power = controller()
        power.startCountdown(PowerAction.SHUTDOWN, "Desk"); runCurrent()
        assertEquals(PowerCountdownState.Finished(PowerAction.SHUTDOWN, "Desk", PowerOutcome.NOT_CONNECTED), power.countdown.value)
        advanceTimeBy(5_000); gateway.connectedState.value = true
        advanceTimeBy(30_000); runCurrent()
        assertTrue(powerCommands().isEmpty())
        assertEquals(PowerCountdownState.Idle, power.countdown.value)
    }
}

/** A gateway whose send suspends until [release], as a slow socket write does. */
private class SuspendingGateway : MediaGateway {
    override val incoming = MutableSharedFlow<Message>(extraBufferCapacity = 1)
    val connectedState = MutableStateFlow(true)
    override val connected: Flow<Boolean> = connectedState
    val release = CompletableDeferred<Unit>()
    val sent = mutableListOf<Command>()

    override suspend fun send(command: Command): Boolean {
        release.await()
        sent += command
        return true
    }
}
