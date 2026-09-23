package com.saubh.deskbuddy.server.media

import com.saubh.deskbuddy.media.PlaybackClock
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import kotlin.math.abs

/**
 * Decides whether a fresh sample is worth pushing to the phone: any non-position change,
 * or a position that drifted more than [driftMs] from what the phone would interpolate.
 */
class NowPlayingFilter(private val driftMs: Long = 1_500) {
    private var published: NowPlayingCommand? = null
    private var publishedAtMs = 0L

    /** Forget the last published sample, e.g. after the helper restarted and consumers were reset to NONE. */
    fun reset() {
        published = null
        publishedAtMs = 0L
    }

    fun shouldPublish(next: NowPlayingCommand, nowMs: Long): Boolean {
        val prev = published
        val changed = prev == null ||
            prev.copy(positionMs = 0) != next.copy(positionMs = 0) ||
            abs(expectedPosition(prev, nowMs) - next.positionMs) > driftMs
        if (changed) {
            published = next
            publishedAtMs = nowMs
        }
        return changed
    }

    private fun expectedPosition(prev: NowPlayingCommand, nowMs: Long): Long =
        PlaybackClock.positionAt(nowMs, publishedAtMs, prev.positionMs, prev.durationMs, prev.status == PlaybackStatus.PLAYING)
}
