package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.AppWindow
import com.saubh.deskbuddy.protocol.BrowserTab
import com.saubh.deskbuddy.protocol.RunningApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Pure parsing of `mac-windows.js list` output and grouping into apps, so both are testable off macOS. */
object MacWindowList {

    /** `[{"pid":1,"bundleId":"…","name":"…","windows":[{"index":0,"title":"…","browserWindow":0,"tabs":["…"]}]}]`. */
    fun parse(json: String): List<MacWindow> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { Json.parseToJsonElement(json.trim()) }.getOrNull() as? JsonArray ?: return emptyList()
        return root.filterIsInstance<JsonObject>().flatMap { app ->
            val pid = app.int("pid") ?: return@flatMap emptyList()
            val bundleId = app.string("bundleId").orEmpty()
            val name = app.string("name").orEmpty()
            (app["windows"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { window ->
                val index = window.int("index") ?: return@mapNotNull null
                val tabs = (window["tabs"] as? JsonArray).orEmpty()
                    .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    .mapIndexed { i, title -> BrowserTab(i, title) }
                MacWindow(pid, bundleId, name, index, window.string("title").orEmpty(), window.int("browserWindow"), tabs)
            }
        }
    }

    /** Apps keep the script's order (frontmost first); a blank window title shows the app name. */
    fun group(windows: List<MacWindow>): List<RunningApp> =
        windows.groupBy { it.appId }.map { (id, group) ->
            val name = group.first().appName.ifBlank { id }
            RunningApp(
                id = id,
                name = name,
                windows = group.map { AppWindow(it.id, it.title.ifBlank { name }, it.tabs) },
            )
        }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
}
