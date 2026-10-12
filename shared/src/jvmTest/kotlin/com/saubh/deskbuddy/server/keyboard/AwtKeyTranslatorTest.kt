package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.PRESSED
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.RELEASED
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.TYPED
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class AwtKeyTranslatorTest {
    private val t = AwtKeyTranslator(mac = false)
    private val mac = AwtKeyTranslator(mac = true)

    private fun feed(vararg keys: RawKey) = keys.flatMap(t::translate)

    private fun feedMac(vararg keys: RawKey) = keys.flatMap(mac::translate)

    @Test
    fun `translate_givenMacCommandC_sendsCtrlCombo`() {
        val out = feedMac(RawKey(PRESSED, KeyEvent.VK_C, meta = true), RawKey(RELEASED, KeyEvent.VK_C, meta = true))
        assertEquals(listOf(KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))), out)
    }

    @Test
    fun `translate_givenMacOptionLetter_sendsTypedCharacter`() {
        val out = feedMac(RawKey(PRESSED, KeyEvent.VK_G, alt = true), RawKey(TYPED, 0, '©', alt = true))
        assertEquals(listOf(KeyTextCommand("©")), out)
    }

    @Test
    fun `translate_givenMacOptionLeftOrBackspace_sendsCtrlWordWise`() {
        assertEquals(listOf(KeyPressCommand(Key.LEFT, setOf(KeyModifier.CTRL))), feedMac(RawKey(PRESSED, KeyEvent.VK_LEFT, alt = true)))
        assertEquals(listOf(KeyPressCommand(Key.BACKSPACE, setOf(KeyModifier.CTRL))), feedMac(RawKey(PRESSED, KeyEvent.VK_BACK_SPACE, alt = true)))
    }

    @Test
    fun `translate_givenMacOptionPageDown_keepsAlt`() {
        assertEquals(listOf(KeyPressCommand(Key.PAGE_DOWN, setOf(KeyModifier.ALT))), feedMac(RawKey(PRESSED, KeyEvent.VK_PAGE_DOWN, alt = true)))
    }

    @Test
    fun `translate_givenWindowsMetaLetter_isIgnoredAsBefore`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_C, meta = true), RawKey(TYPED, 0, 'c', meta = true))
        assertEquals(listOf(KeyTextCommand("c")), out)
    }

    @Test
    fun `translate_givenShiftedLetter_sendsText`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_A, shift = true), RawKey(TYPED, 0, 'A', shift = true))
        assertEquals(listOf(KeyTextCommand("A")), out)
    }

    @Test
    fun `translate_givenCtrlC_sendsCombo`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_C, ctrl = true), RawKey(TYPED, 0, '\u0003', ctrl = true))
        assertEquals(listOf(KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))), out)
    }

    @Test
    fun `translate_givenAltGrCharacter_sendsText`() {
        val out = feed(
            RawKey(PRESSED, KeyEvent.VK_Q, ctrl = true, alt = true),
            RawKey(TYPED, 0, '@', ctrl = true, alt = true),
        )
        assertEquals(listOf(KeyTextCommand("@")), out)
    }

    @Test
    fun `translate_givenAltLetterWithoutTyped_sendsComboOnRelease`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_T, ctrl = true, alt = true), RawKey(RELEASED, KeyEvent.VK_T, ctrl = true, alt = true))
        assertEquals(listOf(KeyPressCommand(Key.T, setOf(KeyModifier.CTRL, KeyModifier.ALT))), out)
    }

    @Test
    fun `translate_givenShiftArrow_sendsKeyWithShift`() {
        assertEquals(listOf(KeyPressCommand(Key.LEFT, setOf(KeyModifier.SHIFT))), feed(RawKey(PRESSED, KeyEvent.VK_LEFT, shift = true)))
    }

    @Test
    fun `translate_givenEnter_sendsOneEnterAndIgnoresTypedNewline`() {
        val out = feed(RawKey(PRESSED, KeyEvent.VK_ENTER), RawKey(TYPED, 0, '\n'))
        assertEquals(listOf(KeyPressCommand(Key.ENTER)), out)
    }
}
