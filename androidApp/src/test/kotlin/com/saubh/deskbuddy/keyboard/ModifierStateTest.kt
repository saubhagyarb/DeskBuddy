package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.protocol.KeyModifier.CTRL
import com.saubh.deskbuddy.protocol.KeyModifier.SHIFT
import kotlin.test.Test
import kotlin.test.assertEquals

class ModifierStateTest {
    @Test
    fun `tap_givenRepeatedTaps_cyclesOneShotLockedOff`() {
        val once = ModifierState().tap(CTRL)
        val twice = once.tap(CTRL)
        val thrice = twice.tap(CTRL)
        assertEquals(setOf(CTRL), once.oneShot)
        assertEquals(setOf(CTRL), twice.locked)
        assertEquals(emptySet(), thrice.active)
    }

    @Test
    fun `consumed_givenOneShotAndLocked_clearsOnlyOneShot`() {
        val state = ModifierState(oneShot = setOf(CTRL), locked = setOf(SHIFT)).consumed()
        assertEquals(setOf(SHIFT), state.active)
    }
}
