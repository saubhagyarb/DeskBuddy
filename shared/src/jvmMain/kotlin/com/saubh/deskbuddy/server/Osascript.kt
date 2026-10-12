package com.saubh.deskbuddy.server

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Runs AppleScript or JXA through `/usr/bin/osascript`. The program is piped on stdin and inputs
 * travel as `argv` (`on run argv` / `function run(argv)`), never spliced into the script text.
 */
object Osascript {
    enum class Language(val flag: String) { APPLESCRIPT("AppleScript"), JAVASCRIPT("JavaScript") }

    private const val OSASCRIPT = "/usr/bin/osascript"

    /** Trimmed stdout, or null when osascript is missing, fails, or exceeds [timeoutMs]. */
    fun run(
        script: String,
        args: List<String> = emptyList(),
        language: Language = Language.APPLESCRIPT,
        timeoutMs: Long = 5_000L,
    ): String? = runCatching {
        val process = start(script, args, language, ProcessBuilder.Redirect.DISCARD)
        // Read on another thread: a hung script must not block past the timeout.
        val output = CompletableFuture.supplyAsync { process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8) }
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() != 0) return null
        output.get(1, TimeUnit.SECONDS).trim()
    }.getOrNull()

    /** A long-running script (e.g. a polling loop); the caller owns the process and reads its stdout. */
    fun start(
        script: String,
        args: List<String> = emptyList(),
        language: Language = Language.APPLESCRIPT,
        stderr: ProcessBuilder.Redirect = ProcessBuilder.Redirect.DISCARD,
    ): Process {
        val process = ProcessBuilder(listOf(OSASCRIPT, "-l", language.flag, "-") + args)
            .redirectError(stderr)
            .start()
        process.outputStream.use { it.write(script.toByteArray(StandardCharsets.UTF_8)) }
        return process
    }
}
