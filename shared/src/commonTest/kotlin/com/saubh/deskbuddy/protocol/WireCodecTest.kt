package com.saubh.deskbuddy.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WireCodecTest {
    @Test
    fun encodeDecode_givenKeyCommands_roundTrips() {
        val messages: List<Message> = listOf(
            Envelope("t", KeyTextCommand("héllo 👋")),
            Envelope("t", KeyPressCommand(Key.C, setOf(KeyModifier.CTRL))),
            Envelope("t", KeyPressCommand(Key.ENTER)),
            Push(KeyboardModeCommand(active = true)),
        )
        messages.forEach { assertEquals(it, WireCodec.decode(WireCodec.encode(it))) }
    }

    @Test
    fun decodeOrNull_givenUnknownKey_returnsNull() {
        val frame = """{"type":"envelope","token":"t","command":{"type":"key_press","key":"F13"}}"""
        assertNull(WireCodec.decodeOrNull(frame))
    }

    @Test
    fun forChar_givenLettersDigitsAndSymbols_mapsOrNull() {
        assertEquals(Key.A, Key.forChar('a'))
        assertEquals(Key.Z, Key.forChar('Z'))
        assertEquals(Key.DIGIT_7, Key.forChar('7'))
        assertNull(Key.forChar('@'))
    }

    @Test
    fun encodeDecode_givenActivateAndNewFields_roundTrips() {
        val messages: List<Message> = listOf(
            Envelope("t", ActivateAppCommand("C:\\Start Menu\\Code.lnk")),
            AppCatalog(listOf(DesktopApp("a", "A")), listOf("a"), canActivate = true),
            Push(RunningAppsCommand(listOf(RunningApp("c:\\code.exe", "Code", emptyList(), shortcutId = "a")))),
        )
        messages.forEach { assertEquals(it, WireCodec.decode(WireCodec.encode(it))) }
    }

    @Test
    fun decode_givenOldDesktopPayloads_defaultsNewFields() {
        val catalog = WireCodec.decode("""{"type":"app_catalog","apps":[],"shortcutIds":[]}""") as AppCatalog
        assertEquals(false, catalog.canActivate)
        val running = WireCodec.decode(
            """{"type":"push","command":{"type":"running_apps","apps":[{"id":"x","name":"X","windows":[]}]}}""",
        ) as Push
        assertNull((running.command as RunningAppsCommand).apps.single().shortcutId)
    }
}
