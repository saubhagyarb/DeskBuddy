package com.saubh.deskbuddy.keyboard

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidKeyMappingTest {
    @Test
    fun `keyCode_givenLettersDigitsAndSpecials_mapsToKeyEventCodes`() {
        assertEquals(KeyEvent.KEYCODE_A, AndroidKeyMapping.keyCode(Key.A))
        assertEquals(KeyEvent.KEYCODE_Z, AndroidKeyMapping.keyCode(Key.Z))
        assertEquals(KeyEvent.KEYCODE_0, AndroidKeyMapping.keyCode(Key.DIGIT_0))
        assertEquals(KeyEvent.KEYCODE_9, AndroidKeyMapping.keyCode(Key.DIGIT_9))
        assertEquals(KeyEvent.KEYCODE_DEL, AndroidKeyMapping.keyCode(Key.BACKSPACE))
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, AndroidKeyMapping.keyCode(Key.DELETE))
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, AndroidKeyMapping.keyCode(Key.HOME))
    }

    @Test
    fun `metaState_givenCtrlShift_setsBothFlags`() {
        val expected = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertEquals(expected, AndroidKeyMapping.metaState(setOf(KeyModifier.CTRL, KeyModifier.SHIFT)))
    }

    @Test
    fun `enterAction_givenSendField_returnsSend`() {
        assertEquals(EditorInfo.IME_ACTION_SEND, AndroidKeyMapping.enterAction(EditorInfo.IME_ACTION_SEND))
    }

    @Test
    fun `enterAction_givenNoEnterActionFlagOrNone_returnsNull`() {
        assertNull(AndroidKeyMapping.enterAction(EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertNull(AndroidKeyMapping.enterAction(EditorInfo.IME_ACTION_UNSPECIFIED))
    }
}
