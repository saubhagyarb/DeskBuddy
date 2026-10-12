package com.saubh.deskbuddy.server.keyboard

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference

/**
 * ⌃⌥K via Carbon `RegisterEventHotKey`, which needs no Accessibility permission. The event is
 * delivered by the AppKit main run loop that AWT already runs, so [onPressed] runs on that thread.
 */
class MacGlobalHotkey(private val onPressed: () -> Unit) : GlobalHotkey {
    companion object {
        const val LABEL = "⌃⌥K"
        private const val KVK_ANSI_K = 0x28
        private const val CONTROL_KEY = 0x1000
        private const val OPTION_KEY = 0x0800
        private const val EVENT_HOT_KEY_PRESSED = 6
        private const val NO_ERR = 0
        private val KEYBOARD_CLASS = fourCc("keyb")
        private val SIGNATURE = fourCc("KNKT")

        private fun fourCc(code: String): Int = code.fold(0) { acc, c -> (acc shl 8) or (c.code and 0xFF) }
    }

    @Suppress("FunctionName")
    internal interface Carbon : Library {
        fun GetEventDispatcherTarget(): Pointer
        fun InstallEventHandler(
            target: Pointer,
            handler: HotkeyHandler,
            numTypes: Int,
            list: EventTypeSpec,
            userData: Pointer?,
            outRef: PointerByReference,
        ): Int
        fun RemoveEventHandler(handler: Pointer): Int
        fun RegisterEventHotKey(
            keyCode: Int,
            modifiers: Int,
            hotKeyId: EventHotKeyID.ByValue,
            target: Pointer,
            options: Int,
            outRef: PointerByReference,
        ): Int
        fun UnregisterEventHotKey(ref: Pointer): Int
    }

    internal fun interface HotkeyHandler : Callback {
        fun invoke(nextHandler: Pointer?, event: Pointer?, userData: Pointer?): Int
    }

    @Structure.FieldOrder("eventClass", "eventKind")
    internal open class EventTypeSpec(@JvmField var eventClass: Int = 0, @JvmField var eventKind: Int = 0) : Structure()

    @Structure.FieldOrder("signature", "id")
    internal open class EventHotKeyID(@JvmField var signature: Int = 0, @JvmField var id: Int = 0) : Structure() {
        class ByValue(signature: Int = 0, id: Int = 0) : EventHotKeyID(signature, id), Structure.ByValue
    }

    /** Held strongly: JNA frees a callback's native stub once the Kotlin object is collected. */
    private val handler = HotkeyHandler { _, _, _ ->
        runCatching { onPressed() }
        NO_ERR
    }

    /** Loaded on first use, so constructing this class off macOS never touches Carbon. */
    private val carbon: Carbon by lazy { Native.load("/System/Library/Frameworks/Carbon.framework/Carbon", Carbon::class.java) }
    private var handlerRef: Pointer? = null
    private var hotKeyRef: Pointer? = null

    @Synchronized
    override fun start(): Boolean = runCatching {
        val target = carbon.GetEventDispatcherTarget()
        val handlerOut = PointerByReference()
        val spec = EventTypeSpec(KEYBOARD_CLASS, EVENT_HOT_KEY_PRESSED)
        if (carbon.InstallEventHandler(target, handler, 1, spec, null, handlerOut) != NO_ERR) return false
        handlerRef = handlerOut.value
        val hotKeyOut = PointerByReference()
        val id = EventHotKeyID.ByValue(SIGNATURE, 1)
        if (carbon.RegisterEventHotKey(KVK_ANSI_K, CONTROL_KEY or OPTION_KEY, id, target, 0, hotKeyOut) != NO_ERR) {
            stop()
            return false
        }
        hotKeyRef = hotKeyOut.value
        true
    }.getOrDefault(false)

    @Synchronized
    override fun stop() {
        runCatching {
            hotKeyRef?.let(carbon::UnregisterEventHotKey)
            handlerRef?.let(carbon::RemoveEventHandler)
        }
        hotKeyRef = null
        handlerRef = null
    }
}
