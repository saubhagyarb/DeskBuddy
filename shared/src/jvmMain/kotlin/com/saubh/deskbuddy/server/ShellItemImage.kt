package com.saubh.deskbuddy.server

import com.saubh.deskbuddy.server.audio.ComObject
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.image.BufferedImage
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Icon of any shell item (including `shell:AppsFolder\…` Store apps) via IShellItemImageFactory.
 * Calls run on one dedicated STA thread, as the shell expects.
 */
internal object ShellItemImage {

    private interface Shell32Ex : StdCallLibrary {
        fun SHCreateItemFromParsingName(path: WString, bindCtx: Pointer?, riid: Guid.REFIID, out: PointerByReference): WinNT.HRESULT

        companion object {
            val INSTANCE: Shell32Ex = Native.load("shell32", Shell32Ex::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }

    @Structure.FieldOrder("cx", "cy")
    class SizeByValue(@JvmField var cx: Int = 0, @JvmField var cy: Int = 0) : Structure(), Structure.ByValue

    private val IID_IShellItemImageFactory = Guid.REFIID(Guid.IID("{bcc18b79-ba16-442f-80c4-8a59c30c463b}"))
    private const val GET_IMAGE = 3
    private const val SIIGBF_ICONONLY = 0x4

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "deskbuddy-shell-icons").apply { isDaemon = true } }
    private var initialized = false

    /** Null when the item does not exist or has no icon. */
    fun render(parsingName: String, sizePx: Int): BufferedImage? = try {
        executor.submit(Callable { onShellThread(parsingName, sizePx) }).get()
    } catch (e: ExecutionException) {
        null
    }

    private fun onShellThread(parsingName: String, sizePx: Int): BufferedImage? {
        if (!initialized) {
            Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
            initialized = true
        }
        val out = PointerByReference()
        val hr = Shell32Ex.INSTANCE.SHCreateItemFromParsingName(WString(parsingName), null, IID_IShellItemImageFactory, out)
        if (hr.toInt() < 0 || out.value == null) return null
        val bitmapRef = PointerByReference()
        ComObject(out.value).use { factory ->
            if (factory.call(GET_IMAGE, SizeByValue(sizePx, sizePx), SIIGBF_ICONONLY, bitmapRef).toInt() < 0) return null
        }
        val handle = bitmapRef.value ?: return null
        val bitmap = WinDef.HBITMAP(handle)
        return try {
            toImage(bitmap)
        } finally {
            GDI32.INSTANCE.DeleteObject(bitmap)
        }
    }

    private fun toImage(bitmap: WinDef.HBITMAP): BufferedImage? {
        val info = WinGDI.BITMAP()
        if (GDI32.INSTANCE.GetObject(bitmap, info.size(), info.pointer) == 0) return null
        info.read()
        val width = info.bmWidth.toInt()
        val height = Math.abs(info.bmHeight.toInt())
        if (width <= 0 || height <= 0) return null

        val header = WinGDI.BITMAPINFO()
        header.bmiHeader.biWidth = width
        header.bmiHeader.biHeight = -height // top-down rows
        header.bmiHeader.biPlanes = 1
        header.bmiHeader.biBitCount = 32
        header.bmiHeader.biCompression = WinGDI.BI_RGB
        val pixels = Memory(width.toLong() * height * 4)
        val dc = User32.INSTANCE.GetDC(null)
        val rows = try {
            GDI32.INSTANCE.GetDIBits(dc, bitmap, 0, height, pixels, header, WinGDI.DIB_RGB_COLORS)
        } finally {
            User32.INSTANCE.ReleaseDC(null, dc)
        }
        if (rows == 0) return null

        // Little-endian BGRA reads back as 0xAARRGGBB. Shell bitmaps carry premultiplied alpha,
        // or none at all (every alpha 0) for opaque icons.
        val argb = pixels.getIntArray(0, width * height)
        val hasAlpha = argb.any { (it ushr 24) != 0 }
        val straight = if (hasAlpha) IntArray(argb.size) { unpremultiply(argb[it]) } else IntArray(argb.size) { argb[it] or OPAQUE }
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply { setRGB(0, 0, width, height, straight, 0, width) }
    }

    private const val OPAQUE = 0xFF shl 24

    private fun unpremultiply(pixel: Int): Int {
        val a = pixel ushr 24
        if (a == 0 || a == 255) return pixel
        fun channel(shift: Int) = (((pixel ushr shift) and 0xFF) * 255 / a).coerceAtMost(255) shl shift
        return (a shl 24) or channel(16) or channel(8) or channel(0)
    }
}
