package com.saubh.deskbuddy.server.audio

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.AudioLevels
import com.saubh.deskbuddy.server.Osascript
import com.saubh.deskbuddy.server.audio.MacCoreAudio.PropertyAddress
import kotlin.math.roundToInt

/**
 * Output devices, default-device switching and master volume through Core Audio. Devices without a
 * settable volume in the HAL (some HDMI or aggregate outputs) fall back to AppleScript's
 * `volume settings`, which drives the same default device the user sees in the menu bar.
 */
class MacAudioActuator(
    private val osascript: (String) -> String? = { Osascript.run(it) },
) : AudioActuator {

    private val audio = MacCoreAudio

    override fun devices(): List<AudioDevice> {
        val defaultId = defaultOutput()
        return outputDevices().mapNotNull { device ->
            val uid = audio.string(device, global(MacCoreAudio.DEVICE_UID)) ?: return@mapNotNull null
            val name = audio.string(device, global(MacCoreAudio.NAME)).orEmpty().ifBlank { "Audio device" }
            AudioDevice(uid, name, isDefault = device == defaultId)
        }
    }

    override fun volume(): Int = levels().volume

    override fun isMuted(): Boolean = levels().muted

    override fun levels(): AudioLevels {
        val device = defaultOutput()
        val scalar = device?.let(::readScalar)
        val mute = device?.let { audio.uint32(it, output(MacCoreAudio.MUTE)) }
        if (scalar != null) return AudioLevels(toPercent(scalar), mute?.let { it != 0 } ?: false)
        return scriptLevels() ?: throw IllegalStateException("No readable output volume")
    }

    override fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        val device = defaultOutput()
        if (device != null && writeScalar(device, clamped / 100f)) return
        osascript("set volume output volume $clamped") ?: throw IllegalStateException("Could not set output volume")
    }

    /** Toggling mute is a MediaActuator concern; this keeps the HAL and AppleScript paths in one place. */
    fun setMuted(muted: Boolean) {
        val device = defaultOutput()
        val address = output(MacCoreAudio.MUTE)
        if (device != null && audio.settable(device, address) && audio.setUint32(device, address, if (muted) 1 else 0)) return
        osascript("set volume output muted $muted") ?: throw IllegalStateException("Could not change mute")
    }

    override fun setDefaultDevice(id: String): Boolean {
        val device = outputDevices().firstOrNull { audio.string(it, global(MacCoreAudio.DEVICE_UID)) == id } ?: return false
        return audio.setUint32(MacCoreAudio.SYSTEM_OBJECT, global(MacCoreAudio.DEFAULT_OUTPUT), device)
    }

    /** Devices with at least one output stream (microphones have none). */
    private fun outputDevices(): List<Int> =
        audio.objectIds(MacCoreAudio.SYSTEM_OBJECT, global(MacCoreAudio.DEVICES))?.toList().orEmpty()
            .filter { (audio.dataSize(it, output(MacCoreAudio.STREAMS)) ?: 0) > 0 }

    private fun defaultOutput(): Int? =
        audio.uint32(MacCoreAudio.SYSTEM_OBJECT, global(MacCoreAudio.DEFAULT_OUTPUT))?.takeIf { it != 0 }

    /** Virtual main volume first, then the main element, then the average of the first two channels. */
    private fun readScalar(device: Int): Float? =
        audio.float32(device, output(MacCoreAudio.VIRTUAL_MAIN_VOLUME))
            ?: audio.float32(device, output(MacCoreAudio.VOLUME_SCALAR))
            ?: CHANNELS.mapNotNull { audio.float32(device, output(MacCoreAudio.VOLUME_SCALAR, it)) }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

    private fun writeScalar(device: Int, scalar: Float): Boolean {
        val virtual = output(MacCoreAudio.VIRTUAL_MAIN_VOLUME)
        if (audio.settable(device, virtual)) return audio.setFloat32(device, virtual, scalar)
        val main = output(MacCoreAudio.VOLUME_SCALAR)
        if (audio.settable(device, main)) return audio.setFloat32(device, main, scalar)
        val channels = CHANNELS.map { output(MacCoreAudio.VOLUME_SCALAR, it) }.filter { audio.settable(device, it) }
        return channels.isNotEmpty() && channels.all { audio.setFloat32(device, it, scalar) }
    }

    private fun scriptLevels(): AudioLevels? = osascript(LEVELS_SCRIPT)?.let(::parseScriptLevels)

    companion object {
        private val CHANNELS = listOf(1, 2)
        private const val LEVELS_SCRIPT =
            "set s to get volume settings\nreturn (output volume of s as text) & \",\" & (output muted of s as text)"

        fun toPercent(scalar: Float): Int = (scalar * 100f).roundToInt().coerceIn(0, 100)

        /** "42,false" from [LEVELS_SCRIPT]; null when the device has no volume ("missing value"). */
        fun parseScriptLevels(output: String): AudioLevels? {
            val volume = output.substringBefore(',').trim().toIntOrNull() ?: return null
            val muted = output.substringAfter(',', "false").trim().equals("true", ignoreCase = true)
            return AudioLevels(volume.coerceIn(0, 100), muted)
        }

        private fun global(selector: Int) = PropertyAddress(selector, MacCoreAudio.SCOPE_GLOBAL, MacCoreAudio.ELEMENT_MAIN)

        private fun output(selector: Int, element: Int = MacCoreAudio.ELEMENT_MAIN) =
            PropertyAddress(selector, MacCoreAudio.SCOPE_OUTPUT, element)
    }
}
