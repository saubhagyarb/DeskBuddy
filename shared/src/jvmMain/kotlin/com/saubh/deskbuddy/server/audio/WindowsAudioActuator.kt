package com.saubh.deskbuddy.server.audio

import com.saubh.deskbuddy.protocol.AudioDevice
import com.saubh.deskbuddy.server.AudioActuator
import com.saubh.deskbuddy.server.audio.CoreAudioIds as Ids
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Core Audio through raw COM vtables (IMMDeviceEnumerator, IAudioEndpointVolume) plus the
 * undocumented-but-stable IPolicyConfig for switching the default device. All calls run on
 * one dedicated MTA thread.
 */
class WindowsAudioActuator : AudioActuator {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "deskbuddy-audio").apply { isDaemon = true } }
    private var initialized = false

    override fun devices(): List<AudioDevice> = onComThread {
        enumerator().use { enumerator ->
            val defaultId = enumerator.child(Ids.GET_DEFAULT_AUDIO_ENDPOINT, Ids.eRender, Ids.eMultimedia).use(::deviceId)
            enumerator.child(Ids.ENUM_AUDIO_ENDPOINTS, Ids.eRender, Ids.DEVICE_STATE_ACTIVE).use { collection ->
                val count = IntByReference().also { collection.check(Ids.COLLECTION_GET_COUNT, it) }.value
                (0 until count).map { index ->
                    collection.child(Ids.COLLECTION_ITEM, index).use { device ->
                        val id = deviceId(device)
                        AudioDevice(id, friendlyName(device), isDefault = id == defaultId)
                    }
                }
            }
        }
    }

    override fun volume(): Int = onComThread {
        val scalar = withEndpointVolume { ep -> FloatByReference().also { ep.check(Ids.GET_MASTER_VOLUME_SCALAR, it) }.value }
        (scalar * 100f).roundToInt().coerceIn(0, 100)
    }

    override fun setVolume(percent: Int) = onComThread {
        withEndpointVolume { ep -> ep.check(Ids.SET_MASTER_VOLUME_SCALAR, percent.coerceIn(0, 100) / 100f, null) }
    }

    override fun isMuted(): Boolean = onComThread {
        withEndpointVolume { ep -> IntByReference().also { ep.check(Ids.GET_MUTE, it) }.value != 0 }
    }

    override fun setDefaultDevice(id: String): Boolean = onComThread {
        if (deviceIds().none { it == id }) return@onComThread false
        val out = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(Ids.CLSID_PolicyConfigClient, null, Ids.CLSCTX_ALL, Ids.IID_IPolicyConfig, out)
        if (hr.toInt() < 0) return@onComThread false
        ComObject(out.value).use { policy ->
            policy.check(Ids.SET_DEFAULT_ENDPOINT, WString(id), Ids.eConsole)
            policy.check(Ids.SET_DEFAULT_ENDPOINT, WString(id), Ids.eMultimedia)
        }
        true
    }

    private fun deviceIds(): List<String> = enumerator().use { enumerator ->
        enumerator.child(Ids.ENUM_AUDIO_ENDPOINTS, Ids.eRender, Ids.DEVICE_STATE_ACTIVE).use { collection ->
            val count = IntByReference().also { collection.check(Ids.COLLECTION_GET_COUNT, it) }.value
            (0 until count).map { i -> collection.child(Ids.COLLECTION_ITEM, i).use(::deviceId) }
        }
    }

    private fun <T> withEndpointVolume(block: (ComObject) -> T): T = enumerator().use { enumerator ->
        enumerator.child(Ids.GET_DEFAULT_AUDIO_ENDPOINT, Ids.eRender, Ids.eMultimedia).use { device ->
            device.child(Ids.DEVICE_ACTIVATE, Ids.IID_IAudioEndpointVolume.pointer, Ids.CLSCTX_ALL, null).use(block)
        }
    }

    private fun enumerator(): ComObject {
        val out = PointerByReference()
        val hr = Ole32.INSTANCE.CoCreateInstance(Ids.CLSID_MMDeviceEnumerator, null, Ids.CLSCTX_ALL, Ids.IID_IMMDeviceEnumerator, out)
        if (hr.toInt() < 0) throw IllegalStateException("CoCreateInstance(MMDeviceEnumerator) failed: $hr")
        return ComObject(out.value)
    }

    private fun deviceId(device: ComObject): String {
        val out = PointerByReference()
        device.check(Ids.DEVICE_GET_ID, out)
        val id = out.value.getWideString(0)
        Ole32.INSTANCE.CoTaskMemFree(out.value)
        return id
    }

    private fun friendlyName(device: ComObject): String =
        device.child(Ids.DEVICE_OPEN_PROPERTY_STORE, Ids.STGM_READ).use { store ->
            val key = PropertyKey(Ids.PKEY_Device_FriendlyName, Ids.PID_Device_FriendlyName).also { it.write() }
            val value = PropVariant().also { it.write() }
            store.check(Ids.PROPERTY_STORE_GET_VALUE, key.pointer, value.pointer)
            value.read()
            val text = if (value.vt.toInt() == Ids.VT_LPWSTR) value.pwszVal?.getWideString(0).orEmpty() else ""
            value.pwszVal?.let { Ole32.INSTANCE.CoTaskMemFree(it) }
            text.ifBlank { "Audio device" }
        }

    private fun <T> onComThread(block: () -> T): T = try {
        executor.submit(
            Callable {
                if (!initialized) {
                    Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED)
                    initialized = true
                }
                block()
            },
        ).get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
}
