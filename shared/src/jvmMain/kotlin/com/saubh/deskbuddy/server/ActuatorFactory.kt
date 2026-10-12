package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.server.audio.MacAudioActuator
import com.saubh.deskbuddy.server.audio.WindowsAudioActuator
import com.saubh.deskbuddy.server.keyboard.KeyboardActuator
import com.saubh.deskbuddy.server.keyboard.MacKeyboardActuator
import com.saubh.deskbuddy.server.keyboard.UnsupportedKeyboardActuator
import com.saubh.deskbuddy.server.keyboard.WindowsKeyboardActuator
import com.saubh.deskbuddy.server.keyboard.XdotoolKeyboardActuator
import com.saubh.deskbuddy.server.media.FallbackMediaSessionActuator
import com.saubh.deskbuddy.server.media.MacMediaRemoteSessionActuator
import com.saubh.deskbuddy.server.media.MacMediaSessionActuator
import com.saubh.deskbuddy.server.media.MediaRemoteAdapter
import com.saubh.deskbuddy.server.media.WindowsMediaSessionActuator
import com.saubh.deskbuddy.server.openapps.MacWindowActuator
import com.saubh.deskbuddy.server.openapps.UnsupportedWindowActuator
import com.saubh.deskbuddy.server.openapps.WindowActuator
import com.saubh.deskbuddy.server.openapps.WindowsWindowActuator
import java.nio.file.Path

object ActuatorFactory {

    /** Located once; null on Windows/Linux and when the app was built without the adapter. */
    private val macAdapter: MediaRemoteAdapter? by lazy { MediaRemoteAdapter.locate() }

    fun create(
        osName: String = HostOs.name,
        onTextShared: (String) -> Unit = {},
        onFileReceived: (Path) -> Unit = {},
        iconFor: (DesktopApp) -> String? = AppIconProvider()::iconPng,
    ): Actuators {
        // One audio actuator: on macOS the media volume keys are driven through it.
        val audio = audio(osName)
        return Actuators(
            media = media(osName, audio),
            share = AwtShareActuator(onTextShared),
            apps = AppCatalogService(apps(osName), ShortcutStore(), iconFor),
            files = FileReceiver(onFileReceived = onFileReceived),
            mediaSession = mediaSession(osName),
            audio = audio,
            power = power(osName),
            keyboard = keyboard(osName),
            windows = windows(osName),
        )
    }

    fun keyboard(osName: String): KeyboardActuator = when {
        HostOs.isWindows(osName) -> WindowsKeyboardActuator()
        HostOs.isMac(osName) -> MacKeyboardActuator()
        HostOs.isLinux(osName) && XdotoolKeyboardActuator.isAvailable() -> XdotoolKeyboardActuator()
        else -> UnsupportedKeyboardActuator(osName)
    }

    fun windows(osName: String): WindowActuator = when {
        HostOs.isWindows(osName) -> WindowsWindowActuator()
        HostOs.isMac(osName) -> MacWindowActuator()
        else -> UnsupportedWindowActuator(osName)
    }

    fun power(osName: String): PowerActuator = when {
        HostOs.isWindows(osName) -> WindowsPowerActuator()
        HostOs.isMac(osName) -> MacPowerActuator()
        else -> UnsupportedPowerActuator(osName)
    }

    fun media(osName: String, audio: AudioActuator = audio(osName)): MediaActuator = when {
        HostOs.isWindows(osName) -> WindowsMediaActuator()
        HostOs.isMac(osName) -> (audio as? MacAudioActuator ?: MacAudioActuator()).let { a ->
            MacMediaActuator(a, a::setMuted, adapterSend = { id -> macAdapter?.send(id) == true })
        }
        else -> UnsupportedMediaActuator(osName)
    }

    fun mediaSession(osName: String): MediaSessionActuator = when {
        HostOs.isWindows(osName) -> WindowsMediaSessionActuator()
        HostOs.isMac(osName) -> FallbackMediaSessionActuator(
            choosePrimary = { macAdapter?.takeIf { it.isWorking() }?.let { MacMediaRemoteSessionActuator(it) } },
            fallback = { MacMediaSessionActuator() },
        )
        else -> UnsupportedMediaSessionActuator()
    }

    fun audio(osName: String): AudioActuator = when {
        HostOs.isWindows(osName) -> WindowsAudioActuator()
        HostOs.isMac(osName) -> MacAudioActuator()
        else -> UnsupportedAudioActuator(osName)
    }

    fun apps(osName: String): AppActuator = when {
        HostOs.isWindows(osName) -> WindowsAppActuator()
        HostOs.isMac(osName) -> MacAppActuator()
        HostOs.isLinux(osName) -> LinuxAppActuator()
        else -> UnsupportedAppActuator(osName)
    }
}
