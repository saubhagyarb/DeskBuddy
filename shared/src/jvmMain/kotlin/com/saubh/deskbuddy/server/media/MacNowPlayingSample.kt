package com.saubh.deskbuddy.server.media

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

/** One line of `mac-now-playing.js`: the shared [HelperState] plus what the Kotlin side needs for artwork. */
data class MacNowPlayingSample(
    val state: HelperState,
    /** "app|title|artist|album", or empty when nothing is playing. */
    val trackKey: String,
    /** Spotify's cover URL; empty for Music, whose artwork is read through AppleScript. */
    val artworkUrl: String,
) {
    companion object {
        /** Null for blank or malformed lines and non-state events. */
        fun parse(line: String): MacNowPlayingSample? {
            val state = MediaHelperProtocol.parse(line) as? HelperState ?: return null
            val obj = runCatching { Json.parseToJsonElement(line) as? JsonObject }.getOrNull() ?: return null
            fun field(name: String) = obj[name]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
            return MacNowPlayingSample(state, field("trackKey"), field("artworkUrl"))
        }

        /** Stable id for a track's cover, so the phone can cache it across reconnects. */
        fun artworkId(trackKey: String): String =
            MessageDigest.getInstance("SHA-1").digest(trackKey.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
