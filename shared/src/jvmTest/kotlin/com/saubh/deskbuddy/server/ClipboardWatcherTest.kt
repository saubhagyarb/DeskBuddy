package com.saubh.deskbuddy.server

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardWatcherTest {
    private class CountingShare(var text: String?) : ShareActuator {
        var reads = 0
        override val lastWrittenText: String? = null
        override fun setClipboard(text: String) = Unit
        override fun getClipboard(): String? { reads++; return text }
        override fun onTextShared(text: String) = Unit
    }

    @Test
    fun `changes_givenUnchangedSequence_skipsClipboardReads`() = runBlocking {
        val share = CountingShare("a")
        val watcher = ClipboardWatcher(share, intervalMillis = 5, changeToken = { 7L })
        withTimeoutOrNull(100) { watcher.changes().first() }
        assertEquals(1, share.reads) // the initial read only
    }

    @Test
    fun `changes_givenLockedClipboardAfterBump_retriesUntilTextIsReadable`() = runBlocking {
        val share = CountingShare("a")
        var seq = 1L
        val watcher = ClipboardWatcher(share, intervalMillis = 5, changeToken = { seq })
        val emitted = async { withTimeout(1_000) { watcher.changes().first() } }
        delay(30)
        share.text = null // the copying app still holds the clipboard
        seq = 2L
        delay(30)
        share.text = "b" // sequence unchanged, but the copy must not be lost
        assertEquals("b", emitted.await())
    }

    @Test
    fun `changes_givenSequenceBump_readsAndEmitsNewText`() = runBlocking {
        val share = CountingShare("a")
        var seq = 1L
        val watcher = ClipboardWatcher(share, intervalMillis = 5, changeToken = { seq })
        val emitted = async { withTimeout(1_000) { watcher.changes().first() } }
        delay(30)
        share.text = "b"
        seq = 2L
        assertEquals("b", emitted.await())
    }
}
