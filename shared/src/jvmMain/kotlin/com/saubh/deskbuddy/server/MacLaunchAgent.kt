package com.saubh.deskbuddy.server

import java.nio.file.Files
import java.nio.file.Path

/**
 * "Start at login" on macOS: a per-user LaunchAgent that runs the stored launch command through
 * `/bin/sh -c` at sign-in. launchd picks the file up at the next login, so nothing is loaded now.
 */
class MacLaunchAgent(
    private val plist: Path = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", "$LABEL.plist"),
) : StartupRegistration.Registry {

    override fun read(): String? = runCatching {
        if (!Files.exists(plist)) return null
        commandIn(Files.readString(plist))
    }.getOrNull()

    override fun write(value: String) {
        Files.createDirectories(plist.parent)
        Files.writeString(plist, plistFor(value))
    }

    override fun delete() {
        Files.deleteIfExists(plist)
    }

    companion object {
        const val LABEL = "com.saubh.konnekt"
        private const val EXEC = "exec "
        private val COMMAND = Regex("<string>-c</string>\\s*<string>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

        /** `exec` so launchd tracks the app itself rather than a waiting shell. */
        fun plistFor(command: String): String = """
            |<?xml version="1.0" encoding="UTF-8"?>
            |<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            |<plist version="1.0">
            |<dict>
            |    <key>Label</key>
            |    <string>$LABEL</string>
            |    <key>ProgramArguments</key>
            |    <array>
            |        <string>/bin/sh</string>
            |        <string>-c</string>
            |        <string>${escape(EXEC + command)}</string>
            |    </array>
            |    <key>RunAtLoad</key>
            |    <true/>
            |    <key>ProcessType</key>
            |    <string>Interactive</string>
            |</dict>
            |</plist>
            |""".trimMargin()

        /** The command [plistFor] stored, or null when the file was not written by it. */
        fun commandIn(plist: String): String? =
            COMMAND.find(plist)?.groupValues?.get(1)?.let(::unescape)?.removePrefix(EXEC)

        private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        private fun unescape(text: String) = text.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    }
}
