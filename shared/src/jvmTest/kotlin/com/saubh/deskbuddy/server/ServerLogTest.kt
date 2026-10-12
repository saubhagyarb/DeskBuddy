package com.saubh.deskbuddy.server

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerLogTest {
    @Test
    fun `write_givenLine_appendsWithTimestamp`() {
        val file = Files.createTempDirectory("log").resolve("server.log")
        ServerLogFile(file, maxBytes = 1_000).write("hello")
        assertTrue(file.readText().trim().endsWith("hello"))
    }

    @Test
    fun `write_givenFileOverLimit_rotatesToBackup`() {
        val dir = Files.createTempDirectory("log")
        val file = dir.resolve("server.log")
        val log = ServerLogFile(file, maxBytes = 50)
        repeat(5) { log.write("0123456789") }
        assertTrue(dir.resolve("server.log.1").exists())
        assertTrue(Files.size(file) <= 50)
    }

    @Test
    fun `write_givenUnwritableDirectory_doesNotThrow`() {
        val blocker = Files.createTempFile("not-a-dir", "")
        ServerLogFile(blocker.resolve("server.log"), maxBytes = 50).write("x")
        assertFalse(blocker.resolve("server.log").exists())
    }
}
