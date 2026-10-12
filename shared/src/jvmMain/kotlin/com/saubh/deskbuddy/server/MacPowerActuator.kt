package com.saubh.deskbuddy.server

import com.sun.jna.NativeLibrary

/**
 * Shut down / restart through System Events (asks for Automation permission once; apps may still
 * ask to save, as on Windows). Lock calls `SACLockScreenImmediate` from the private `login`
 * framework, which needs no permission; if it is missing, Ctrl+Cmd+Q is sent instead.
 */
class MacPowerActuator(
    private val runAppleScript: (String) -> String? = { Osascript.run(it) },
    private val lockScreen: () -> Boolean = ::lockViaLoginFramework,
) : PowerActuator {
    override fun shutdown() = systemEvents("shut down")
    override fun restart() = systemEvents("restart")

    override fun lock() {
        if (lockScreen()) return
        checkNotNull(runAppleScript("""tell application "System Events" to keystroke "q" using {control down, command down}""")) {
            "Could not lock the screen"
        }
    }

    private fun systemEvents(command: String) {
        checkNotNull(runAppleScript("""tell application "System Events" to $command""")) { "System Events refused: $command" }
    }

    private companion object {
        const val LOGIN_FRAMEWORK = "/System/Library/PrivateFrameworks/login.framework/Versions/Current/login"

        fun lockViaLoginFramework(): Boolean = runCatching {
            NativeLibrary.getInstance(LOGIN_FRAMEWORK).getFunction("SACLockScreenImmediate").invokeInt(emptyArray())
            true
        }.getOrDefault(false)
    }
}
