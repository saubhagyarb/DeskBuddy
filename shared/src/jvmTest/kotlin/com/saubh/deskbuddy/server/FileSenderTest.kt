package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.FileChunkCommand
import com.saubh.deskbuddy.protocol.FileCompleteCommand
import com.saubh.deskbuddy.protocol.FileOfferCommand
import com.saubh.deskbuddy.protocol.Message
import com.saubh.deskbuddy.protocol.Push
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSenderTest {
    @Test
    fun `send_givenSeventyKilobyteFile_pushesOfferThreeChunksAndComplete`() = runBlocking {
        val file = Files.createTempFile("deskbuddy", ".bin").also { Files.write(it, ByteArray(70 * 1024) { 1 }) }
        val pushed = mutableListOf<Message>()
        val sent = FileSender { pushed += it; true }.send(file)
        val commands = pushed.map { (it as Push).command }
        assertTrue(sent)
        assertTrue(commands.first() is FileOfferCommand)
        assertEquals(listOf(0, 1, 2), commands.filterIsInstance<FileChunkCommand>().map { it.index })
        assertTrue(commands.last() is FileCompleteCommand)
    }
}
