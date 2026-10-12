package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.server.keyboard.KeyboardActuator
import com.saubh.deskbuddy.server.keyboard.UnsupportedKeyboardActuator
import com.saubh.deskbuddy.server.openapps.UnsupportedWindowActuator
import com.saubh.deskbuddy.server.openapps.WindowActuator

/** Everything a [ControlSession] can drive on the desktop, grouped by concern. */
class Actuators(
    val media: MediaActuator,
    val share: ShareActuator,
    val apps: AppCatalogService,
    val files: FileReceiver,
    val mediaSession: MediaSessionActuator = UnsupportedMediaSessionActuator(),
    val audio: AudioActuator = UnsupportedAudioActuator("unknown"),
    val power: PowerActuator = UnsupportedPowerActuator("unknown"),
    val keyboard: KeyboardActuator = UnsupportedKeyboardActuator("unknown"),
    val windows: WindowActuator = UnsupportedWindowActuator("unknown"),
) {
    /** Invoked on [com.saubh.deskbuddy.protocol.MediaStateRequestCommand]; the desktop app re-pushes its media state. */
    var onStateRequested: () -> Unit = {}

    /** Invoked after the phone changed volume or the default output device, so the fresh state is pushed at once. */
    var onAudioChanged: () -> Unit = {}

    /** Invoked on [com.saubh.deskbuddy.protocol.RunningAppsRequestCommand]; listing is slow, so the desktop app pushes it when ready. */
    var onRunningAppsRequested: () -> Unit = {}
}
