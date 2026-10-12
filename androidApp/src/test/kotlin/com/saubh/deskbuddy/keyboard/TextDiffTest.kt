package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.keyboard.TextDiff.SENTINEL
import kotlin.test.Test
import kotlin.test.assertEquals

class TextDiffTest {
    @Test
    fun `between_givenAppend_insertsOnlyNewText`() {
        assertEquals(TextDiff.Result(0, "lo"), TextDiff.between("${SENTINEL}hel", "${SENTINEL}hello"))
    }

    @Test
    fun `between_givenAutocorrectReplace_backspacesThenRetypes`() {
        // Common prefix is sentinel + "t": delete "eh", type "he ".
        assertEquals(TextDiff.Result(2, "he "), TextDiff.between("${SENTINEL}teh", "${SENTINEL}the "))
    }

    @Test
    fun `between_givenSentinelDeleted_countsOneBackspace`() {
        assertEquals(TextDiff.Result(1, ""), TextDiff.between(SENTINEL, ""))
    }

    @Test
    fun `between_givenEmojiReplaced_doesNotSplitSurrogates`() {
        // 😀 = D83D DE00, 😃 = D83D DE03: same high surrogate.
        assertEquals(TextDiff.Result(1, "😃"), TextDiff.between("${SENTINEL}a😀", "${SENTINEL}a😃"))
    }

    @Test
    fun `display_givenSentinelRemoved_restoresIt`() {
        assertEquals(SENTINEL, TextDiff.display(""))
        assertEquals("${SENTINEL}x", TextDiff.display("${SENTINEL}x"))
    }

    @Test
    fun `between_givenSelectAllAndType_doesNotCountSentinelAsPcCharacter`() {
        assertEquals(TextDiff.Result(3, "x"), TextDiff.between("${SENTINEL}abc", "x"))
    }

    @Test
    fun `between_givenTypingBeforeSentinel_retypesWithoutExtraBackspace`() {
        assertEquals(TextDiff.Result(3, "xabc"), TextDiff.between("${SENTINEL}abc", "x${SENTINEL}abc"))
    }
}
