package com.saubh.deskbuddy.ui.standby

import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatScheduleTest {
    @Test
    fun `nextDelay_givenFirstRepeat_waitsInitialDelay`() {
        assertEquals(400L, RepeatSchedule.nextDelay(repeatsSoFar = 0, heldMs = 0))
    }

    @Test
    fun `nextDelay_givenHeldUnderFastThreshold_repeatsAt150`() {
        assertEquals(150L, RepeatSchedule.nextDelay(repeatsSoFar = 3, heldMs = 1_499))
    }

    @Test
    fun `nextDelay_givenHeldPastFastThreshold_repeatsAt60`() {
        assertEquals(60L, RepeatSchedule.nextDelay(repeatsSoFar = 9, heldMs = 1_500))
    }
}
