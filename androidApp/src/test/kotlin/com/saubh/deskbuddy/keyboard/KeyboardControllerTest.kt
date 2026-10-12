package com.saubh.deskbuddy.keyboard

import com.saubh.deskbuddy.FakeGateway
import com.saubh.deskbuddy.keyboard.TextDiff.SENTINEL
import com.saubh.deskbuddy.protocol.Command
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.protocol.KeyPressCommand
import com.saubh.deskbuddy.protocol.KeyTextCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardControllerTest {
    @Test
    fun `onFieldEdit_givenAutocorrect_sendsBackspacesThenTextInOrder`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.onFieldEdit("${SENTINEL}teh", "${SENTINEL}the")
        runCurrent()
        assertEquals(
            listOf<Command>(KeyPressCommand(Key.BACKSPACE), KeyPressCommand(Key.BACKSPACE), KeyTextCommand("he")),
            gateway.sent,
        )
    }

    @Test
    fun `onFieldEdit_givenArmedCtrlAndLetter_sendsComboAndKeepsField`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.tapModifier(KeyModifier.CTRL)
        val display = controller.onFieldEdit(SENTINEL, "${SENTINEL}s")
        runCurrent()
        assertEquals(SENTINEL, display)
        assertEquals(listOf<Command>(KeyPressCommand(Key.S, setOf(KeyModifier.CTRL))), gateway.sent)
        assertEquals(emptySet(), controller.modifiers.value.active)
    }

    @Test
    fun `onFieldEdit_givenLongPaste_splitsIntoChunksUnderDesktopLimit`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.onFieldEdit(SENTINEL, SENTINEL + "x".repeat(2_500))
        runCurrent()
        assertEquals(listOf(1_000, 1_000, 500), gateway.sent.map { (it as KeyTextCommand).text.length })
    }

    @Test
    fun `onFieldEdit_givenArmedCtrlAndBackspace_sendsCtrlBackspace`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.tapModifier(KeyModifier.CTRL)
        controller.onFieldEdit("${SENTINEL}ab", "${SENTINEL}a")
        runCurrent()
        assertEquals(listOf<Command>(KeyPressCommand(Key.BACKSPACE, setOf(KeyModifier.CTRL))), gateway.sent)
    }

    @Test
    fun `onFieldEdit_givenArmedCtrlAndUppercaseLetter_addsShift`() = runTest {
        val gateway = FakeGateway()
        val controller = KeyboardController(gateway, backgroundScope)
        controller.tapModifier(KeyModifier.CTRL)
        controller.onFieldEdit(SENTINEL, "${SENTINEL}T")
        runCurrent()
        assertEquals(listOf<Command>(KeyPressCommand(Key.T, setOf(KeyModifier.CTRL, KeyModifier.SHIFT))), gateway.sent)
    }

    @Test
    fun `outbox_givenSendThrows_keepsSendingLaterKeys`() = runTest {
        val gateway = FakeGateway().apply { failNextSends = 1 }
        val controller = KeyboardController(gateway, backgroundScope)
        controller.press(Key.ENTER)
        controller.press(Key.TAB)
        runCurrent()
        assertEquals(listOf<Command>(KeyPressCommand(Key.TAB)), gateway.sent)
    }
}
