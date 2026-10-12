package com.saubh.deskbuddy.server.openapps

import com.saubh.deskbuddy.protocol.BrowserTab
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Browser tab strips through UI Automation, in a PowerShell helper. The walk never enters
 * `Document` elements, so tab widgets inside web pages are not mistaken for browser tabs.
 * Inputs travel in environment variables, never in the script text.
 */
object BrowserTabs {
    private const val LIST_TIMEOUT_SECONDS = 12L

    private const val FIND_TABS = """
Add-Type -AssemblyName UIAutomationClient, UIAutomationTypes
${'$'}A = [System.Windows.Automation.AutomationElement]
${'$'}CT = [System.Windows.Automation.ControlType]
${'$'}walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
function Find-Tabs(${'$'}el, ${'$'}depth, ${'$'}acc) {
  if (${'$'}depth -gt 14) { return }
  ${'$'}c = ${'$'}walker.GetFirstChild(${'$'}el)
  while (${'$'}c -ne ${'$'}null) {
    ${'$'}t = ${'$'}c.Current.ControlType
    if (${'$'}t -eq ${'$'}CT::TabItem) { [void]${'$'}acc.Add(${'$'}c) }
    elseif (${'$'}t -ne ${'$'}CT::Document) { Find-Tabs ${'$'}c (${'$'}depth + 1) ${'$'}acc }
    ${'$'}c = ${'$'}walker.GetNextSibling(${'$'}c)
  }
}
function Get-Tabs(${'$'}h) {
  ${'$'}acc = New-Object System.Collections.ArrayList
  Find-Tabs (${'$'}A::FromHandle([IntPtr][long]${'$'}h)) 0 ${'$'}acc
  return ,${'$'}acc
}
"""

    private const val LIST_SCRIPT = "[Console]::OutputEncoding=[Text.Encoding]::UTF8\n" + FIND_TABS + """
${'$'}out = New-Object System.Collections.ArrayList
foreach (${'$'}h in ${'$'}env:KONNEKT_WINDOWS.Split(',')) {
  try {
    ${'$'}names = @(Get-Tabs ${'$'}h | ForEach-Object { ${'$'}_.Current.Name })
    [void]${'$'}out.Add([pscustomobject]@{ h = ${'$'}h; t = ${'$'}names })
  } catch {}
}
ConvertTo-Json -InputObject @(${'$'}out) -Compress -Depth 3
"""

    private const val CLOSE_SCRIPT = FIND_TABS + """
${'$'}tabs = Get-Tabs ${'$'}env:KONNEKT_WINDOW
${'$'}i = [int]${'$'}env:KONNEKT_INDEX
${'$'}title = ${'$'}env:KONNEKT_TITLE
${'$'}target = ${'$'}null
if (${'$'}i -lt ${'$'}tabs.Count -and ${'$'}tabs[${'$'}i].Current.Name -eq ${'$'}title) { ${'$'}target = ${'$'}tabs[${'$'}i] }
else { ${'$'}target = ${'$'}tabs | Where-Object { ${'$'}_.Current.Name -eq ${'$'}title } | Select-Object -First 1 }
if (${'$'}target -eq ${'$'}null) { exit 2 }
${'$'}cond = New-Object System.Windows.Automation.PropertyCondition(${'$'}A::ControlTypeProperty, ${'$'}CT::Button)
${'$'}close = ${'$'}target.FindAll([System.Windows.Automation.TreeScope]::Descendants, ${'$'}cond) |
  Where-Object { ${'$'}_.Current.Name -match 'close' } | Select-Object -First 1
if (${'$'}close -ne ${'$'}null) {
  ${'$'}close.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
  exit 0
}
${'$'}sel = ${'$'}null
if (${'$'}target.TryGetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern, [ref]${'$'}sel)) { ${'$'}sel.Select() }
${'$'}shell = New-Object -ComObject WScript.Shell
[void]${'$'}shell.AppActivate([int]${'$'}target.Current.ProcessId)
Start-Sleep -Milliseconds 200
${'$'}shell.SendKeys('^w')
"""

    /** Tab titles per window handle; windows whose tabs could not be read are absent. */
    fun list(handles: List<Long>): Map<Long, List<BrowserTab>> {
        if (handles.isEmpty()) return emptyMap()
        val process = powershell(LIST_SCRIPT, mapOf("KONNEKT_WINDOWS" to handles.joinToString(","))).start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (!process.waitFor(LIST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return emptyMap()
        }
        return parse(output)
    }

    /** Fire-and-forget: the phone re-lists afterwards to see the result. */
    fun close(handle: Long, index: Int, title: String) {
        powershell(
            CLOSE_SCRIPT,
            mapOf("KONNEKT_WINDOW" to handle.toString(), "KONNEKT_INDEX" to index.toString(), "KONNEKT_TITLE" to title),
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    }

    /** `[{"h":"123","t":["a","b"]}]`; `t` may be a bare string when there is a single tab. */
    fun parse(json: String): Map<Long, List<BrowserTab>> {
        if (json.isBlank()) return emptyMap()
        val root = runCatching { Json.parseToJsonElement(json.trim()) }.getOrNull() ?: return emptyMap()
        val entries: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
        return entries.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val handle = obj["h"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNull null
            val titles = when (val t = obj["t"]) {
                is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> listOfNotNull(t.contentOrNull)
                else -> emptyList()
            }
            handle to titles.mapIndexed { i, title -> BrowserTab(i, title) }
        }.toMap()
    }

    /** `-EncodedCommand` (UTF-16LE, base64) sidesteps Windows command-line quoting of the multi-line script. */
    private fun powershell(script: String, env: Map<String, String>): ProcessBuilder {
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(StandardCharsets.UTF_16LE))
        return ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .also { it.environment().putAll(env) }
    }
}
