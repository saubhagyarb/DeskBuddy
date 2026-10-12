package com.saubh.deskbuddy.server.openapps

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Catalog id → the lower-cased exe a running window reports, so pinned `.lnk` shortcuts can be matched
 * to open windows. Start-menu (UWP) ids have no exe and stay unmatched; they launch instead.
 */
class ShortcutTargets(private val resolveLnk: (String) -> String? = ::lnkTarget) {
    private val cache = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun target(appId: String): String? = when {
        appId.endsWith(".exe", ignoreCase = true) -> appId.lowercase()
        !appId.endsWith(".lnk", ignoreCase = true) || appId in misses -> null
        else -> cache[appId] ?: resolveLnk(appId)?.takeIf { it.isNotBlank() }?.lowercase()?.also { cache[appId] = it }
            ?: null.also { misses += appId }
    }

    private companion object {
        /** The path travels in an environment variable, never in the script text. */
        fun lnkTarget(path: String): String? = runCatching {
            val process = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(New-Object -ComObject WScript.Shell).CreateShortcut(\$env:KONNEKT_LNK).TargetPath",
            ).apply { environment()["KONNEKT_LNK"] = path }.redirectErrorStream(true).start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            process.inputStream.bufferedReader().readText().trim().takeIf { it.endsWith(".exe", ignoreCase = true) }
        }.getOrNull()
    }
}
