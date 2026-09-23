package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One JSON line from the PowerShell helper's stdout. */
@Serializable
sealed class HelperEvent

@Serializable
@SerialName("state")
data class HelperState(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val app: String? = null,
    val status: String = "NONE",
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val canSeek: Boolean = false,
    val artworkId: String? = null,
) : HelperEvent() {
    fun toNowPlaying(): NowPlayingCommand = NowPlayingCommand(
        title = title?.takeIf { it.isNotBlank() },
        artist = artist?.takeIf { it.isNotBlank() },
        album = album?.takeIf { it.isNotBlank() },
        appName = app?.takeIf { it.isNotBlank() },
        status = when (status) {
            "Playing" -> PlaybackStatus.PLAYING
            "Paused" -> PlaybackStatus.PAUSED
            "NONE" -> PlaybackStatus.NONE
            else -> PlaybackStatus.STOPPED
        },
        positionMs = positionMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0),
        canSeek = canSeek,
        artworkId = artworkId?.takeIf { it.isNotBlank() },
    )
}

@Serializable
@SerialName("artwork")
data class HelperArtwork(val artworkId: String, val mimeType: String, val data: String) : HelperEvent()

@Serializable
@SerialName("result")
data class HelperResult(val ok: Boolean) : HelperEvent()

object MediaHelperProtocol {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    /** Null for blank lines, malformed JSON, or unknown event types. */
    fun parse(line: String): HelperEvent? =
        if (line.isBlank()) null else runCatching { json.decodeFromString(HelperEvent.serializer(), line) }.getOrNull()
}
