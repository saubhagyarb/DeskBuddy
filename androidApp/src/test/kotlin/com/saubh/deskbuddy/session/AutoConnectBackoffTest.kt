package com.saubh.deskbuddy.session

import kotlin.test.Test
import kotlin.test.assertEquals

class AutoConnectBackoffTest {
    @Test
    fun `grow_givenRepeatedMisses_doublesUpToCap`() {
        val backoff = AutoConnectBackoff(initialMs = 10_000, maxMs = 300_000)
        val seen = (1..7).map { backoff.grow(); backoff.currentMs }
        assertEquals(listOf(20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L, 300_000L), seen)
    }

    @Test
    fun `reset_afterGrowth_returnsToInitial`() {
        val backoff = AutoConnectBackoff(initialMs = 10_000, maxMs = 300_000)
        backoff.grow(); backoff.grow()
        backoff.reset()
        assertEquals(10_000L, backoff.currentMs)
    }
}
