package com.saubh.deskbuddy.server.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsAudioActuatorTest {
    @Test
    fun `levels_onWindows_matchesSeparateReads`() {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val audio = WindowsAudioActuator()
        val separate = runCatching { audio.volume() to audio.isMuted() }.getOrNull() ?: return // no audio endpoint
        val levels = audio.levels()
        assertEquals(separate.first, levels.volume)
        assertEquals(separate.second, levels.muted)
        assertEquals(levels, audio.levels()) // second call reuses the cached endpoint
    }
}
