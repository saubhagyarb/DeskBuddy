package com.saubh.deskbuddy.server

import com.sun.jna.platform.win32.User32

interface PowerActuator {
    fun shutdown()
    fun restart()
    fun lock()
}

/** `shutdown.exe` for power off / restart (no countdown), `LockWorkStation` for lock. */
class WindowsPowerActuator(
    private val run: (List<String>) -> Unit = { ProcessBuilder(it).redirectErrorStream(true).start() },
    private val lockWorkStation: () -> Boolean = { User32.INSTANCE.LockWorkStation().booleanValue() },
) : PowerActuator {
    override fun shutdown() = run(listOf("shutdown.exe", "/s", "/t", "0"))
    override fun restart() = run(listOf("shutdown.exe", "/r", "/t", "0"))
    override fun lock() {
        check(lockWorkStation()) { "LockWorkStation failed" }
    }
}

class UnsupportedPowerActuator(private val osName: String) : PowerActuator {
    override fun shutdown() = unsupported()
    override fun restart() = unsupported()
    override fun lock() = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOsException(osName)
}
