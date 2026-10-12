package com.saubh.deskbuddy.server.audio

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference

/**
 * Thin typed wrapper over the Core Audio HAL (`AudioObject*` C functions) through JNA. Loaded
 * lazily, so other platforms never resolve the framework. Every call returns null (or false) on a
 * non-zero `OSStatus` instead of throwing, so callers can fall back.
 */
internal object MacCoreAudio {
    const val SYSTEM_OBJECT = 1
    val DEVICES = FourCc.of("dev#")
    val DEFAULT_OUTPUT = FourCc.of("dOut")
    val SCOPE_GLOBAL = FourCc.of("glob")
    val SCOPE_OUTPUT = FourCc.of("outp")
    const val ELEMENT_MAIN = 0
    val STREAMS = FourCc.of("stm#")
    val DEVICE_UID = FourCc.of("uid ")
    val NAME = FourCc.of("lnam")
    /** kAudioHardwareServiceDeviceProperty_VirtualMainVolume: one scalar across all channels. */
    val VIRTUAL_MAIN_VOLUME = FourCc.of("vmvc")
    val VOLUME_SCALAR = FourCc.of("volm")
    val MUTE = FourCc.of("mute")

    @Structure.FieldOrder("mSelector", "mScope", "mElement")
    class PropertyAddress(
        @JvmField var mSelector: Int = 0,
        @JvmField var mScope: Int = 0,
        @JvmField var mElement: Int = 0,
    ) : Structure()

    @Suppress("FunctionName")
    interface Lib : Library {
        /** C `Boolean` is one byte; a JNA boolean would read garbage from the upper register bits. */
        fun AudioObjectHasProperty(objectId: Int, address: PropertyAddress): Byte
        fun AudioObjectIsPropertySettable(objectId: Int, address: PropertyAddress, outSettable: ByteByReference): Int
        fun AudioObjectGetPropertyDataSize(objectId: Int, address: PropertyAddress, qualifierSize: Int, qualifier: Pointer?, outSize: IntByReference): Int
        fun AudioObjectGetPropertyData(objectId: Int, address: PropertyAddress, qualifierSize: Int, qualifier: Pointer?, ioSize: IntByReference, outData: Pointer): Int
        fun AudioObjectSetPropertyData(objectId: Int, address: PropertyAddress, qualifierSize: Int, qualifier: Pointer?, dataSize: Int, data: Pointer): Int
    }

    private val lib: Lib by lazy { Native.load("CoreAudio", Lib::class.java) }

    fun has(objectId: Int, address: PropertyAddress): Boolean = lib.AudioObjectHasProperty(objectId, address).toInt() != 0

    fun settable(objectId: Int, address: PropertyAddress): Boolean {
        if (!has(objectId, address)) return false
        val out = ByteByReference()
        return lib.AudioObjectIsPropertySettable(objectId, address, out) == 0 && out.value.toInt() != 0
    }

    fun dataSize(objectId: Int, address: PropertyAddress): Int? {
        val size = IntByReference()
        return if (lib.AudioObjectGetPropertyDataSize(objectId, address, 0, null, size) == 0) size.value else null
    }

    /** An array of `AudioObjectID`s (e.g. the device list). */
    fun objectIds(objectId: Int, address: PropertyAddress): IntArray? {
        val size = dataSize(objectId, address) ?: return null
        if (size == 0) return IntArray(0)
        val buffer = Memory(size.toLong())
        val io = IntByReference(size)
        if (lib.AudioObjectGetPropertyData(objectId, address, 0, null, io, buffer) != 0) return null
        return buffer.getIntArray(0, io.value / Int.SIZE_BYTES)
    }

    fun uint32(objectId: Int, address: PropertyAddress): Int? = read(objectId, address, Int.SIZE_BYTES) { it.getInt(0) }

    fun float32(objectId: Int, address: PropertyAddress): Float? = read(objectId, address, Float.SIZE_BYTES) { it.getFloat(0) }

    /** A `CFStringRef` property; the returned reference is released here. */
    fun string(objectId: Int, address: PropertyAddress): String? {
        val pointer = read(objectId, address, Native.POINTER_SIZE) { it.getPointer(0) } ?: return null
        val ref = CoreFoundation.CFStringRef(pointer)
        return try {
            ref.stringValue()
        } finally {
            CoreFoundation.INSTANCE.CFRelease(ref)
        }
    }

    fun setUint32(objectId: Int, address: PropertyAddress, value: Int): Boolean =
        write(objectId, address, Memory(Int.SIZE_BYTES.toLong()).apply { setInt(0, value) })

    fun setFloat32(objectId: Int, address: PropertyAddress, value: Float): Boolean =
        write(objectId, address, Memory(Float.SIZE_BYTES.toLong()).apply { setFloat(0, value) })

    private fun <T> read(objectId: Int, address: PropertyAddress, size: Int, value: (Memory) -> T): T? {
        if (!has(objectId, address)) return null
        val buffer = Memory(size.toLong())
        val io = IntByReference(size)
        return if (lib.AudioObjectGetPropertyData(objectId, address, 0, null, io, buffer) == 0) value(buffer) else null
    }

    private fun write(objectId: Int, address: PropertyAddress, data: Memory): Boolean =
        lib.AudioObjectSetPropertyData(objectId, address, 0, null, data.size().toInt(), data) == 0
}
