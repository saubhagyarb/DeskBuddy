package com.saubh.deskbuddy.ui.standby

import com.saubh.deskbuddy.ui.standby.StandbyMode.CLOCK
import com.saubh.deskbuddy.ui.standby.StandbyMode.DEVELOPMENT
import com.saubh.deskbuddy.ui.standby.StandbyMode.MEDIA_APPS
import com.saubh.deskbuddy.ui.standby.StandbyMode.MULTIMEDIA
import com.saubh.deskbuddy.ui.standby.clock.ClockFace
import kotlin.test.Test
import kotlin.test.assertEquals

class StandbyModePolicyTest {
    @Test
    fun `onEnter_givenConnected_opensMediaApps`() {
        assertEquals(StandbyModeState(MEDIA_APPS, false), StandbyModePolicy.onEnter(connected = true))
    }

    @Test
    fun `onEnter_givenNoPc_opensClock`() {
        assertEquals(StandbyModeState(CLOCK, false), StandbyModePolicy.onEnter(connected = false))
    }

    @Test
    fun `onConnectionChanged_givenAutoClockAndPcConnects_switchesToMediaApps`() {
        assertEquals(MEDIA_APPS, StandbyModePolicy.onConnectionChanged(StandbyModeState(CLOCK, false), connected = true).mode)
    }

    @Test
    fun `onConnectionChanged_givenUserPickedClock_staysOnClock`() {
        assertEquals(StandbyModeState(CLOCK, true), StandbyModePolicy.onConnectionChanged(StandbyModeState(CLOCK, true), connected = true))
    }

    @Test
    fun `onConnectionChanged_givenPcModeAndPcLeaves_fallsBackToAutoClock`() {
        listOf(MEDIA_APPS, MULTIMEDIA, DEVELOPMENT).forEach { mode ->
            assertEquals(StandbyModeState(CLOCK, false), StandbyModePolicy.onConnectionChanged(StandbyModeState(mode, true), connected = false))
        }
    }

    @Test
    fun `onUserSelect_givenAnyMode_marksUserPicked`() {
        assertEquals(StandbyModeState(DEVELOPMENT, true), StandbyModePolicy.onUserSelect(StandbyModeState(), DEVELOPMENT))
    }

    @Test
    fun `fromName_givenUnknownOrNull_defaultsToFlip`() {
        assertEquals(ClockFace.NEON, ClockFace.fromName("NEON"))
        assertEquals(ClockFace.FLIP, ClockFace.fromName("gone"))
        assertEquals(ClockFace.FLIP, ClockFace.fromName(null))
    }
}
