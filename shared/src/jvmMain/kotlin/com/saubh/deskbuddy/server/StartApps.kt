package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/** The Windows Start apps list (the shell's AppsFolder), which includes Store apps that have no .lnk file. */
object StartApps {
    const val APPS_FOLDER_PREFIX = "shell:AppsFolder\\"
    private const val TIMEOUT_SECONDS = 30L
    private const val SCRIPT =
        "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-StartApps | Select-Object Name,AppID | ConvertTo-Json -Compress"

    fun isAppsFolderId(id: String) = id.startsWith(APPS_FOLDER_PREFIX, ignoreCase = true)

    /** Null when PowerShell or `Get-StartApps` is unavailable, so the caller can fall back. */
    fun query(): List<DesktopApp>? {
        val process = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", SCRIPT)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        return parse(output).ifEmpty { null }
    }

    /** `ConvertTo-Json` emits an object for one result and an array otherwise. */
    fun parse(json: String): List<DesktopApp> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(json.trim()) }.getOrNull() ?: return emptyList()
        val entries: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        return entries.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val name = obj["Name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val appId = obj["AppID"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isEmpty() || appId.isEmpty()) null else DesktopApp(id = APPS_FOLDER_PREFIX + appId, name = name)
        }
    }
}
