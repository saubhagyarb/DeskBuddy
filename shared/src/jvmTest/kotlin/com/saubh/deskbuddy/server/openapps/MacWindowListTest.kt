package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacWindowListTest {
    private val json = """
        [{"pid":42,"bundleId":"com.google.Chrome","name":"Google Chrome","frontmost":true,
          "windows":[{"index":0,"title":"Mail","browserWindow":1,"tabs":["Mail","News"]}]},
         {"pid":7,"bundleId":"","name":"tool","windows":[{"index":2,"title":""}]}]
    """.trimIndent()

    @Test
    fun `parse_givenBrowserAndBareProcess_readsWindowsAndTabs`() {
        val windows = MacWindowList.parse(json)
        assertEquals(
            MacWindow(42, "com.google.Chrome", "Google Chrome", 0, "Mail", 1, listOf(BrowserTab(0, "Mail"), BrowserTab(1, "News"))),
            windows[0],
        )
        assertNull(windows[1].browserWindow)
        assertEquals("pid:7", windows[1].appId)
        assertEquals("7:2", windows[1].id)
    }

    @Test
    fun `parse_givenGarbage_returnsEmpty`() {
        assertTrue(MacWindowList.parse("not json").isEmpty())
        assertTrue(MacWindowList.parse("").isEmpty())
        assertTrue(MacWindowList.parse("""{"pid":1}""").isEmpty())
    }

    @Test
    fun `group_givenTwoWindowsOfOneApp_keepsOrderAndShowsAppNameForBlankTitle`() {
        val apps = MacWindowList.group(
            listOf(
                MacWindow(1, "com.apple.TextEdit", "TextEdit", 0, "Notes"),
                MacWindow(2, "com.apple.Safari", "Safari", 0, "Home"),
                MacWindow(1, "com.apple.TextEdit", "TextEdit", 1, ""),
            ),
        )
        assertEquals(listOf("com.apple.TextEdit", "com.apple.Safari"), apps.map { it.id })
        assertEquals(listOf("1:0" to "Notes", "1:1" to "TextEdit"), apps[0].windows.map { it.id to it.title })
    }
}
