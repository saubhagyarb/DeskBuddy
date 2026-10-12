package com.saubh.deskbuddy.power

import com.saubh.deskbuddy.protocol.PowerAction

/** Restart and shut down wait out a countdown the user can cancel; [Finished] stays briefly so the result is seen. */
sealed interface PowerCountdownState {
    data object Idle : PowerCountdownState
    data class Counting(val action: PowerAction, val pcName: String, val secondsLeft: Int) : PowerCountdownState
    data class Sending(val action: PowerAction, val pcName: String) : PowerCountdownState
    data class Finished(val action: PowerAction, val pcName: String, val outcome: PowerOutcome) : PowerCountdownState
}
