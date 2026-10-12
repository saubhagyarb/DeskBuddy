package com.saubh.deskbuddy.session

/** Auto-connect probe interval: doubles after each round where no paired PC answered, capped; reset on a wake-up. */
class AutoConnectBackoff(private val initialMs: Long = 10_000L, private val maxMs: Long = 300_000L) {
    var currentMs: Long = initialMs
        private set

    fun grow() {
        currentMs = (currentMs * 2).coerceAtMost(maxMs)
    }

    fun reset() {
        currentMs = initialMs
    }
}
