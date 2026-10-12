package com.saubh.deskbuddy.power

import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.protocol.PowerCommand
import com.saubh.deskbuddy.session.MediaGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lock, restart and shut down the connected PC. Lock is instant; restart and shut down run a
 * [COUNTDOWN_SECONDS] countdown (app-scoped, so it survives navigation) that the user can cancel.
 */
class PowerController(private val gateway: MediaGateway, private val scope: CoroutineScope) {
    companion object {
        const val COUNTDOWN_SECONDS = 10
        const val RESULT_VISIBLE_MS = 2_000L
    }

    private val _countdown = MutableStateFlow<PowerCountdownState>(PowerCountdownState.Idle)
    val countdown: StateFlow<PowerCountdownState> = _countdown.asStateFlow()

    private var job: Job? = null

    // Pessimistic until the first emission, so a countdown never starts on an unknown link.
    private var connectedNow = false

    init {
        // A countdown must never fire into a later connection, possibly to another PC.
        scope.launch {
            gateway.connected.collect {
                connectedNow = it
                if (!it) finishEarly(PowerOutcome.NOT_CONNECTED)
            }
        }
    }

    fun send(action: PowerAction) {
        scope.launch { gateway.send(PowerCommand(action)) }
    }

    fun startCountdown(action: PowerAction, pcName: String) {
        if (_countdown.value.let { it is PowerCountdownState.Counting || it is PowerCountdownState.Sending }) return
        job?.cancel()
        if (!connectedNow) {
            job = scope.launch { showResult(action, pcName, PowerOutcome.NOT_CONNECTED) }
            return
        }
        job = scope.launch {
            for (left in COUNTDOWN_SECONDS downTo 1) {
                _countdown.value = PowerCountdownState.Counting(action, pcName, left)
                delay(1_000L)
            }
            // Past this point the command may already be on the wire, so cancel and disconnect no longer apply.
            _countdown.value = PowerCountdownState.Sending(action, pcName)
            val sent = withContext(NonCancellable) { runCatching { gateway.send(PowerCommand(action)) }.getOrDefault(false) }
            showResult(action, pcName, if (sent) PowerOutcome.SENT else PowerOutcome.NOT_CONNECTED)
        }
    }

    fun cancelCountdown() = finishEarly(PowerOutcome.CANCELLED)

    private fun finishEarly(outcome: PowerOutcome) {
        val counting = _countdown.value as? PowerCountdownState.Counting ?: return
        job?.cancel()
        job = scope.launch { showResult(counting.action, counting.pcName, outcome) }
    }

    private suspend fun showResult(action: PowerAction, pcName: String, outcome: PowerOutcome) {
        _countdown.value = PowerCountdownState.Finished(action, pcName, outcome)
        delay(RESULT_VISIBLE_MS)
        _countdown.value = PowerCountdownState.Idle
    }
}
