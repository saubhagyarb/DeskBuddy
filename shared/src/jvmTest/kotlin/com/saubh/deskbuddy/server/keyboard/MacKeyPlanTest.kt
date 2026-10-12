package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import kotlin.test.Test
import kotlin.test.assertEquals

class MacKeyPlanTest {
    @Test
    fun `forKey_givenCtrlC_sendsCommandC`() {
        assertEquals(MacKeyStroke(0x08, MacKeyPlan.FLAG_COMMAND), MacKeyPlan.forKey(Key.C, setOf(KeyModifier.CTRL)))
    }

    @Test
    fun `forKey_givenCtrlLeftOrBackspace_sendsOptionForWordWise`() {
        assertEquals(MacKeyStroke(0x7B, MacKeyPlan.FLAG_OPTION), MacKeyPlan.forKey(Key.LEFT, setOf(KeyModifier.CTRL)))
        assertEquals(MacKeyStroke(0x33, MacKeyPlan.FLAG_OPTION), MacKeyPlan.forKey(Key.BACKSPACE, setOf(KeyModifier.CTRL)))
    }

    @Test
    fun `forKey_givenAllModifiers_combinesFlags`() {
        val stroke = MacKeyPlan.forKey(Key.Z, KeyModifier.entries.toSet())
        assertEquals(0x06, stroke.keyCode)
        assertEquals(
            MacKeyPlan.FLAG_COMMAND or MacKeyPlan.FLAG_OPTION or MacKeyPlan.FLAG_SHIFT or MacKeyPlan.FLAG_CONTROL,
            stroke.flags,
        )
    }

    @Test
    fun `keyCode_givenLettersAndDigits_usesAnsiPositions`() {
        assertEquals(0x00, MacKeyPlan.keyCode(Key.A))
        assertEquals(0x28, MacKeyPlan.keyCode(Key.K))
        assertEquals(0x2E, MacKeyPlan.keyCode(Key.M))
        assertEquals(0x1D, MacKeyPlan.keyCode(Key.DIGIT_0))
        assertEquals(0x17, MacKeyPlan.keyCode(Key.DIGIT_5))
        assertEquals(0x19, MacKeyPlan.keyCode(Key.DIGIT_9))
    }

    @Test
    fun `textChunks_givenLongText_splitsAtTwentyUnits`() {
        assertEquals(listOf("a".repeat(20), "a".repeat(5)), MacKeyPlan.textChunks("a".repeat(25)))
        assertEquals(emptyList(), MacKeyPlan.textChunks(""))
    }

    @Test
    fun `textChunks_givenSurrogatePairAtBoundary_keepsPairTogether`() {
        val text = "a".repeat(19) + "😀" + "b"
        val chunks = MacKeyPlan.textChunks(text)
        assertEquals(listOf("a".repeat(19), "😀b"), chunks)
        assertEquals(text, chunks.joinToString(""))
    }
}
