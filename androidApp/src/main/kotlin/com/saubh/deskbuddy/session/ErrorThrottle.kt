package com.saubh.deskbuddy.session

import com.saubh.deskbuddy.ui.ErrorKind

/**
 * Lets each [ErrorKind] through at most once per [windowMs]. Keystrokes are many small commands;
 * a PC that rejects them all (e.g. an older DeskBuddy) must produce one snackbar, not one per key.
 */
class ErrorThrottle(private val windowMs: Long = 3_000L, private val clock: () -> Long = System::currentTimeMillis) {
    private val lastShown = mutableMapOf<ErrorKind, Long>()

    @Synchronized
    fun allow(kind: ErrorKind): Boolean {
        val now = clock()
        val last = lastShown[kind]
        if (last != null && now - last < windowMs) return false
        lastShown[kind] = now
        return true
    }
}
