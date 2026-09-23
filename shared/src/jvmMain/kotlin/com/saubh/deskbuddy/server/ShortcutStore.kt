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

    fun load(): List<DesktopApp> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

    fun add(app: DesktopApp) = save(load().filter { it.id != app.id } + app)

    fun remove(id: String) = save(load().filter { it.id != id })

    private fun save(apps: List<DesktopApp>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, apps))
    }
}
