package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Win32 `SendInput`. Text goes as `KEYEVENTF_UNICODE`, so the phone's text arrives exactly,
 * whatever keyboard layout the PC uses. Elevated windows and the lock screen do not accept it (UIPI).
 */
class WindowsKeyboardActuator : KeyboardActuator {
    private companion object {
        const val MAPVK_VK_TO_VSC = 0
    }

    override fun type(text: String) = send(SendInputPlan.forText(text))

    override fun press(key: Key, modifiers: Set<KeyModifier>) = send(SendInputPlan.forKey(key, modifiers))

    /** Unicode input carries its UTF-16 unit; virtual keys also get a scan code, which games, RDP and VMs need. */
    private fun scanCode(key: KeyInput): Int =
        if (key.flags and SendInputPlan.KEYEVENTF_UNICODE != 0) key.scan
        else User32.INSTANCE.MapVirtualKeyEx(key.vk, MAPVK_VK_TO_VSC, User32.INSTANCE.GetKeyboardLayout(0))

    private fun send(plan: List<KeyInput>) {
        if (plan.isEmpty()) return
        @Suppress("UNCHECKED_CAST")
        val inputs = WinUser.INPUT().toArray(plan.size) as Array<WinUser.INPUT> // SendInput needs contiguous memory
        plan.forEachIndexed { i, key ->
            inputs[i].type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
            inputs[i].input.setType("ki")
            inputs[i].input.ki.wVk = WinDef.WORD(key.vk.toLong())
            inputs[i].input.ki.wScan = WinDef.WORD(scanCode(key).toLong())
            inputs[i].input.ki.time = WinDef.DWORD(0)
            inputs[i].input.ki.dwFlags = WinDef.DWORD(key.flags.toLong())
            inputs[i].input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)
        }
        User32.INSTANCE.SendInput(WinDef.DWORD(inputs.size.toLong()), inputs, inputs[0].size())
    }
}
