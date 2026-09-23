package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.server.audio.WindowsAudioActuator
import com.saubh.deskbuddy.server.media.WindowsMediaSessionActuator
import java.nio.file.Path

object ActuatorFactory {

    fun create(
        osName: String = System.getProperty("os.name") ?: "unknown",
        onTextShared: (String) -> Unit = {},
        onFileReceived: (Path) -> Unit = {},
        iconFor: (DesktopApp) -> String? = AppIconProvider()::iconPng,
    ): Actuators = Actuators(
        media = media(osName),
        share = AwtShareActuator(onTextShared),
        apps = AppCatalogService(apps(osName), ShortcutStore(), iconFor),
        files = FileReceiver(onFileReceived = onFileReceived),
        mediaSession = mediaSession(osName),
        audio = audio(osName),
    )

    fun media(osName: String): MediaActuator =
        if (isWindows(osName)) WindowsMediaActuator() else UnsupportedMediaActuator(osName)

    fun mediaSession(osName: String): MediaSessionActuator =
        if (isWindows(osName)) WindowsMediaSessionActuator() else UnsupportedMediaSessionActuator()

    fun audio(osName: String): AudioActuator =
        if (isWindows(osName)) WindowsAudioActuator() else UnsupportedAudioActuator(osName)

    fun apps(osName: String): AppActuator = when {
        isWindows(osName) -> WindowsAppActuator()
        osName.startsWith("Linux", ignoreCase = true) -> LinuxAppActuator()
        else -> UnsupportedAppActuator(osName)
    }

    private fun isWindows(osName: String) = osName.startsWith("Windows", ignoreCase = true)
}
