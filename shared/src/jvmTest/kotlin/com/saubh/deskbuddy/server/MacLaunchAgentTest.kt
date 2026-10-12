package com.saubh.deskbuddy.server

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MacLaunchAgentTest {
    private val plist = Files.createTempDirectory("launch-agents").resolve("com.saubh.konnekt.plist")
    private val agent = MacLaunchAgent(plist)

    @Test
    fun `write_givenCommandWithXmlCharacters_readsBackUnchanged`() {
        val command = "\"/Applications/A & B <beta>.app/Contents/MacOS/KonneKt\" --background"
        agent.write(command)
        assertEquals(command, agent.read())
        assertTrue(Files.readString(plist).contains("&amp;"))
    }

    @Test
    fun `plistFor_givenCommand_runsItThroughShellAtLoad`() {
        val text = MacLaunchAgent.plistFor("\"/x\" --background")
        assertTrue(text.contains("<string>/bin/sh</string>"))
        assertTrue(text.contains("<string>exec \"/x\" --background</string>"))
        assertTrue(text.contains("<key>RunAtLoad</key>\n    <true/>"))
    }

    @Test
    fun `delete_givenWrittenAgent_removesFileAndReadsNull`() {
        agent.write("\"/x\"")
        agent.delete()
        assertFalse(Files.exists(plist))
        assertNull(agent.read())
    }

    @Test
    fun `commandIn_givenForeignPlist_returnsNull`() {
        assertNull(MacLaunchAgent.commandIn("<plist><dict></dict></plist>"))
    }
}
