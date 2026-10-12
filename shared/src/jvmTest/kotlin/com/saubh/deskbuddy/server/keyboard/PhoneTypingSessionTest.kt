package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.PRESSED
import com.saubh.deskbuddy.server.keyboard.RawKey.Type.TYPED
import java.awt.event.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhoneTypingSessionTest {
    private val sent = mutableListOf<Command>()
    private var phone: String? = "Pixel 8"
    private val session = PhoneTypingSession(send = { sent += it }, phoneName = { phone })

    @Test
    fun `toggle_givenPhone_entersAndAnnouncesMode`() {
        session.toggle()
        assertEquals(TypingState.Active("Pixel 8"), session.state.value)
        assertEquals(listOf<Command>(KeyboardModeCommand(true)), sent)
    }

    @Test
    fun `toggle_givenNoPhone_showsNoPhoneAndSendsNothing`() {
        phone = null
        session.toggle()
        assertEquals(TypingState.NoPhone, session.state.value)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `exit_givenEachReason_announcesOffExactlyOnce`() {
        session.enter()
        session.exit(ExitReason.FOCUS_LOST)
        session.exit(ExitReason.HOTKEY)
        assertEquals(listOf<Command>(KeyboardModeCommand(true), KeyboardModeCommand(false)), sent)
        assertEquals(TypingState.Idle, session.state.value)
    }

    @Test
    fun `onKey_givenEscape_exitsWithoutForwarding`() {
        session.enter()
        assertTrue(session.onKey(RawKey(PRESSED, KeyEvent.VK_ESCAPE)))
        assertEquals(listOf<Command>(KeyboardModeCommand(true), KeyboardModeCommand(false)), sent)
    }

    @Test
    fun `onKey_givenTypedText_forwardsAndEchoes`() {
        session.enter()
        session.onKey(RawKey(TYPED, 0, 'h'))
        session.onKey(RawKey(TYPED, 0, 'i'))
        assertEquals(listOf(KeyTextCommand("h"), KeyTextCommand("i")), sent.drop(1))
        assertEquals("hi", session.echo.value)
    }

    @Test
    fun `onKey_givenIdle_isNotConsumed`() {
        assertFalse(session.onKey(RawKey(TYPED, 0, 'x')))
    }

    @Test
    fun `phoneReconnected_givenActive_reannouncesModeToNewConnection`() {
        session.enter()
        session.phoneReconnected()
        assertEquals(listOf<Command>(KeyboardModeCommand(true), KeyboardModeCommand(true)), sent)
        assertEquals(TypingState.Active("Pixel 8"), session.state.value)
    }

    @Test
    fun `phoneReconnected_givenIdle_sendsNothing`() {
        session.phoneReconnected()
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `phoneDisconnected_givenActive_exitsWithNotice`() {
        session.enter()
        session.exit(ExitReason.PHONE_DISCONNECTED)
        assertEquals(TypingState.PhoneDisconnected, session.state.value)
        session.dismissNotice()
        assertEquals(TypingState.Idle, session.state.value)
    }
}
