package com.saubh.deskbuddy.server

import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * A `.app` bundle's icon: `CFBundleIconFile` from its Info.plist (read with `plutil`), converted
 * from `.icns` to PNG by `sips`. Null when the bundle has no `.icns` (e.g. asset-catalog-only icons).
 */
object MacAppIcon {
    private const val TIMEOUT_SECONDS = 5L
    private const val DEFAULT_ICON = "AppIcon.icns"

    fun isBundle(id: String) = id.endsWith(".app") && Files.isDirectory(Path.of(id))

    fun render(bundle: Path, sizePx: Int): BufferedImage? {
        val icns = iconFile(bundle) ?: return null
        val png = Files.createTempFile("konnekt-icon-", ".png")
        return try {
            val ok = run(listOf("/usr/bin/sips", "-s", "format", "png", "-z", "$sizePx", "$sizePx", icns.toString(), "--out", png.toString())) != null
            if (ok && Files.size(png) > 0) ImageIO.read(png.toFile()) else null
        } finally {
            Files.deleteIfExists(png)
        }
    }

    private fun iconFile(bundle: Path): Path? {
        val resources = bundle.resolve("Contents").resolve("Resources")
        val plist = bundle.resolve("Contents").resolve("Info.plist")
        val declared = if (Files.isRegularFile(plist)) {
            run(listOf("/usr/bin/plutil", "-extract", "CFBundleIconFile", "raw", "-o", "-", plist.toString()))
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.endsWith(".icns")) it else "$it.icns" }
        } else {
            null
        }
        return listOfNotNull(declared, DEFAULT_ICON).map(resources::resolve).firstOrNull(Files::isRegularFile)
    }

    /** Trimmed stdout, or null on a non-zero exit or timeout. */
    private fun run(command: List<String>): String? {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        return if (process.exitValue() == 0) output.trim() else null
    }
}
