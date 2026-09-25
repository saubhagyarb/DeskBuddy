package com.saubh.deskbuddy.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saubh.deskbuddy.MainActivity
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus

/** The one ongoing notification of [ConnectionService]: connection status, or media controls while the PC plays. */
class SessionNotifications(private val context: Context) {

    companion object {
        const val ID = 1
        private const val STATUS_CHANNEL = "connection"
        private const val MEDIA_CHANNEL = "now_playing"
    }

    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(STATUS_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notif_channel_connection))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(MEDIA_CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.notif_channel_media))
                    .setShowBadge(false)
                    .build(),
            ),
        )
    }

    fun status(title: String, text: String): Notification =
        NotificationCompat.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_deskbuddy)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun media(nowPlaying: NowPlayingCommand, artwork: Bitmap?, pcName: String, token: MediaSession.Token): Notification {
        val playing = nowPlaying.status == PlaybackStatus.PLAYING
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, MEDIA_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        return builder
            .setSmallIcon(R.drawable.ic_stat_deskbuddy)
            .setContentTitle(nowPlaying.title ?: context.getString(R.string.media_unknown_title))
            .setContentText(listOfNotNull(nowPlaying.artist, nowPlaying.album).joinToString(context.getString(R.string.separator_dot)))
            .setSubText(listOfNotNull(nowPlaying.appName, pcName).joinToString(context.getString(R.string.separator_dot)))
            .setLargeIcon(artwork)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(R.drawable.ic_notif_previous, R.string.cd_previous, MediaActionReceiver.ACTION_PREVIOUS))
            .addAction(
                if (playing) {
                    action(R.drawable.ic_notif_pause, R.string.cd_pause, MediaActionReceiver.ACTION_PLAY_PAUSE)
                } else {
                    action(R.drawable.ic_notif_play, R.string.cd_play, MediaActionReceiver.ACTION_PLAY_PAUSE)
                },
            )
            .addAction(action(R.drawable.ic_notif_next, R.string.cd_next, MediaActionReceiver.ACTION_NEXT))
            .setStyle(Notification.MediaStyle().setMediaSession(token).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    /** Posting needs the notification permission on Android 13+; without it the service still runs. */
    fun post(notification: Notification) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) context.getSystemService(NotificationManager::class.java).notify(ID, notification)
    }

    private fun action(icon: Int, label: Int, intentAction: String): Notification.Action {
        val intent = Intent(context, MediaActionReceiver::class.java).setAction(intentAction)
        val pending = PendingIntent.getBroadcast(context, intentAction.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Action.Builder(Icon.createWithResource(context, icon), context.getString(label), pending).build()
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
