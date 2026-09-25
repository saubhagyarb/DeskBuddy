package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.AppCatalog
import com.saubh.deskbuddy.protocol.DesktopApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

/**
 * Merges installed apps with user-chosen shortcuts and is the single authority on
 * which ids may be launched: only ids present in the catalog are accepted.
 */
class AppCatalogService(
    private val apps: AppActuator,
    private val store: ShortcutStore,
    /** Base64 PNG for a shortcut app, or null when none can be produced. Only asked for shortcuts. */
    private val iconFor: (DesktopApp) -> String? = { null },
) {
    private val _catalog = MutableStateFlow(AppCatalog(emptyList(), emptyList()))
    val catalog: StateFlow<AppCatalog> = _catalog

    private var installed: List<DesktopApp>? = null

    /** Re-scans installed apps and returns the fresh catalog. */
    @Synchronized
    fun refresh(): AppCatalog {
        installed = runCatching { apps.installedApps() }.getOrDefault(emptyList())
        return rebuild()
    }

    /** Current catalog, scanning installed apps on first use. */
    @Synchronized
    fun current(): AppCatalog = if (installed == null) refresh() else _catalog.value

    /** Returns false when [id] is not a known app (nothing is launched). */
    @Synchronized
    fun launch(id: String): Boolean {
        val app = find(id) ?: return false
        apps.launch(app)
        return true
    }

    @Synchronized
    fun addShortcut(id: String): Boolean {
        val app = find(id) ?: return false
        store.add(app)
        rebuild()
        return true
    }

    /** Adds a program the user browsed to on disk (any launchable file). */
    @Synchronized
    fun addCustom(path: Path): DesktopApp {
        val app = DesktopApp(id = path.toAbsolutePath().toString(), name = path.nameWithoutExtension)
        store.add(app)
        rebuild()
        return app
    }

    @Synchronized
    fun removeShortcut(id: String) {
        store.remove(id)
        rebuild()
    }

    private fun find(id: String): DesktopApp? {
        current()
        return store.load().firstOrNull { it.id == id } ?: installed?.firstOrNull { it.id == id }
    }

    private fun rebuild(): AppCatalog {
        val shortcuts = store.load()
        val known = installed.orEmpty()
        val extra = shortcuts.filter { s -> known.none { it.id == s.id } }
        // A shortcut saved under an older id (e.g. a .lnk path) hides the same-named listed app, so it is not shown twice.
        val listed = known.filterNot { app -> extra.any { it.name.equals(app.name, ignoreCase = true) } }
        val all = (listed + extra).sortedBy { it.name.lowercase() }
        val icons = shortcuts.mapNotNull { app -> runCatching { iconFor(app) }.getOrNull()?.let { app.id to it } }.toMap()
        return AppCatalog(all, shortcuts.map { it.id }, icons).also { _catalog.value = it }
    }
}
