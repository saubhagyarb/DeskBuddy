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

    fun load(): List<PairedDevice> =
        if (Files.exists(file)) {
            runCatching { json.decodeFromString(serializer, Files.readString(file)) }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

    fun add(device: PairedDevice) = save(load().filter { it.token != device.token } + device)

    fun isValidToken(token: String): Boolean = load().any { it.token == token }

    fun deviceNameForToken(token: String): String? =
        load().firstOrNull { it.token == token }?.deviceName

    fun removeAll() {
        Files.deleteIfExists(file)
    }

    private fun save(devices: List<PairedDevice>) {
        Files.createDirectories(file.parent)
        Files.writeString(file, json.encodeToString(serializer, devices))
    }
}
