package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppCatalogServiceTest {
    private class CountingApps : AppActuator {
        var scans = 0
        var apps = listOf(DesktopApp("a", "Alpha"))
        override fun installedApps(): List<DesktopApp> { scans++; return apps }
        override fun launch(app: DesktopApp) = Unit
    }

    private val dir = Files.createTempDirectory("deskbuddy-catalog")
    private val apps = CountingApps()
    private var now = 0L
    private val service = AppCatalogService(
        apps, ShortcutStore(dir.resolve("shortcuts.json")),
        clock = { now }, staleMs = 30_000L, background = { it() },
    )

    @Test
    fun `forPhone_givenFreshScan_servesCacheWithoutRescan`() {
        service.forPhone()
        now = 10_000L
        service.forPhone()
        assertEquals(1, apps.scans)
    }

    @Test
    fun `forPhone_givenStaleScan_rescansInBackgroundAndPublishesChange`() {
        service.forPhone()
        apps.apps = apps.apps + DesktopApp("b", "Beta")
        now = 31_000L
        service.forPhone()
        assertEquals(2, apps.scans)
        assertEquals(listOf("Alpha", "Beta"), service.catalog.value.apps.map { it.name })
    }

    @Test
    fun `shortcutStore_givenFileDeletedAfterWrite_servesFromMemory`() {
        val file = dir.resolve("s.json")
        val store = ShortcutStore(file)
        store.add(DesktopApp("a", "Alpha"))
        Files.delete(file)
        assertEquals(listOf(DesktopApp("a", "Alpha")), store.load())
    }

    private fun serviceWith(installed: List<DesktopApp>, launched: MutableList<String>) = AppCatalogService(
        object : AppActuator {
            override fun installedApps() = installed
            override fun launch(app: DesktopApp) { launched += app.id }
        },
        ShortcutStore(Files.createTempDirectory("sc").resolve("shortcuts.json")),
    )

    @Test
    fun `activate_givenFocusSucceeds_doesNotLaunch`() {
        val launched = mutableListOf<String>()
        val service = serviceWith(listOf(DesktopApp("a", "A")), launched)
        assertTrue(service.activate("a") { true })
        assertTrue(launched.isEmpty())
    }

    @Test
    fun `activate_givenNoWindow_launches`() {
        val launched = mutableListOf<String>()
        val service = serviceWith(listOf(DesktopApp("a", "A")), launched)
        assertTrue(service.activate("a") { false })
        assertEquals(listOf("a"), launched)
    }

    @Test
    fun `activate_givenUnknownId_returnsFalse`() {
        assertFalse(serviceWith(emptyList(), mutableListOf()).activate("zzz") { true })
    }

    @Test
    fun `catalog_givenNewDesktop_advertisesActivate`() {
        assertTrue(serviceWith(emptyList(), mutableListOf()).current().canActivate)
    }
}
