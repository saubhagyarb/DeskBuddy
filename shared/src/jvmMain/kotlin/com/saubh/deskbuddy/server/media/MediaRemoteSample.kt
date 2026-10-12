package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.protocol.NowPlayingCommand

/** One merged adapter payload. [state] carries no artwork id; the actuator assigns it once the cover is published. */
data class MediaRemoteSample(
    val state: NowPlayingCommand,
    /** "bundle|title|artist|album", or empty when nothing is playing. */
    val trackKey: String,
    val artworkMimeType: String?,
    val artworkBase64: String?,
)
