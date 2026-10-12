package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/** User-chosen app shortcuts, persisted as JSON. Order is insertion order. */
class ShortcutStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "shortcuts.json"),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(DesktopApp.serializer())
    private val lock = Any()

    /** Read once, then served from memory; every launch looks shortcuts up. */
    @Volatile
    private var cache: List<DesktopApp>? = null

    fun load(): List<DesktopApp> = cache ?: synchronized(lock) { cache ?: readFile().also { cache = it } }

    fun add(app: DesktopApp) = synchronized(lock) { save(load().filter { it.id != app.id } + app) }

    fun remove(id: String) = synchronized(lock) { save(load().filter { it.id != id }) }

    private fun readFile(): List<DesktopApp> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    private fun save(apps: List<DesktopApp>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, apps))
        cache = apps
    }
}
