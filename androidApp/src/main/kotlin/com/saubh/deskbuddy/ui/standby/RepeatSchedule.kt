package com.saubh.deskbuddy.ui.standby

/** Hold-to-repeat timing for the volume buttons: a pause before repeating, then faster the longer it is held. */
object RepeatSchedule {
    const val INITIAL_DELAY_MS = 400L
    const val REPEAT_MS = 150L
    const val FAST_REPEAT_MS = 60L
    const val FAST_AFTER_MS = 1_500L

    fun nextDelay(repeatsSoFar: Int, heldMs: Long): Long = when {
        repeatsSoFar == 0 -> INITIAL_DELAY_MS
        heldMs >= FAST_AFTER_MS -> FAST_REPEAT_MS
        else -> REPEAT_MS
    }
}
