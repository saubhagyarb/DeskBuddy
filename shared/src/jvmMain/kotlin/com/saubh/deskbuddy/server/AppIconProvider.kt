package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.filechooser.FileSystemView

/** Shell icon of a launchable file or Start app as base64 PNG, cached per id. Null when unavailable. */
class AppIconProvider(private val sizePx: Int = 64) {
    private val cache = ConcurrentHashMap<String, Optional<String>>()

    fun iconPng(app: DesktopApp): String? =
        cache.computeIfAbsent(app.id) { Optional.ofNullable(render(it)) }.orElse(null)

    private fun render(id: String): String? = runCatching {
        val image = (if (StartApps.isAppsFolderId(id)) ShellItemImage.render(id, sizePx) else fileIcon(id)) ?: return null
        ByteArrayOutputStream().use { out ->
            ImageIO.write(image, "png", out)
            Base64.getEncoder().encodeToString(out.toByteArray())
        }
    }.getOrNull()

    private fun fileIcon(path: String): BufferedImage? {
        val file = File(path)
        if (!file.exists()) return null
        val icon = FileSystemView.getFileSystemView().getSystemIcon(file, sizePx, sizePx) ?: return null
        return BufferedImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB).also { image ->
            image.createGraphics().also { g ->
                icon.paintIcon(null, g, 0, 0)
                g.dispose()
            }
        }
    }
}
