package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.server.keyboard.SendInputPlan.KEYEVENTF_EXTENDEDKEY
import com.saubh.deskbuddy.server.keyboard.SendInputPlan.KEYEVENTF_KEYUP
import com.saubh.deskbuddy.server.keyboard.SendInputPlan.KEYEVENTF_UNICODE
import kotlin.test.Test
import kotlin.test.assertEquals

class SendInputPlanTest {
    @Test
    fun `forText_givenSurrogatePair_sendsEachUnitDownThenUp`() {
        val plan = SendInputPlan.forText("a👋")
        val units = "a👋".map { it.code }
        assertEquals(6, plan.size)
        assertEquals(units, plan.filter { it.flags == KEYEVENTF_UNICODE }.map { it.scan })
        assertEquals(units, plan.filter { it.flags == KEYEVENTF_UNICODE or KEYEVENTF_KEYUP }.map { it.scan })
    }

    @Test
    fun `forKey_givenCtrlShift_pressesModifiersFirstReleasesInReverse`() {
        val plan = SendInputPlan.forKey(Key.S, setOf(KeyModifier.SHIFT, KeyModifier.CTRL))
        assertEquals(
            listOf(
                KeyInput(0x11, 0, 0), KeyInput(0x10, 0, 0),
                KeyInput(0x53, 0, 0), KeyInput(0x53, 0, KEYEVENTF_KEYUP),
                KeyInput(0x10, 0, KEYEVENTF_KEYUP), KeyInput(0x11, 0, KEYEVENTF_KEYUP),
            ),
            plan,
        )
    }

    @Test
    fun `forKey_givenArrow_setsExtendedFlag`() {
        val plan = SendInputPlan.forKey(Key.LEFT, emptySet())
        assertEquals(listOf(KeyInput(0x25, 0, KEYEVENTF_EXTENDEDKEY), KeyInput(0x25, 0, KEYEVENTF_EXTENDEDKEY or KEYEVENTF_KEYUP)), plan)
    }

    @Test
    fun `forKey_givenDigit_mapsToDigitVirtualKey`() {
        assertEquals(KeyInput(0x37, 0, 0), SendInputPlan.forKey(Key.DIGIT_7, emptySet()).first())
    }
}
