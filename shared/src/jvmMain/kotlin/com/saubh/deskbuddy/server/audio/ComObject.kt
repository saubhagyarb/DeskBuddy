package com.saubh.deskbuddy.server.audio

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference

/** Raw vtable caller for COM interfaces JNA Platform does not wrap. */
internal class ComObject(pointer: Pointer) : Unknown(pointer), AutoCloseable {
    fun call(slot: Int, vararg args: Any?): HRESULT = HRESULT(_invokeNativeInt(slot, arrayOf(pointer, *args)))

    fun check(slot: Int, vararg args: Any?) {
        val hr = call(slot, *args)
        if (hr.toInt() < 0) throw COMException("COM call slot $slot failed", hr)
    }

    fun child(slot: Int, vararg args: Any?): ComObject {
        val out = PointerByReference()
        check(slot, *args, out)
        return ComObject(out.value ?: throw COMException("null interface from slot $slot"))
    }

    override fun close() {
        Release()
    }
}
