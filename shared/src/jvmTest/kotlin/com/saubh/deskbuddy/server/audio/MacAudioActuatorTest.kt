package com.saubh.deskbuddy.server.audio

import com.saubh.deskbuddy.server.AudioLevels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MacAudioActuatorTest {
    @Test
    fun `fourCc_givenCoreAudioCodes_matchesHeaderValues`() {
        assertEquals(0x64657623, FourCc.of("dev#")) // kAudioHardwarePropertyDevices
        assertEquals(0x6F757470, FourCc.of("outp")) // kAudioObjectPropertyScopeOutput
        assertEquals(0x75696420, FourCc.of("uid ")) // trailing space is significant
    }

    @Test
    fun `fourCc_givenWrongLength_throws`() {
        assertFailsWith<IllegalArgumentException> { FourCc.of("dev") }
    }

    @Test
    fun `parseScriptLevels_givenVolumeAndMute_returnsLevels`() {
        assertEquals(AudioLevels(42, muted = true), MacAudioActuator.parseScriptLevels("42,true"))
        assertEquals(AudioLevels(100, muted = false), MacAudioActuator.parseScriptLevels("100,false"))
    }

    @Test
    fun `parseScriptLevels_givenMissingValue_returnsNull`() {
        assertNull(MacAudioActuator.parseScriptLevels("missing value,missing value"))
    }

    @Test
    fun `toPercent_givenScalar_roundsAndClamps`() {
        assertEquals(50, MacAudioActuator.toPercent(0.499f))
        assertEquals(100, MacAudioActuator.toPercent(1.2f))
    }
}
