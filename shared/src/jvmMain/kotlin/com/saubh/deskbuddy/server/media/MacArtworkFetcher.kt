package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.server.Osascript
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.Base64

/** Cover art for the Mac now-playing helper: Spotify's HTTPS URL, or Music's embedded artwork via AppleScript. */
class MacArtworkFetcher(
    private val runAppleScript: (String, List<String>) -> String? = { script, args -> Osascript.run(script, args) },
    private val download: (String) -> ByteArray? = ::httpsGet,
) {
    /** (mimeType, base64) or null when the track has no artwork or it cannot be read. */
    fun fetch(app: String, artworkUrl: String): Pair<String, String>? {
        val bytes = when {
            artworkUrl.isNotBlank() -> download(artworkUrl)
            app == "Music" -> musicArtwork()
            else -> null
        } ?: return null
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return null
        val mime = mimeType(bytes) ?: return null
        return mime to Base64.getEncoder().encodeToString(bytes)
    }

    private fun musicArtwork(): ByteArray? {
        val file = Files.createTempFile("konnekt-artwork-", ".bin")
        return try {
            if (runAppleScript(MUSIC_ARTWORK_SCRIPT, listOf(file.toString())) != "ok") return null
            Files.readAllBytes(file).takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            Files.deleteIfExists(file)
        }
    }

    companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val TIMEOUT_MS = 3_000

        /** The file is written outside the `tell` block, where Standard Additions behave reliably. */
        private const val MUSIC_ARTWORK_SCRIPT = """
on run argv
    tell application "Music"
        if (count of artworks of current track) is 0 then return "none"
        set artData to raw data of artwork 1 of current track
    end tell
    set f to open for access (POSIX file (item 1 of argv)) with write permission
    try
        set eof f to 0
        write artData to f
    end try
    close access f
    return "ok"
end run
"""

        /** Sniffs the image type from magic bytes rather than trusting what the player reports. */
        fun mimeType(bytes: ByteArray): String? = when {
            bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
            bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte() -> "image/png"
            else -> null
        }

        /** Only HTTPS, with timeouts and a size cap, so a bad URL cannot stall or bloat the helper. */
        fun httpsGet(url: String): ByteArray? = runCatching {
            val uri = URI(url)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            val connection = uri.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > MAX_BYTES) return null
                    }
                    out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
