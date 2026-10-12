package com.saubh.deskbuddy.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

@Serializable
data class PairedDevice(val deviceName: String, val token: String)

class PairedDeviceStore(
    private val file: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "paired.json"),
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = ListSerializer(PairedDevice.serializer())
    private val lock = Any()

    /** Read once, then served from memory: the token is checked on every message, including file chunks and keystrokes. */
    @Volatile
    private var cache: List<PairedDevice>? = null

    fun load(): List<PairedDevice> = cache ?: synchronized(lock) { cache ?: readFile().also { cache = it } }

    fun add(device: PairedDevice) = synchronized(lock) { save(load().filter { it.token != device.token } + device) }

    fun isValidToken(token: String): Boolean = deviceNameForToken(token) != null

    fun deviceNameForToken(token: String): String? = load().firstOrNull { it.token == token }?.deviceName

    fun removeAll() = synchronized(lock) {
        Files.deleteIfExists(file)
        cache = emptyList()
    }

    private fun readFile(): List<PairedDevice> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    private fun save(devices: List<PairedDevice>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, devices))
        cache = devices
    }
}
