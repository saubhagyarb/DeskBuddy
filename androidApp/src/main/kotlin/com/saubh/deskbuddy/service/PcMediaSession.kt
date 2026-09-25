package com.saubh.deskbuddy.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus

/**
 * Mirrors what the PC is playing into an Android media session, so the system media controls
 * (notification shade, lock screen) show it with artwork, a seek bar and transport buttons.
 */
class PcMediaSession(
    context: Context,
    private val onAction: (MediaAction) -> Unit,
    private val onSeek: (Long) -> Unit,
) {
    private val session = MediaSession(context, "DeskBuddy PC").apply {
        setCallback(
            object : MediaSession.Callback() {
                // The PC only exposes a toggle, so play and pause both send it.
                override fun onPlay() = onAction(MediaAction.PLAY_PAUSE)
                override fun onPause() = onAction(MediaAction.PLAY_PAUSE)
                override fun onSkipToNext() = onAction(MediaAction.NEXT)
                override fun onSkipToPrevious() = onAction(MediaAction.PREVIOUS)
                override fun onSeekTo(pos: Long) = onSeek(pos)
            },
        )
    }

    val token: MediaSession.Token get() = session.sessionToken

    /** [receivedAtMs] is wall-clock time of the push that carried [nowPlaying]'s position. */
    fun update(nowPlaying: NowPlayingCommand, receivedAtMs: Long, artwork: Bitmap?) {
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, nowPlaying.title.orEmpty())
            .putString(MediaMetadata.METADATA_KEY_ARTIST, nowPlaying.artist.orEmpty())
            .putString(MediaMetadata.METADATA_KEY_ALBUM, nowPlaying.album.orEmpty())
            .putLong(MediaMetadata.METADATA_KEY_DURATION, nowPlaying.durationMs)
        artwork?.let { metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) }
        session.setMetadata(metadata.build())

        val playing = nowPlaying.status == PlaybackStatus.PLAYING
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if (nowPlaying.canSeek && nowPlaying.durationMs > 0) actions = actions or PlaybackState.ACTION_SEEK_TO
        // The system interpolates from this timestamp, so the seek bar moves without further updates.
        val positionTakenAt = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - receivedAtMs).coerceAtLeast(0)
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(stateOf(nowPlaying.status), nowPlaying.positionMs, if (playing) 1f else 0f, positionTakenAt)
                .build(),
        )
        if (!session.isActive) session.isActive = true
    }

    fun deactivate() {
        if (session.isActive) session.isActive = false
    }

    fun release() = session.release()

    private fun stateOf(status: PlaybackStatus) = when (status) {
        PlaybackStatus.PLAYING -> PlaybackState.STATE_PLAYING
        PlaybackStatus.PAUSED -> PlaybackState.STATE_PAUSED
        PlaybackStatus.STOPPED -> PlaybackState.STATE_STOPPED
        PlaybackStatus.NONE -> PlaybackState.STATE_NONE
    }
}
