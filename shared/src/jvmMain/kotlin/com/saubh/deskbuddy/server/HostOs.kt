package com.saubh.deskbuddy.server

/** The desktop OS this JVM runs on, from `os.name`. Every check takes the name so tests can pass one. */
object HostOs {
    val name: String get() = System.getProperty("os.name") ?: "unknown"

    fun isWindows(osName: String = name) = osName.startsWith("Windows", ignoreCase = true)

    /** `os.name` is "Mac OS X" on every macOS release. */
    fun isMac(osName: String = name) = osName.startsWith("Mac", ignoreCase = true)

    fun isLinux(osName: String = name) = osName.startsWith("Linux", ignoreCase = true)
}
