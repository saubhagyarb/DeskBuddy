package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.ActivateAppCommand
import com.saubh.deskbuddy.protocol.Ack
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.Envelope
import com.saubh.deskbuddy.protocol.ErrorCode
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import com.saubh.deskbuddy.protocol.KeyboardModeCommand
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.CloseTabCommand
import com.saubh.deskbuddy.protocol.CloseWindowCommand
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.protocol.PowerCommand
import com.saubh.deskbuddy.protocol.RunningApp
import com.saubh.deskbuddy.protocol.RunningAppsCommand
import com.saubh.deskbuddy.protocol.RunningAppsRequestCommand
import com.saubh.deskbuddy.server.keyboard.KeyboardActuator
import com.saubh.deskbuddy.server.openapps.WindowActuator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlSessionTest {
    private val store = tempDeviceStore().apply { add(PairedDevice("Pixel", "tok")) }
    private val listener = RecordingListener()
    private val session = ControlSession(store, testActuators(), listener)

    private fun sessionWith(keyboard: KeyboardActuator) = ControlSession(store, testActuators(keyboard = keyboard), listener)

    @Test
    fun `envelope_givenUnknownToken_repliesAuthRequired`() {
        val reply = session.onMessage(Envelope("nope", MediaCommand(MediaAction.NEXT)))
        assertEquals(Ack(ok = false, error = ErrorCode.AUTH_REQUIRED), reply.message)
        assertTrue(listener.authenticated.isEmpty())
    }

    @Test
    fun `envelope_givenPairedToken_executesAndReportsDeviceName`() {
        val reply = session.onMessage(Envelope("tok", MediaCommand(MediaAction.NEXT)))
        assertEquals(Ack(ok = true), reply.message)
        assertEquals(listOf("Pixel"), listener.authenticated)
    }

    @Test
    fun `envelope_givenActuatorThrows_logsCommandAndError`() {
        val lines = mutableListOf<String>()
        val failing = object : PowerActuator {
            override fun shutdown() = Unit
            override fun restart() = Unit
            override fun lock(): Unit = throw IllegalStateException("boom")
        }
        val session = ControlSession(store, testActuators(power = failing), listener, log = { lines += it })
        val reply = session.onMessage(Envelope("tok", PowerCommand(PowerAction.LOCK)))
        assertEquals(Ack(ok = false, error = ErrorCode.INTERNAL), reply.message)
        assertEquals(1, lines.size)
        assertTrue("PowerCommand" in lines.single() && "IllegalStateException" in lines.single() && "boom" in lines.single())
    }

    @Test
    fun `keyText_givenPairedToken_typesSilently`() {
        val keyboard = RecordingKeyboard()
        val reply = sessionWith(keyboard).onMessage(Envelope("tok", KeyTextCommand("hi")))
        assertNull(reply.message)
        assertEquals(listOf("hi"), keyboard.typed)
    }

    @Test
    fun `keyPress_givenModifiers_passesThemThrough`() {
        val keyboard = RecordingKeyboard()
        sessionWith(keyboard).onMessage(Envelope("tok", KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))))
        assertEquals(listOf(Key.C to setOf(KeyModifier.CTRL)), keyboard.pressed)
    }

    @Test
    fun `keyText_givenUnsupportedOs_reportsOnlyOnce`() {
        val session = sessionWith(RecordingKeyboard(unsupported = true))
        val replies = (1..5).map { session.onMessage(Envelope("tok", KeyTextCommand("x"))).message }
        assertEquals(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS), replies.first())
        assertEquals(List(4) { null }, replies.drop(1))
    }

    @Test
    fun `keyText_givenOversizedText_repliesInternalWithoutTyping`() {
        val keyboard = RecordingKeyboard()
        val reply = sessionWith(keyboard).onMessage(Envelope("tok", KeyTextCommand("x".repeat(4097))))
        assertEquals(Ack(ok = false, error = ErrorCode.INTERNAL), reply.message)
        assertEquals(emptyList(), keyboard.typed)
    }

    @Test
    fun `keyboardMode_fromPhone_repliesInternal`() {
        val reply = session.onMessage(Envelope("tok", KeyboardModeCommand(active = true)))
        assertEquals(Ack(ok = false, error = ErrorCode.INTERNAL), reply.message)
    }

    @Test
    fun `runningAppsRequest_givenUnsupportedOs_repliesUnsupported`() {
        val reply = session.onMessage(Envelope("tok", RunningAppsRequestCommand))
        assertEquals(Ack(ok = false, error = ErrorCode.UNSUPPORTED_OS), reply.message)
    }

    @Test
    fun `runningAppsRequest_givenSupportedOs_acksAndTriggersPush`() {
        val actuators = testActuators(windows = FakeWindows())
        var requested = 0
        actuators.onRunningAppsRequested = { requested++ }
        val reply = ControlSession(store, actuators, listener).onMessage(Envelope("tok", RunningAppsRequestCommand))
        assertEquals(Ack(ok = true), reply.message)
        assertEquals(1, requested)
    }

    @Test
    fun `closeWindow_givenUnknownWindow_repliesNotFound`() {
        val windows = FakeWindows()
        val session = ControlSession(store, testActuators(windows = windows), listener)
        assertEquals(Ack(ok = false, error = ErrorCode.NOT_FOUND), session.onMessage(Envelope("tok", CloseWindowCommand("x"))).message)
        assertEquals(Ack(ok = true), session.onMessage(Envelope("tok", CloseWindowCommand("w1"))).message)
        assertEquals(Ack(ok = true), session.onMessage(Envelope("tok", CloseTabCommand("w1", 0, "Tab"))).message)
        assertEquals(listOf("window:w1", "tab:w1:0:Tab"), windows.calls)
    }

    @Test
    fun `runningApps_fromPhone_repliesInternal`() {
        val reply = session.onMessage(Envelope("tok", RunningAppsCommand(emptyList())))
        assertEquals(Ack(ok = false, error = ErrorCode.INTERNAL), reply.message)
    }

    private class FakeWindows : WindowActuator {
        val calls = mutableListOf<String>()
        override fun runningApps(): List<RunningApp> = emptyList()
        override fun closeWindow(windowId: String) = (windowId == "w1").also { if (it) calls += "window:$windowId" }
        override fun closeApp(appId: String) = false
        override fun closeTab(windowId: String, index: Int, title: String) = true.also { calls += "tab:$windowId:$index:$title" }
    }

    @Test
    fun `activate_givenFocusableApp_focusesAndRepliesOk`() {
        val focused = mutableListOf<String>()
        val windows = object : WindowActuator {
            override fun runningApps(): List<RunningApp> = emptyList()
            override fun closeWindow(windowId: String) = false
            override fun closeApp(appId: String) = false
            override fun closeTab(windowId: String, index: Int, title: String) = false
            override fun focusApp(app: DesktopApp): Boolean { focused += app.id; return true }
        }
        val apps = RecordingAppActuator(listOf(DesktopApp("a", "A")))
        val session = ControlSession(store, testActuators(windows = windows, apps = apps), listener)
        assertEquals(Ack(ok = true), session.onMessage(Envelope("tok", ActivateAppCommand("a"))).message)
        assertEquals(listOf("a"), focused)
        assertTrue(apps.launched.isEmpty())
    }

    @Test
    fun `activate_givenUnsupportedWindows_launchesInstead`() {
        val apps = RecordingAppActuator(listOf(DesktopApp("a", "A")))
        val session = ControlSession(store, testActuators(apps = apps), listener)
        assertEquals(Ack(ok = true), session.onMessage(Envelope("tok", ActivateAppCommand("a"))).message)
        assertEquals(listOf("a"), apps.launched)
    }

    @Test
    fun `activate_givenUnknownApp_repliesNotFound`() {
        assertEquals(Ack(ok = false, error = ErrorCode.NOT_FOUND), session.onMessage(Envelope("tok", ActivateAppCommand("zzz"))).message)
    }
}
