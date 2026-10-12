package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.WireCodec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlServerLogTest {
    @Test
    fun `undecodableFrameLog_givenFrameWithToken_neverLogsTheToken`() {
        val frame = """{"token":"s3cret-token-value","command":{"type":"no.such.command"}}"""
        val error = runCatching { WireCodec.decode(frame) }.exceptionOrNull() ?: error("frame must not decode")

        val line = undecodableFrameLog(error, frame.length)

        assertFalse("s3cret-token-value" in line, line)
        assertTrue(error::class.simpleName.orEmpty() in line, line)
        assertTrue("${frame.length}" in line, line)
    }
}
