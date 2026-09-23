package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.streams.asSequence

/** Reads freedesktop `.desktop` entries and launches their `Exec` line. */
class LinuxAppActuator(
    private val roots: List<Path> = defaultRoots(),
    private val launcher: (String) -> Unit = ::runDetached,
) : AppActuator {

    override fun installedApps(): List<DesktopApp> {
        val byName = LinkedHashMap<String, DesktopApp>()
        for (root in roots) {
            if (!Files.isDirectory(root)) continue
            Files.walk(root).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) && it.extension == "desktop" }
                    .mapNotNull { parse(it) }
                    .forEach { byName.putIfAbsent(it.name.lowercase(), it) }
            }
        }
        return byName.values.sortedBy { it.name.lowercase() }
    }

    override fun launch(app: DesktopApp) {
        val exec = if (app.id.endsWith(".desktop")) execLine(Path.of(app.id)) else quote(app.id)
        launcher(exec ?: throw IllegalStateException("No Exec entry in ${app.id}"))
    }

    private fun parse(file: Path): DesktopApp? {
        val entry = readEntry(file)
        if (entry["NoDisplay"] == "true" || entry["Hidden"] == "true") return null
        if (entry["Type"] != null && entry["Type"] != "Application") return null
        val name = entry["Name"] ?: return null
        entry["Exec"] ?: return null
        return DesktopApp(id = file.toAbsolutePath().toString(), name = name)
    }

    private fun execLine(file: Path): String? = readEntry(file)["Exec"]?.let(::stripFieldCodes)

    private fun readEntry(file: Path): Map<String, String> {
        val result = HashMap<String, String>()
        var inEntry = false
        for (line in Files.readAllLines(file)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[")) {
                inEntry = trimmed == "[Desktop Entry]"
                continue
            }
            if (!inEntry || trimmed.startsWith("#") || '=' !in trimmed) continue
            val key = trimmed.substringBefore('=').trim()
            if (key !in result) result[key] = trimmed.substringAfter('=').trim()
        }
        return result
    }

    private fun quote(path: String) = "'" + path.replace("'", "'\\''") + "'"

    companion object {
        private val fieldCode = Regex("%[a-zA-Z%]")

        fun stripFieldCodes(exec: String): String =
            fieldCode.replace(exec, "").replace(Regex("\\s+"), " ").trim()

        fun defaultRoots(): List<Path> = listOf(
            Path.of(System.getProperty("user.home"), ".local", "share", "applications"),
            Path.of("/usr/local/share/applications"),
            Path.of("/usr/share/applications"),
        )

        fun runDetached(exec: String) {
            ProcessBuilder("sh", "-c", "nohup $exec >/dev/null 2>&1 &").start()
        }
    }
}
