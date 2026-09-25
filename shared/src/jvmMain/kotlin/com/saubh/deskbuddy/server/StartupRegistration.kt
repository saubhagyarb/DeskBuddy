package com.saubh.deskbuddy.server

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Files
import java.nio.file.Path

/**
 * "Start with Windows": a per-user `Run` registry value that launches this app hidden
 * ([BACKGROUND_ARG]) at sign-in. On first launch it is switched on by default.
 */
class StartupRegistration(
    private val command: () -> String? = { currentLaunchCommand() },
    private val registry: Registry = WindowsRegistry,
    private val initializedMarker: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "startup-initialized"),
) {
    interface Registry {
        fun read(): String?
        fun write(value: String)
        fun delete()
    }

    companion object {
        const val BACKGROUND_ARG = "--background"
        private const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        private const val VALUE_NAME = "DeskBuddy"

        private val defaultArgFile: Path = Path.of(System.getProperty("user.home"), ".deskbuddy", "launch.args")

        /**
         * The packaged launcher (jpackage sets `jpackage.app-path`). Otherwise (e.g. a Gradle
         * `run`) the running JVM's options, classpath and main class are written to a Java
         * `@argfile`, because Windows does not expose a process's arguments and a Gradle
         * classpath can exceed the command-line limit.
         */
        fun currentLaunchCommand(argFile: Path = defaultArgFile): String? {
            System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }?.let { return "${quote(it)} $BACKGROUND_ARG" }
            val main = System.getProperty("sun.java.command")?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: return null
            val javaHome = System.getProperty("java.home") ?: return null
            // The console-less launcher, so no terminal window flashes up at sign-in.
            val launcher = listOf("javaw.exe", "java.exe").map { Path.of(javaHome, "bin", it) }.firstOrNull(Files::exists) ?: return null
            val jvmOptions = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments
                .filterNot { it.startsWith("-agentlib") || it.startsWith("-javaagent") || it.startsWith("-Xrunjdwp") }
            val target = if (main.endsWith(".jar", ignoreCase = true)) {
                listOf("-jar", main)
            } else {
                listOf("-cp", System.getProperty("java.class.path").orEmpty(), main)
            }
            Files.createDirectories(argFile.parent)
            // Forward slashes: inside an argfile a backslash is an escape character.
            Files.writeString(argFile, (jvmOptions + target).joinToString("\n") { "\"${it.replace('\\', '/')}\"" })
            return "${quote(launcher.toString())} @${quote(argFile.toString())} $BACKGROUND_ARG"
        }

        private fun quote(arg: String) = "\"$arg\""
    }

    object WindowsRegistry : Registry {
        override fun read(): String? = runCatching {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
            } else {
                null
            }
        }.getOrNull()

        override fun write(value: String) = Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME, value)

        override fun delete() {
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, VALUE_NAME)
            }
        }
    }

    val isEnabled: Boolean get() = registry.read() != null

    /**
     * Called at every launch: turns startup on the first time, and refreshes the stored command
     * when it is on (the app may have moved or been rebuilt). Returns whether it is on.
     */
    fun sync(): Boolean {
        val firstRun = !Files.exists(initializedMarker)
        if (firstRun) {
            runCatching {
                Files.createDirectories(initializedMarker.parent)
                Files.createFile(initializedMarker)
            }
        }
        if (firstRun || isEnabled) setEnabled(true)
        return isEnabled
    }

    /** Returns false when the launch command cannot be determined or the registry write fails. */
    fun setEnabled(enabled: Boolean): Boolean = runCatching {
        if (enabled) registry.write(command() ?: return false) else registry.delete()
        true
    }.getOrDefault(false)
}
