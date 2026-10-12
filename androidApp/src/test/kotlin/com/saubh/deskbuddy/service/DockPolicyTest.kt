package com.saubh.deskbuddy.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DockPolicyTest {

    @Test
    fun `isLandscape_givenSidewaysEitherWay_returnsTrue`() {
        listOf(60, 90, 120, 240, 270, 300).forEach { assertEquals(true, DockPolicy.isLandscape(it), "degrees=$it") }
    }

    @Test
    fun `isLandscape_givenUprightOrTilted_returnsFalse`() {
        listOf(0, 30, 59, 121, 180, 239, 301, 359).forEach { assertEquals(false, DockPolicy.isLandscape(it), "degrees=$it") }
    }

    @Test
    fun `isLandscape_givenFlatPhone_returnsNoReading`() {
        assertNull(DockPolicy.isLandscape(-1))
    }

    @Test
    fun `shouldStandby_givenChargingLandscape_returnsTrueWithoutPc`() {
        assertTrue(DockPolicy.shouldStandby(charging = true, landscape = true, suppressed = false))
    }

    @Test
    fun `shouldStandby_givenAnyConditionMissing_returnsFalse`() {
        assertFalse(DockPolicy.shouldStandby(charging = false, landscape = true, suppressed = false))
        assertFalse(DockPolicy.shouldStandby(charging = true, landscape = false, suppressed = false))
    }

    @Test
    fun `shouldStandby_givenPcTypingIntoPhone_returnsFalse`() {
        assertFalse(DockPolicy.shouldStandby(charging = true, landscape = true, suppressed = true))
    }
}
