package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Lists `.app` bundles in the standard Applications folders (two levels deep, never inside a
 * bundle) and launches through `open`, which also handles any file the user browsed to.
 */
class MacAppActuator(
    private val roots: List<Path> = defaultRoots(),
    private val launcher: (String) -> Unit = ::open,
) : AppActuator {

    private companion object {
        const val BUNDLE_SUFFIX = ".app"
        const val MAX_DEPTH = 2

        fun defaultRoots(): List<Path> = listOf(
            Path.of("/Applications"),
            Path.of("/Applications/Utilities"),
            Path.of("/System/Applications"),
            Path.of("/System/Applications/Utilities"),
            Path.of(System.getProperty("user.home"), "Applications"),
        )

        fun open(target: String) {
            ProcessBuilder("/usr/bin/open", target).redirectErrorStream(true).start()
        }
    }

    override fun installedApps(): List<DesktopApp> {
        val byName = LinkedHashMap<String, DesktopApp>()
        roots.filter { it.isDirectory() }.forEach { root ->
            bundles(root, MAX_DEPTH).forEach { byName.putIfAbsent(it.name.lowercase(), it) }
        }
        return byName.values.sortedBy { it.name.lowercase() }
    }

    override fun launch(app: DesktopApp) = launcher(app.id)

    private fun bundles(dir: Path, depth: Int): List<DesktopApp> {
        val children = runCatching { Files.newDirectoryStream(dir).use { it.toList() } }.getOrDefault(emptyList())
        return children.sortedBy { it.name.lowercase() }.flatMap { child ->
            when {
                !child.isDirectory() -> emptyList()
                child.name.endsWith(BUNDLE_SUFFIX) ->
                    listOf(DesktopApp(id = child.toAbsolutePath().toString(), name = child.name.removeSuffix(BUNDLE_SUFFIX)))
                depth > 1 -> bundles(child, depth - 1)
                else -> emptyList()
            }
        }
    }
}
