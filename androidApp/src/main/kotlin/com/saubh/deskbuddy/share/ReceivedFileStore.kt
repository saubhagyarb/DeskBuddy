package com.saubh.deskbuddy.share

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.saubh.deskbuddy.protocol.Protocol
import com.saubh.deskbuddy.transfer.FileNames
import java.io.File
import java.io.OutputStream

/**
 * Where incoming files land on the phone. API 29+ uses MediaStore Downloads
 * (visible in the Files app under Download/DeskBuddy); API 24–28 uses the
 * app's external Downloads directory, which needs no runtime permission.
 */
class ReceivedFileStore(private val context: Context) {

    /** An open destination. Call exactly one of [finish] or [abort]. */
    class Target(
        val stream: OutputStream,
        val location: String,
        private val onFinish: () -> Unit,
        private val onAbort: () -> Unit,
    ) {
        fun finish() {
            runCatching { stream.close() }
            onFinish()
        }

        fun abort() {
            runCatching { stream.close() }
            onAbort()
        }
    }

    fun open(name: String, mimeType: String): Target {
        val safeName = FileNames.sanitize(name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openMediaStore(safeName, mimeType) else openLegacy(safeName)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openMediaStore(name: String, mimeType: String): Target {
        val resolver = context.contentResolver
        val relativePath = Environment.DIRECTORY_DOWNLOADS + File.separator + Protocol.RECEIVED_FOLDER_NAME
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        val stream = resolver.openOutputStream(uri) ?: throw IllegalStateException("Cannot open $uri")
        return Target(
            stream = stream,
            location = "$relativePath/$name",
            onFinish = {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            },
            onAbort = { resolver.delete(uri, null, null) },
        )
    }

    private fun openLegacy(name: String): Target {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), Protocol.RECEIVED_FOLDER_NAME)
        dir.mkdirs()
        val file = uniqueFile(dir, name)
        return Target(
            stream = file.outputStream(),
            location = file.absolutePath,
            onFinish = {},
            onAbort = { file.delete() },
        )
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val (base, ext) = FileNames.splitExtension(name)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$ext")
            n++
        }
        return candidate
    }
}
