package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MacAppActuatorTest {
    private val system: Path = Files.createTempDirectory("mac-system")
    private val user: Path = Files.createTempDirectory("mac-user")

    private fun bundle(root: Path, relative: String): Path =
        Files.createDirectories(root.resolve(relative).resolve("Contents/MacOS"))

    @Test
    fun `installedApps_givenNestedAndInnerBundles_listsTopTwoLevelsOnly`() {
        bundle(system, "Safari.app")
        bundle(system, "Utilities/Terminal.app")
        bundle(system, "Xcode.app/Contents/Developer/Applications/Simulator.app")
        bundle(system, "A/B/TooDeep.app")
        Files.createFile(system.resolve("notes.txt"))

        val names = MacAppActuator(roots = listOf(system), launcher = {}).installedApps().map { it.name }

        assertEquals(listOf("Safari", "Terminal", "Xcode"), names)
    }

    @Test
    fun `installedApps_givenDuplicateNames_firstRootWins`() {
        val userSafari = bundle(user, "Safari.app").parent.parent
        bundle(system, "Safari.app")

        val apps = MacAppActuator(roots = listOf(user, system), launcher = {}).installedApps()

        assertEquals(listOf(userSafari.toAbsolutePath().toString()), apps.map { it.id })
    }

    @Test
    fun `installedApps_givenMissingRoot_skipsIt`() {
        val apps = MacAppActuator(roots = listOf(system.resolve("missing")), launcher = {}).installedApps()
        assertEquals(emptyList(), apps)
    }

    @Test
    fun `launch_givenApp_opensItsPath`() {
        val launched = mutableListOf<String>()
        MacAppActuator(roots = emptyList(), launcher = { launched += it }).launch(DesktopApp("/Applications/Notes.app", "Notes"))
        assertEquals(listOf("/Applications/Notes.app"), launched)
    }
}
