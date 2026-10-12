package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import com.saubh.deskbuddy.server.keyboard.KeyboardActuator
import com.saubh.deskbuddy.server.openapps.UnsupportedWindowActuator
import com.saubh.deskbuddy.server.openapps.WindowActuator
import java.nio.file.Files

class RecordingPowerActuator : PowerActuator {
    val calls = mutableListOf<String>()
    override fun shutdown() { calls += "shutdown" }
    override fun restart() { calls += "restart" }
    override fun lock() { calls += "lock" }
}

private class NoopMediaActuator : MediaActuator {
    override fun playPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun volumeUp() = Unit
    override fun volumeDown() = Unit
    override fun muteToggle() = Unit
}

private class NoopShareActuator : ShareActuator {
    override val lastWrittenText: String? = null
    override fun setClipboard(text: String) = Unit
    override fun getClipboard(): String? = null
    override fun onTextShared(text: String) = Unit
}

class RecordingAppActuator(private val installed: List<DesktopApp> = emptyList()) : AppActuator {
    val launched = mutableListOf<String>()
    override fun installedApps(): List<DesktopApp> = installed
    override fun launch(app: DesktopApp) { launched += app.id }
}

private class NoopAppActuator : AppActuator {
    override fun installedApps(): List<DesktopApp> = emptyList()
    override fun launch(app: DesktopApp) = Unit
}

/** Actuators that touch nothing on the machine, with temp-dir stores. */
class RecordingKeyboard(private val unsupported: Boolean = false) : KeyboardActuator {
    val typed = mutableListOf<String>()
    val pressed = mutableListOf<Pair<Key, Set<KeyModifier>>>()
    override fun type(text: String) {
        if (unsupported) throw UnsupportedOsException("test")
        typed += text
    }
    override fun press(key: Key, modifiers: Set<KeyModifier>) {
        if (unsupported) throw UnsupportedOsException("test")
        pressed += key to modifiers
    }
}

fun testActuators(
    power: PowerActuator = RecordingPowerActuator(),
    keyboard: KeyboardActuator = RecordingKeyboard(),
    windows: WindowActuator = UnsupportedWindowActuator("test"),
    apps: AppActuator = NoopAppActuator(),
): Actuators {
    val dir = Files.createTempDirectory("deskbuddy-test")
    return Actuators(
        media = NoopMediaActuator(),
        share = NoopShareActuator(),
        apps = AppCatalogService(apps, ShortcutStore(dir.resolve("shortcuts.json"))),
        files = FileReceiver(directory = dir.resolve("received")),
        power = power,
        keyboard = keyboard,
        windows = windows,
    )
}

fun tempDeviceStore(): PairedDeviceStore = PairedDeviceStore(Files.createTempDirectory("deskbuddy-paired").resolve("paired.json"))

class RecordingListener : SessionListener {
    val authenticated = mutableListOf<String>()
    override fun onPairingStarted(pin: String) = Unit
    override fun onPairingEnded() = Unit
    override fun onAuthenticated(deviceName: String, token: String) {
        authenticated += deviceName
    }
}
