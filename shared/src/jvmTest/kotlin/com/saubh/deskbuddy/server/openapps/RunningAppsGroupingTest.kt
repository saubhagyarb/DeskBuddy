package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunningAppsGroupingTest {
    private fun window(handle: Long, title: String, exe: String, name: String = "", pid: Int = 1) =
        TopLevelWindow(handle, title, pid, exe, name)

    @Test
    fun `group_givenWindowsInZOrder_keepsAppOfFrontWindowFirst`() {
        val apps = RunningAppsGrouping.group(
            listOf(
                window(1, "Doc - Word", "C:\\Office\\WINWORD.EXE", "Microsoft Word"),
                window(2, "Inbox - Chrome", "C:\\Chrome\\chrome.exe", "Google Chrome"),
                window(3, "Notes - Word", "C:\\Office\\WINWORD.EXE", "Microsoft Word"),
            ),
        )
        assertEquals(listOf("Microsoft Word", "Google Chrome"), apps.map { it.name })
        assertEquals(listOf("1", "3"), apps.first().windows.map { it.id })
    }

    @Test
    fun `group_givenTabsForWindow_attachesThemToThatWindow`() {
        val tabs = listOf(BrowserTab(0, "Mail"), BrowserTab(1, "News"))
        val apps = RunningAppsGrouping.group(listOf(window(7, "Mail", "C:\\b\\msedge.exe")), mapOf(7L to tabs))
        assertEquals(tabs, apps.single().windows.single().tabs)
    }

    @Test
    fun `displayName_givenNoVersionInfo_usesCapitalisedExeName`() {
        assertEquals("Notepad", RunningAppsGrouping.displayName(window(1, "x", "C:\\Windows\\notepad.exe")))
    }

    @Test
    fun `appId_givenUnreadableProcess_isUniquePerPid`() {
        assertEquals("pid:42", RunningAppsGrouping.appId(window(1, "Admin", "", pid = 42)))
    }

    @Test
    fun `isBrowser_givenKnownBrowserExe_isTrueCaseInsensitively`() {
        assertTrue(RunningAppsGrouping.isBrowser(window(1, "x", "C:\\Program Files\\Google\\Chrome\\Application\\Chrome.EXE")))
        assertFalse(RunningAppsGrouping.isBrowser(window(1, "x", "C:\\Windows\\explorer.exe")))
    }
}
