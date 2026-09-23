package com.saubh.deskbuddy.server

import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser

/**
 * Sends media/volume virtual-key taps via Win32 SendInput. These keys are
 * handled system-wide, so they reach whichever app owns the media session.
 * java.awt.Robot cannot send them — AWT defines no VK codes for media keys.
 */
class WindowsMediaActuator : MediaActuator {

    private companion object {
        const val VK_MEDIA_PLAY_PAUSE = 0xB3
        const val VK_MEDIA_NEXT_TRACK = 0xB0
        const val VK_MEDIA_PREV_TRACK = 0xB1
        const val VK_VOLUME_UP = 0xAF
        const val VK_VOLUME_DOWN = 0xAE
        const val VK_VOLUME_MUTE = 0xAD
        const val KEYEVENTF_KEYUP = 0x0002
    }

    override fun playPause() = tap(VK_MEDIA_PLAY_PAUSE)
    override fun next() = tap(VK_MEDIA_NEXT_TRACK)
    override fun previous() = tap(VK_MEDIA_PREV_TRACK)
    override fun volumeUp() = tap(VK_VOLUME_UP)
    override fun volumeDown() = tap(VK_VOLUME_DOWN)
    override fun muteToggle() = tap(VK_VOLUME_MUTE)

    private fun tap(vk: Int) {
        sendKey(vk, flags = 0)
        sendKey(vk, flags = KEYEVENTF_KEYUP)
    }

    private fun sendKey(vk: Int, flags: Int) {
        val input = WinUser.INPUT()
        input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        input.input.setType("ki")
        input.input.ki.wVk = WinDef.WORD(vk.toLong())
        input.input.ki.wScan = WinDef.WORD(0)
        input.input.ki.time = WinDef.DWORD(0)
        input.input.ki.dwFlags = WinDef.DWORD(flags.toLong())
        input.input.ki.dwExtraInfo = BaseTSD.ULONG_PTR(0)
        User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(input), input.size())
    }
}
