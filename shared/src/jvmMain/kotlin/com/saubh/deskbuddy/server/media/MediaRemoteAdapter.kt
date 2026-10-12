package com.saubh.deskbuddy.server.media

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * The bundled `mediaremote-adapter` (BSD-3, github.com/ungive/mediaremote-adapter). It runs under the
 * system perl, which macOS 15.4+ still lets use MediaRemote, so it sees every app's now playing (browsers too).
 */
class MediaRemoteAdapter(
    private val script: Path,
    private val framework: Path,
    private val run: (List<String>) -> Int = ::runQuiet,
) {
    fun command(vararg args: String): List<String> = listOf(PERL, script.toString(), framework.toString()) + args

    fun startStream(): Process =
        ProcessBuilder(command("stream", "--micros", "--debounce=100")).redirectError(ProcessBuilder.Redirect.DISCARD).start()

    fun isWorking(): Boolean = run(command("test")) == 0

    fun send(id: Int): Boolean = run(command("send", id.toString())) == 0

    fun seek(positionMs: Long): Boolean = run(command("seek", (positionMs.coerceAtLeast(0) * 1_000).toString())) == 0

    companion object {
        const val SEND_TOGGLE = 2
        const val SEND_NEXT = 4
        const val SEND_PREVIOUS = 5
        private const val PERL = "/usr/bin/perl"
        private const val TIMEOUT_MS = 3_000L

        /** Compose Desktop sets `compose.application.resources.dir` for both the packaged app and `./gradlew run`. */
        fun locate(resourcesDir: String? = System.getProperty("compose.application.resources.dir")): MediaRemoteAdapter? {
            val dir = resourcesDir?.let(Path::of) ?: return null
            val script = dir.resolve("mediaremote-adapter.pl")
            val framework = dir.resolve("MediaRemoteAdapter.framework")
            return if (Files.isRegularFile(script) && Files.isDirectory(framework)) MediaRemoteAdapter(script, framework) else null
        }

        private fun runQuiet(command: List<String>): Int = runCatching {
            val process = ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (!process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
                -1
            } else {
                process.exitValue()
            }
        }.getOrDefault(-1)
    }
}
