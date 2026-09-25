package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.protocol.DesktopApp
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

private class NoopAppActuator : AppActuator {
    override fun installedApps(): List<DesktopApp> = emptyList()
    override fun launch(app: DesktopApp) = Unit
}

/** Actuators that touch nothing on the machine, with temp-dir stores. */
fun testActuators(power: PowerActuator = RecordingPowerActuator()): Actuators {
    val dir = Files.createTempDirectory("deskbuddy-test")
    return Actuators(
        media = NoopMediaActuator(),
        share = NoopShareActuator(),
        apps = AppCatalogService(NoopAppActuator(), ShortcutStore(dir.resolve("shortcuts.json"))),
        files = FileReceiver(directory = dir.resolve("received")),
        power = power,
    )
}

fun tempDeviceStore(): PairedDeviceStore = PairedDeviceStore(Files.createTempDirectory("deskbuddy-paired").resolve("paired.json"))
