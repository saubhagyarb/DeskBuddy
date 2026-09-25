package com.saubh.deskbuddy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

private const val APP_ICON_RESOURCE = "deskbuddy-icon.png"

/** The DeskBuddy app icon (256 px), used for the window title bar / taskbar and the tray. */
@Composable
fun rememberAppIconPainter(): Painter = remember {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(APP_ICON_RESOURCE)
        ?.use { it.readBytes() }
        ?: error("Missing resource $APP_ICON_RESOURCE")
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
