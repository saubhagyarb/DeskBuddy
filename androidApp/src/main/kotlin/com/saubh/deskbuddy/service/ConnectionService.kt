package com.saubh.deskbuddy.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.appGraph
import com.saubh.deskbuddy.protocol.MediaCommand
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus
import com.saubh.deskbuddy.ui.ConnectionUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps the process — and so the connection and auto-connect — alive while no screen is open,
 * and shows one ongoing notification: the connection status, or full media controls while the
 * PC is playing something. Runs whenever at least one PC is paired.
 */
class ConnectionService : Service() {

    companion object {
        private const val MAX_ART_PX = 512

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionService::class.java))
        }
    }

    /** What the notification shows; the media controller's 500 ms position ticks are deliberately not part of it. */
    private data class Snapshot(
        val connection: ConnectionUiState,
        val nowPlaying: NowPlayingCommand?,
        val receivedAtMs: Long,
        val artworkId: String?,
        val hasArtwork: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notifications: SessionNotifications
    private lateinit var mediaSession: PcMediaSession
    private var artworkCache: Pair<String, Bitmap?>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val graph = appGraph
        notifications = SessionNotifications(this)
        mediaSession = PcMediaSession(
            context = this,
            onAction = { action -> graph.scope.launch { graph.session.send(MediaCommand(action)) } },
            onSeek = graph.media::seek,
        )
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        val started = runCatching {
            ServiceCompat.startForeground(this, SessionNotifications.ID, statusNotification(graph.session.uiState.value), type)
        }
        // Android may refuse a background start (e.g. a sticky restart); auto-connect then waits for the app to open.
        if (started.isFailure) {
            stopSelf()
            return
        }
        graph.autoConnector.start(this)
        graph.autoStandby.start()

        scope.launch {
            combine(graph.session.uiState, graph.media.state) { connection, media ->
                Snapshot(connection, media.nowPlaying, media.receivedAtMs, media.artworkId, media.artwork != null)
            }
                .distinctUntilChanged()
                .collectLatest { render(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        appGraph.autoStandby.stop()
        if (::mediaSession.isInitialized) mediaSession.release()
        super.onDestroy()
    }

    private suspend fun render(snapshot: Snapshot) {
        val pcName = (snapshot.connection as? ConnectionUiState.Connected)?.desktopName
        val nowPlaying = snapshot.nowPlaying
        if (pcName != null && nowPlaying != null && nowPlaying.status != PlaybackStatus.NONE) {
            val art = artwork(snapshot.artworkId)
            mediaSession.update(nowPlaying, snapshot.receivedAtMs, art)
            notifications.post(notifications.media(nowPlaying, art, pcName, mediaSession.token))
        } else {
            mediaSession.deactivate()
            notifications.post(statusNotification(snapshot.connection))
        }
    }

    private fun statusNotification(state: ConnectionUiState) = when (state) {
        is ConnectionUiState.Connected ->
            notifications.status(getString(R.string.notif_connected, state.desktopName), getString(R.string.notif_connected_text))
        is ConnectionUiState.Connecting, is ConnectionUiState.Reconnecting, is ConnectionUiState.Pairing ->
            notifications.status(getString(R.string.notif_connecting), getString(R.string.notif_waiting_text))
        else -> if (appGraph.session.autoConnectPaused) {
            notifications.status(getString(R.string.notif_paused), getString(R.string.notif_paused_text))
        } else {
            notifications.status(getString(R.string.notif_waiting), getString(R.string.notif_waiting_text))
        }
    }

    /** Decoded once per track and downscaled: system media controls pass it over binder. */
    private suspend fun artwork(artworkId: String?): Bitmap? {
        if (artworkId == null) return null
        artworkCache?.takeIf { it.first == artworkId }?.let { return it.second }
        val bytes = appGraph.media.state.value.artwork?.takeIf { appGraph.media.state.value.artworkId == artworkId } ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeScaled(bytes) }
        artworkCache = artworkId to bitmap
        return bitmap
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_ART_PX && bounds.outHeight / (sample * 2) >= MAX_ART_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
