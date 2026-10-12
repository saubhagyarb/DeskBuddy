package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.RunningApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsWindowActuatorTest {
    private val word = TopLevelWindow(1, "Doc", 10, "C:\\WINWORD.EXE", "Word")
    private val word2 = TopLevelWindow(2, "Notes", 10, "C:\\WINWORD.EXE", "Word")
    private val chrome = TopLevelWindow(3, "Mail", 20, "C:\\chrome.exe", "Chrome")
    private val closed = mutableListOf<Long>()
    private val tabCloses = mutableListOf<Triple<Long, Int, String>>()
    private val tabRequests = mutableListOf<List<Long>>()

    private val actuator = WindowsWindowActuator(
        listWindows = { listOf(word, word2, chrome) },
        postClose = { closed += it; true },
        listTabs = { handles -> tabRequests += handles; mapOf(3L to listOf(BrowserTab(0, "Mail"))) },
        startCloseTab = { h, i, t -> tabCloses += Triple(h, i, t) },
    )

    @Test
    fun `runningApps_givenBrowserWindow_readsTabsOnlyForBrowsers`() {
        val apps = actuator.runningApps()
        assertEquals(listOf(listOf(3L)), tabRequests)
        assertEquals(listOf(BrowserTab(0, "Mail")), apps.last().windows.single().tabs)
    }

    @Test
    fun `closeWindow_givenUnlistedHandle_refusesWithoutPosting`() {
        assertFalse(actuator.closeWindow("999"))
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `closeWindow_givenListedHandle_postsClose`() {
        assertTrue(actuator.closeWindow("2"))
        assertEquals(listOf(2L), closed)
    }

    @Test
    fun `closeApp_givenAppId_closesEveryWindowOfThatApp`() {
        assertTrue(actuator.closeApp("c:\\winword.exe"))
        assertEquals(listOf(1L, 2L), closed)
    }

    @Test
    fun `closeTab_givenNonBrowserWindow_refuses`() {
        assertFalse(actuator.closeTab("1", 0, "Doc"))
        assertTrue(tabCloses.isEmpty())
    }

    @Test
    fun `closeTab_givenBrowserWindow_startsClose`() {
        assertTrue(actuator.closeTab("3", 0, "Mail"))
        assertEquals(listOf(Triple(3L, 0, "Mail")), tabCloses)
    }

    private val codeWindow = TopLevelWindow(handle = 7, title = "x.kt", processId = 1, exePath = "C:\\Code\\Code.exe", appName = "Code")
    private val codeTargets = ShortcutTargets(resolveLnk = { "C:\\Code\\Code.exe" })

    @Test
    fun `focusApp_givenRunningShortcutTarget_focusesItsFrontWindow`() {
        val focused = mutableListOf<Long>()
        val actuator = WindowsWindowActuator(listWindows = { listOf(codeWindow) }, targets = codeTargets, focusWindow = { focused += it; true })
        assertTrue(actuator.focusApp(DesktopApp("C:\\Start\\Code.lnk", "Code")))
        assertEquals(listOf(7L), focused)
    }

    @Test
    fun `focusApp_givenNoMatchingWindow_returnsFalse`() {
        val actuator = WindowsWindowActuator(listWindows = { emptyList() }, targets = codeTargets, focusWindow = { true })
        assertFalse(actuator.focusApp(DesktopApp("C:\\Start\\Code.lnk", "Code")))
    }

    @Test
    fun `focusApp_givenFocusRefused_stillReportsFocused`() {
        val focused = mutableListOf<Long>()
        val actuator = WindowsWindowActuator(listWindows = { listOf(codeWindow) }, targets = codeTargets, focusWindow = { focused += it; false })
        // Windows may refuse the foreground and only flash the taskbar; launching a duplicate would be worse.
        assertTrue(actuator.focusApp(DesktopApp("C:\\Start\\Code.lnk", "Code")))
        assertEquals(listOf(7L), focused)
    }

    @Test
    fun `shortcutFor_givenRunningExeOfPinnedLnk_returnsShortcutId`() {
        val actuator = WindowsWindowActuator(listWindows = { emptyList() }, targets = codeTargets)
        val running = RunningApp("c:\\code\\code.exe", "Code", emptyList())
        assertEquals("C:\\Start\\Code.lnk", actuator.shortcutFor(running, listOf(DesktopApp("C:\\Start\\Code.lnk", "Code"))))
    }
}

