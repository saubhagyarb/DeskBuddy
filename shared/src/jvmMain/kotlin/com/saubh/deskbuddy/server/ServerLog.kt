package com.saubh.deskbuddy.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant

/** Append-only log with one rotated backup. Never throws: logging must not break the command it reports on. */
class ServerLogFile(private val file: Path, private val maxBytes: Long) {
    @Synchronized
    fun write(line: String) {
        runCatching {
            Files.createDirectories(file.parent)
            if (Files.exists(file) && Files.size(file) + line.length > maxBytes) {
                Files.move(file, file.resolveSibling("${file.fileName}.1"), StandardCopyOption.REPLACE_EXISTING)
            }
            Files.writeString(file, "${Instant.now()} $line\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }
}

/** `~/.deskbuddy/server.log`, also echoed to stderr for `./gradlew run`. No tokens or payloads go in here. */
object ServerLog {
    private val file = ServerLogFile(Path.of(System.getProperty("user.home"), ".deskbuddy", "server.log"), maxBytes = 1_000_000)

    fun write(line: String) {
        System.err.println(line)
        file.write(line)
    }
}
