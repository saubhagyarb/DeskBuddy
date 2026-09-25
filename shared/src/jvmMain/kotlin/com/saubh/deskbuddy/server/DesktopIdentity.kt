package com.saubh.deskbuddy.server

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Random id created once per PC so phones recognise it after its IP address or name changes. */
class DesktopIdentity(
    private val file: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "desktop-id"),
) {
    val id: String by lazy {
        runCatching { Files.readString(file).trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { fresh ->
                runCatching {
                    Files.createDirectories(file.parent)
                    Files.writeString(file, fresh)
                }
            }
    }
}
