package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacWindowActuatorTest {
    private val doc = MacWindow(10, "com.apple.TextEdit", "TextEdit", 0, "Doc")
    private val doc2 = MacWindow(10, "com.apple.TextEdit", "TextEdit", 1, "Notes")
    private val safari = MacWindow(20, "com.apple.Safari", "Safari", 0, "Mail", browserWindow = 0, tabs = listOf(BrowserTab(0, "Mail")))
    private val closed = mutableListOf<String>()
    private val quit = mutableListOf<String>()
    private val tabCloses = mutableListOf<Triple<String, Int, String>>()

    private val actuator = MacWindowActuator(
        listWindows = { listOf(doc, doc2, safari) },
        pressClose = { closed += it.id; true },
        startQuit = { quit += it },
        startCloseTab = { w, i, t -> tabCloses += Triple(w.id, i, t) },
    )

    @Test
    fun `runningApps_givenWindows_groupsByBundleWithTabs`() {
        val apps = actuator.runningApps()
        assertEquals(listOf("com.apple.TextEdit", "com.apple.Safari"), apps.map { it.id })
        assertEquals(listOf(BrowserTab(0, "Mail")), apps.last().windows.single().tabs)
    }

    @Test
    fun `closeWindow_givenUnlistedId_refusesWithoutClosing`() {
        assertFalse(actuator.closeWindow("10:9"))
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `closeWindow_givenListedId_pressesClose`() {
        assertTrue(actuator.closeWindow("10:1"))
        assertEquals(listOf("10:1"), closed)
    }

    @Test
    fun `closeApp_givenListedApp_startsQuit`() {
        assertTrue(actuator.closeApp("com.apple.TextEdit"))
        assertEquals(listOf("com.apple.TextEdit"), quit)
    }

    @Test
    fun `closeApp_givenAppWithoutWindows_refuses`() {
        assertFalse(actuator.closeApp("com.apple.Music"))
        assertTrue(quit.isEmpty())
    }

    @Test
    fun `closeTab_givenNonBrowserWindow_refuses`() {
        assertFalse(actuator.closeTab("10:0", 0, "Doc"))
        assertTrue(tabCloses.isEmpty())
    }

    @Test
    fun `closeTab_givenBrowserWindow_startsClose`() {
        assertTrue(actuator.closeTab("20:0", 0, "Mail"))
        assertEquals(listOf(Triple("20:0", 0, "Mail")), tabCloses)
    }
}
