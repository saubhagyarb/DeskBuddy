package com.saubh.deskbuddy.server.keyboard

import com.saubh.deskbuddy.protocol.Key
import com.saubh.deskbuddy.protocol.KeyModifier
import java.io.File
import java.util.concurrent.TimeUnit

/** X11 typing through `xdotool`; created only when it is on PATH. */
class XdotoolKeyboardActuator : KeyboardActuator {
    override fun type(text: String) = run(XdotoolArgs.forText(text), XdotoolArgs.timeoutMs(text))

    override fun press(key: Key, modifiers: Set<KeyModifier>) = run(XdotoolArgs.forKey(key, modifiers), XdotoolArgs.timeoutMs(""))

    private fun run(command: List<String>, timeoutMs: Long) {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) process.destroyForcibly()
    }

    companion object {
        fun isAvailable(): Boolean = System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .any { File(it, "xdotool").canExecute() }
    }
}
