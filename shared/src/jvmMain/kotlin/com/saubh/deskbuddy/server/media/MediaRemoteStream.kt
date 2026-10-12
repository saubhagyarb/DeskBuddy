package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Folds `mediaremote-adapter stream --micros` lines into samples. Diff lines merge onto the last full
 * payload (a null value removes the key), so one instance must see every line of one process, in order.
 */
class MediaRemoteStream(private val clockMs: () -> Long = System::currentTimeMillis) {
    private var payload: Map<String, JsonElement> = emptyMap()

    fun accept(line: String): MediaRemoteSample? {
        val obj = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
        if (obj.string("type") != "data") return null
        val incoming = obj["payload"] as? JsonObject
        val diff = obj["diff"]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } ?: false
        payload = when {
            incoming == null -> emptyMap()
            diff -> (payload + incoming).filterValues { it !is JsonNull }
            else -> incoming.filterValues { it !is JsonNull }
        }
        return sample()
    }

    private fun sample(): MediaRemoteSample {
        val title = payload.string("title").orEmpty()
        if (title.isBlank()) return MediaRemoteSample(NowPlayingCommand.NONE, "", null, null)
        val bundle = payload.string("bundleIdentifier").orEmpty()
        val artist = payload.string("artist").orEmpty()
        val album = payload.string("album").orEmpty()
        val playing = payload["playing"]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } == true
        val durationMs = (payload.long("durationMicros") ?: 0L) / 1_000
        val elapsedMs = (payload.long("elapsedTimeMicros") ?: 0L) / 1_000
        val stampMs = (payload.long("timestampEpochMicros") ?: 0L) / 1_000
        val drift = if (playing && stampMs > 0) (clockMs() - stampMs).coerceAtLeast(0) else 0L
        val position = (elapsedMs + drift).let { if (durationMs > 0) it.coerceAtMost(durationMs) else it }
        val artwork = payload.string("artworkData")?.takeIf { it.length / 4 * 3 <= MacArtworkFetcher.MAX_BYTES }
        return MediaRemoteSample(
            state = NowPlayingCommand(
                title = title,
                artist = artist.ifBlank { null },
                album = album.ifBlank { null },
                appName = bundle.takeIf { it.isNotBlank() }?.let(MediaRemoteApps::displayName),
                status = if (playing) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
                positionMs = position,
                durationMs = durationMs,
                canSeek = durationMs > 0,
            ),
            trackKey = "$bundle|$title|$artist|$album",
            artworkMimeType = artwork?.let { payload.string("artworkMimeType") },
            artworkBase64 = artwork?.takeIf { payload.string("artworkMimeType") != null },
        )
    }

    private fun Map<String, JsonElement>.string(key: String): String? =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }

    private fun Map<String, JsonElement>.long(key: String): Long? =
        this[key]?.let { runCatching { it.jsonPrimitive.longOrNull }.getOrNull() }
}
