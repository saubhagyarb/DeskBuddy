package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.AudioDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MacMediaActuatorTest {
    private class FakeAudio(var level: Int, var muted: Boolean = false) : AudioActuator {
        override fun devices() = emptyList<AudioDevice>()
        override fun volume() = level
        override fun setVolume(percent: Int) { level = percent }
        override fun isMuted() = muted
        override fun setDefaultDevice(id: String) = false
    }

    private fun actuator(audio: FakeAudio, scripts: MutableList<List<String>> = mutableListOf(), result: String? = "ok") =
        MacMediaActuator(audio, setMuted = { audio.muted = it }, runJxa = { _, args -> scripts += args; result })

    @Test
    fun `volumeUp_givenNearMax_clampsToHundred`() {
        val audio = FakeAudio(97)
        actuator(audio).volumeUp()
        assertEquals(100, audio.level)
    }

    @Test
    fun `volumeDown_givenLevel_stepsDown`() {
        val audio = FakeAudio(50)
        actuator(audio).volumeDown()
        assertEquals(50 - MacMediaActuator.VOLUME_STEP, audio.level)
    }

    @Test
    fun `muteToggle_givenUnmuted_mutes`() {
        val audio = FakeAudio(30, muted = false)
        actuator(audio).muteToggle()
        assertEquals(true, audio.muted)
    }

    @Test
    fun `next_givenScriptRunner_postsNextKeyCode`() {
        val scripts = mutableListOf<List<String>>()
        actuator(FakeAudio(0), scripts).next()
        assertEquals(listOf(listOf(MacMediaActuator.NX_KEYTYPE_NEXT.toString())), scripts)
    }

    @Test
    fun `playPause_givenAdapterSucceeds_doesNotPostMediaKey`() {
        val scripts = mutableListOf<String>()
        val sent = mutableListOf<Int>()
        val actuator = MacMediaActuator(FakeAudio(0), {}, runJxa = { s, _ -> scripts += s; "ok" }, adapterSend = { sent += it; true })
        actuator.playPause()
        actuator.next()
        actuator.previous()
        assertEquals(listOf(2, 4, 5), sent)
        assertTrue(scripts.isEmpty())
    }

    @Test
    fun `playPause_givenAdapterFails_fallsBackToMediaKey`() {
        val args = mutableListOf<List<String>>()
        val actuator = MacMediaActuator(FakeAudio(0), {}, runJxa = { _, a -> args += a; "ok" }, adapterSend = { false })
        actuator.playPause()
        assertEquals(listOf(listOf(MacMediaActuator.NX_KEYTYPE_PLAY.toString())), args)
    }

    @Test
    fun `playPause_givenScriptFailure_throws`() {
        assertFailsWith<IllegalStateException> { actuator(FakeAudio(0), result = null).playPause() }
    }
}
