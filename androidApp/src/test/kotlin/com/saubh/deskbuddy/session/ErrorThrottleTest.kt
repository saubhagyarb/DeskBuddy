package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.ui.ErrorKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorThrottleTest {
    private var now = 0L
    private val throttle = ErrorThrottle(windowMs = 3_000L, clock = { now })

    @Test
    fun `allow_givenSameErrorRepeatedWithinWindow_letsOnlyFirstThrough`() {
        val allowed = (1..20).map { now += 100; throttle.allow(ErrorKind.INTERNAL) }
        assertEquals(1, allowed.count { it })
    }

    @Test
    fun `allow_givenDifferentErrors_letsEachThrough`() {
        assertEquals(true, throttle.allow(ErrorKind.INTERNAL))
        assertEquals(true, throttle.allow(ErrorKind.NOT_CONNECTED))
    }

    @Test
    fun `allow_givenSameErrorAfterWindow_letsItThroughAgain`() {
        throttle.allow(ErrorKind.INTERNAL)
        now = 3_000L
        assertEquals(true, throttle.allow(ErrorKind.INTERNAL))
    }
}
