package com.saubh.deskbuddy.media

/** Pure position interpolation shared by the desktop's change filter and the phone's ticker. */
object PlaybackClock {
    /**
     * Position at [nowMs] given a sample of [positionMs] taken at [receivedAtMs].
     * Clamped to `[0, durationMs]`; a zero/unknown duration only clamps below.
     */
    fun positionAt(nowMs: Long, receivedAtMs: Long, positionMs: Long, durationMs: Long, playing: Boolean): Long {
        val elapsed = if (playing) (nowMs - receivedAtMs).coerceAtLeast(0) else 0
        val raw = positionMs + elapsed
        val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return raw.coerceIn(0, upper)
    }
}
