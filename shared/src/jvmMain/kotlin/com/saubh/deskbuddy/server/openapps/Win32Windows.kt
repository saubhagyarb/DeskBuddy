package com.saubh.deskbuddy.server.openapps

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.Version
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.util.concurrent.ConcurrentHashMap

/** Alt+Tab-style window enumeration and `WM_CLOSE`, via JNA. */
object Win32Windows {
    private const val GW_OWNER = 4
    private const val GWL_EXSTYLE = -20
    private const val WS_EX_TOOLWINDOW = 0x00000080
    private const val WS_EX_APPWINDOW = 0x00040000
    private const val DWMWA_CLOAKED = 14
    private const val WM_CLOSE = 0x0010
    private const val PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
    private const val UWP_HOST = "applicationframehost.exe"

    /** Desktop and taskbar windows that are visible and titled but never "open apps". */
    private val SHELL_CLASSES = setOf("Progman", "WorkerW", "Shell_TrayWnd", "Shell_SecondaryTrayWnd")

    private interface Dwmapi : StdCallLibrary {
        fun DwmGetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int

        companion object {
            val INSTANCE: Dwmapi? = runCatching {
                Native.load("dwmapi", Dwmapi::class.java, W32APIOptions.DEFAULT_OPTIONS)
            }.getOrNull()
        }
    }

    private const val SW_RESTORE = 9
    private const val VK_MENU: Byte = 0x12
    private const val KEYEVENTF_KEYUP = 0x0002

    private interface FocusApi : StdCallLibrary {
        fun IsIconic(hwnd: WinDef.HWND): Boolean
        fun keybd_event(bVk: Byte, bScan: Byte, dwFlags: Int, dwExtraInfo: Pointer?)

        companion object {
            val INSTANCE: FocusApi? = runCatching { Native.load("user32", FocusApi::class.java, W32APIOptions.DEFAULT_OPTIONS) }.getOrNull()
        }
    }

    /**
     * Restores and foregrounds a window. Windows only lets the foreground process move focus; a
     * synthetic Alt tap counts as user input and lifts that lock for this call.
     */
    fun focus(handle: Long): Boolean {
        val hwnd = WinDef.HWND(Pointer(handle))
        val user32 = User32.INSTANCE
        if (!user32.IsWindow(hwnd)) return false
        val api = FocusApi.INSTANCE
        if (api?.IsIconic(hwnd) == true) user32.ShowWindow(hwnd, SW_RESTORE)
        api?.keybd_event(VK_MENU, 0, 0, null)
        api?.keybd_event(VK_MENU, 0, KEYEVENTF_KEYUP, null)
        return user32.SetForegroundWindow(hwnd)
    }

    private val appNames =ConcurrentHashMap<String, String>()

    /** Top-level windows in Z-order (front first), excluding this process's own windows. */
    fun list(): List<TopLevelWindow> {
        val ownPid = ProcessHandle.current().pid().toInt()
        val found = mutableListOf<TopLevelWindow>()
        User32.INSTANCE.EnumWindows({ hwnd, _ ->
            runCatching { describe(hwnd) }.getOrNull()?.takeIf { it.processId != ownPid }?.let(found::add)
            true
        }, null)
        return found
    }

    /** Posts `WM_CLOSE`, as the window's own close button would. */
    fun close(handle: Long): Boolean {
        val hwnd = WinDef.HWND(Pointer(handle))
        if (!User32.INSTANCE.IsWindow(hwnd)) return false
        User32.INSTANCE.PostMessage(hwnd, WM_CLOSE, WinDef.WPARAM(0), WinDef.LPARAM(0))
        return true
    }

    private fun describe(hwnd: WinDef.HWND): TopLevelWindow? {
        val user32 = User32.INSTANCE
        if (!user32.IsWindowVisible(hwnd)) return null
        if (user32.GetWindow(hwnd, WinDef.DWORD(GW_OWNER.toLong())) != null) return null
        val exStyle = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        if (exStyle and WS_EX_TOOLWINDOW != 0 && exStyle and WS_EX_APPWINDOW == 0) return null
        if (isCloaked(hwnd)) return null
        val className = CharArray(256).let { buf -> String(buf, 0, user32.GetClassName(hwnd, buf, buf.size)) }
        if (className in SHELL_CLASSES) return null
        val title = CharArray(user32.GetWindowTextLength(hwnd) + 1)
            .let { buf -> String(buf, 0, user32.GetWindowText(hwnd, buf, buf.size)) }
            .trim()
        if (title.isEmpty()) return null
        val pid = IntByReference().also { user32.GetWindowThreadProcessId(hwnd, it) }.value
        val exe = exePath(pid)
        // Store apps all live in ApplicationFrameHost: tell them apart by their title.
        if (exe.substringAfterLast('\\').equals(UWP_HOST, ignoreCase = true)) {
            return TopLevelWindow(Pointer.nativeValue(hwnd.pointer), title, pid, "uwp:$title", title)
        }
        val name = if (exe.isEmpty()) "" else appNames.getOrPut(exe) { fileDescription(exe) }
        return TopLevelWindow(Pointer.nativeValue(hwnd.pointer), title, pid, exe, name)
    }

    /** Suspended Store apps and windows on other virtual desktops are "visible" but cloaked. */
    private fun isCloaked(hwnd: WinDef.HWND): Boolean {
        val dwm = Dwmapi.INSTANCE ?: return false
        val cloaked = IntByReference()
        return dwm.DwmGetWindowAttribute(hwnd, DWMWA_CLOAKED, cloaked, 4) == 0 && cloaked.value != 0
    }

    private fun exePath(pid: Int): String {
        val process: WinNT.HANDLE = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, pid) ?: return ""
        return try {
            val buffer = CharArray(1024)
            val size = IntByReference(buffer.size)
            if (Kernel32.INSTANCE.QueryFullProcessImageName(process, 0, buffer, size)) String(buffer, 0, size.value) else ""
        } finally {
            Kernel32.INSTANCE.CloseHandle(process)
        }
    }

    /** `FileDescription` from the executable's version resource ("Google Chrome"), or empty. */
    private fun fileDescription(exe: String): String = runCatching {
        val version = Version.INSTANCE
        val size = version.GetFileVersionInfoSize(exe, IntByReference())
        if (size <= 0) return@runCatching ""
        val block = com.sun.jna.Memory(size.toLong())
        if (!version.GetFileVersionInfo(exe, 0, size, block)) return@runCatching ""
        val translation = PointerByReference()
        val length = IntByReference()
        if (!version.VerQueryValue(block, "\\VarFileInfo\\Translation", translation, length) || length.value < 4) {
            return@runCatching ""
        }
        val lang = translation.value.getShort(0).toInt() and 0xFFFF
        val codePage = translation.value.getShort(2).toInt() and 0xFFFF
        val key = "\\StringFileInfo\\%04x%04x\\FileDescription".format(lang, codePage)
        val value = PointerByReference()
        if (version.VerQueryValue(block, key, value, length) && length.value > 0) value.value.getWideString(0) else ""
    }.getOrDefault("")
}
