package com.saubh.deskbuddy.server

/** Everything a [ControlSession] can drive on the desktop, grouped by concern. */
class Actuators(
    val media: MediaActuator,
    val share: ShareActuator,
    val apps: AppCatalogService,
    val files: FileReceiver,
    val mediaSession: MediaSessionActuator = UnsupportedMediaSessionActuator(),
    val audio: AudioActuator = UnsupportedAudioActuator("unknown"),
) {
    /** Invoked on [com.saubh.deskbuddy.protocol.MediaStateRequestCommand]; the desktop app re-pushes its media state. */
    var onStateRequested: () -> Unit = {}

    /** Invoked after the phone changed volume or the default output device, so the fresh state is pushed at once. */
    var onAudioChanged: () -> Unit = {}
}
