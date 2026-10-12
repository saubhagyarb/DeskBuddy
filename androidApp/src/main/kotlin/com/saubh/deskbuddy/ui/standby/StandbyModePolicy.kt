package com.saubh.deskbuddy.ui.standby

/** Which standby page shows; a mode the user picked is kept until standby closes, unless the PC it needs goes away. */
object StandbyModePolicy {
    fun onEnter(connected: Boolean): StandbyModeState =
        StandbyModeState(if (connected) StandbyMode.MEDIA_APPS else StandbyMode.CLOCK, userPicked = false)

    fun onConnectionChanged(state: StandbyModeState, connected: Boolean): StandbyModeState = when {
        connected && state.mode == StandbyMode.CLOCK && !state.userPicked -> state.copy(mode = StandbyMode.MEDIA_APPS)
        !connected && state.mode != StandbyMode.CLOCK -> StandbyModeState(StandbyMode.CLOCK, userPicked = false)
        else -> state
    }

    fun onUserSelect(state: StandbyModeState, mode: StandbyMode): StandbyModeState = StandbyModeState(mode, userPicked = true)
}
