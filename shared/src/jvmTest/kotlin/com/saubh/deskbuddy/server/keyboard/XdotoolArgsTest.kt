package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import kotlin.test.Test
import kotlin.test.assertEquals

class XdotoolArgsTest {
    @Test
    fun `forKey_givenCtrlShiftA_buildsChord`() {
        assertEquals(
            listOf("xdotool", "key", "--clearmodifiers", "ctrl+shift+a"),
            XdotoolArgs.forKey(Key.A, setOf(KeyModifier.SHIFT, KeyModifier.CTRL)),
        )
    }

    @Test
    fun `forText_givenDashLeadingText_endsOptions`() {
        assertEquals(listOf("xdotool", "type", "--clearmodifiers", "--delay", "1", "--", "-rf"), XdotoolArgs.forText("-rf"))
    }

    @Test
    fun `timeoutMs_givenThousandCharChunk_allowsWholeChunkToType`() {
        // xdotool's default 12 ms/char would need 12 s; with --delay 1 the budget must still cover it.
        assert(XdotoolArgs.timeoutMs("x".repeat(1_000)) >= 1_000 * 1 + 2_000)
        assertEquals(2_000L, XdotoolArgs.timeoutMs(""))
    }
}
