package com.saubh.deskbuddy.server.openapps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortcutTargetsTest {
    @Test
    fun `target_givenLnkExeAndAppsFolderIds_resolvesLowercasedExeOrNull`() {
        var lookups = 0
        val targets = ShortcutTargets(resolveLnk = { lookups++; "C:\\Program Files\\Code\\Code.exe" })
        assertEquals("c:\\program files\\code\\code.exe", targets.target("C:\\Start\\Code.lnk"))
        assertEquals("c:\\program files\\code\\code.exe", targets.target("C:\\Start\\Code.lnk"))
        assertEquals(1, lookups) // cached
        assertEquals("c:\\tools\\app.exe", targets.target("C:\\Tools\\App.exe"))
        assertNull(targets.target("Microsoft.WindowsCalculator_8wekyb3d8bbwe!App"))
    }

    @Test
    fun `target_givenUnresolvableLnk_returnsNull`() {
        assertNull(ShortcutTargets(resolveLnk = { null }).target("C:\\x.lnk"))
    }
}
