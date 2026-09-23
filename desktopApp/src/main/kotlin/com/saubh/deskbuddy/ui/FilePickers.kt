package com.saubh.deskbuddy.ui

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path

/** Native file chooser; returns null when the user cancels. Call from the UI thread. */
fun pickFile(title: String): Path? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return Path.of(directory, file)
}
