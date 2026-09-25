package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.streams.asSequence

/**
 * Lists every app on the Start menu — classic programs and Store apps such as Settings or
 * WhatsApp, which have no .lnk file — via the shell's AppsFolder (`Get-StartApps`). Falls back
 * to scanning Start Menu shortcuts if PowerShell is unavailable. Store-style ids are
 * `shell:AppsFolder\<AppID>`; both kinds launch through `cmd /c start`.
 */
class WindowsAppActuator(
    private val roots: List<Path> = defaultRoots(),
    private val startApps: () -> List<DesktopApp>? = StartApps::query,
    private val launcher: (String) -> Unit = ::startViaCmd,
) : AppActuator {

    override fun installedApps(): List<DesktopApp> {
        val byName = LinkedHashMap<String, DesktopApp>()
        val listed = runCatching { startApps() }.getOrNull()
        (listed ?: shortcutFiles()).filterNot { isNoise(it.name) }.forEach { byName.putIfAbsent(it.name.lowercase(), it) }
        return byName.values.sortedBy { it.name.lowercase() }
    }

    override fun launch(app: DesktopApp) = launcher(app.id)

    /** Start Menu .lnk files, user roots first so they win on duplicate names. */
    private fun shortcutFiles(): List<DesktopApp> = roots.filter { Files.isDirectory(it) }.flatMap { root ->
        Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { Files.isRegularFile(it) && it.extension.equals("lnk", ignoreCase = true) }
                .map { DesktopApp(id = it.toAbsolutePath().toString(), name = it.nameWithoutExtension) }
                .toList()
        }
    }

    private fun isNoise(name: String) = name.startsWith("Uninstall", ignoreCase = true)

    companion object {
        fun defaultRoots(): List<Path> = listOfNotNull(
            System.getenv("APPDATA")?.let { Path.of(it, "Microsoft", "Windows", "Start Menu", "Programs") },
            System.getenv("ProgramData")?.let { Path.of(it, "Microsoft", "Windows", "Start Menu", "Programs") },
        )

        fun startViaCmd(target: String) {
            val builder = ProcessBuilder("cmd", "/c", "start", "", target).redirectErrorStream(true)
            if (!StartApps.isAppsFolderId(target)) {
                runCatching { Path.of(target).parent?.toFile() }.getOrNull()?.let { builder.directory(it) }
            }
            builder.start()
        }
    }
}
