package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

/**
 * Enumerates Start Menu shortcuts (user roots first so they win on duplicate names)
 * and launches anything via `cmd /c start`, which resolves .lnk/.exe/document associations.
 */
class WindowsAppActuator(
    private val roots: List<Path> = defaultRoots(),
    private val launcher: (Path) -> Unit = ::startViaCmd,
) : AppActuator {

    override fun installedApps(): List<DesktopApp> {
        val byName = LinkedHashMap<String, DesktopApp>()
        for (root in roots) {
            if (!Files.isDirectory(root)) continue
            Files.walk(root).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) && it.extension.equals("lnk", ignoreCase = true) }
                    .map { DesktopApp(id = it.toAbsolutePath().toString(), name = it.nameWithoutExtension) }
                    .filterNot { it.name.startsWith("Uninstall", ignoreCase = true) }
                    .forEach { byName.putIfAbsent(it.name.lowercase(), it) }
            }
        }
        return byName.values.sortedBy { it.name.lowercase() }
    }

    override fun launch(app: DesktopApp) = launcher(Path.of(app.id))

    companion object {
        fun defaultRoots(): List<Path> = listOfNotNull(
            System.getenv("APPDATA")?.let { Path.of(it, "Microsoft", "Windows", "Start Menu", "Programs") },
            System.getenv("ProgramData")?.let { Path.of(it, "Microsoft", "Windows", "Start Menu", "Programs") },
        )

        fun startViaCmd(path: Path) {
            ProcessBuilder("cmd", "/c", "start", "", path.toString())
                .directory(path.parent?.toFile())
                .redirectErrorStream(true)
                .start()
        }
    }
}
