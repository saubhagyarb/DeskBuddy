package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BrowserTabsTest {
    @Test
    fun `parse_givenArrayOfWindows_mapsHandlesToIndexedTabs`() {
        val parsed = BrowserTabs.parse("""[{"h":"100","t":["Mail","News"]},{"h":"200","t":[]}]""")
        assertEquals(listOf(BrowserTab(0, "Mail"), BrowserTab(1, "News")), parsed[100L])
        assertEquals(emptyList(), parsed[200L])
    }

    @Test
    fun `parse_givenSingleObjectWithBareStringTab_returnsOneTab`() {
        assertEquals(mapOf(5L to listOf(BrowserTab(0, "Only"))), BrowserTabs.parse("""{"h":"5","t":"Only"}"""))
    }

    @Test
    fun `parse_givenGarbage_returnsEmpty`() {
        assertTrue(BrowserTabs.parse("not json").isEmpty())
        assertTrue(BrowserTabs.parse("").isEmpty())
    }
}
