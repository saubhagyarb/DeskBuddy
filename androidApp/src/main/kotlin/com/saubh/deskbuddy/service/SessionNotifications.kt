package com.saubh.deskbuddy.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.Settings
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
import com.saubh.deskbuddy.ime.KeyboardPickerActivity
import com.saubh.deskbuddy.protocol.NowPlayingCommand
import com.saubh.deskbuddy.protocol.PlaybackStatus

/** The one ongoing notification of [ConnectionService]: connection status, or media controls while the PC plays. */
class SessionNotifications(private val context: Context) {

    companion object {
        const val ID = 1
        const val KEYBOARD_ID = 2
        const val DOCK_ID = 3
        private const val STATUS_CHANNEL = "connection"
        private const val MEDIA_CHANNEL = "now_playing"
        private const val KEYBOARD_CHANNEL = "pc_keyboard"
        private const val DOCK_CHANNEL = "dock"
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
                NotificationChannelCompat.Builder(KEYBOARD_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.notif_channel_keyboard))
                    .setShowBadge(false)
                    .build(),
                NotificationChannelCompat.Builder(DOCK_CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.notif_channel_dock))
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
    /**
     * Heads-up prompt: Android cannot switch keyboards for us, so a tap opens the keyboard picker.
     * The picker only lists enabled keyboards, so until KonneKt Keyboard is enabled the tap opens
     * the system keyboard settings instead.
     */
    fun keyboardPrompt(imeEnabled: Boolean): Notification {
        val intent = if (imeEnabled) {
            Intent(context, KeyboardPickerActivity::class.java)
        } else {
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(context, KEYBOARD_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = if (imeEnabled) R.string.notif_keyboard_text else R.string.notif_keyboard_enable_text
        return NotificationCompat.Builder(context, KEYBOARD_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_deskbuddy)
            .setContentTitle(context.getString(R.string.notif_keyboard_title))
            .setContentText(context.getString(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /**
     * Docked but Android will not let a background app open a screen (no "Display over other apps").
     * The full-screen intent opens standby straight away on a locked phone; otherwise it is a heads-up to tap.
     */
    fun dockPrompt(): Notification {
        val pending = PendingIntent.getActivity(
            context,
            DOCK_ID,
            MainActivity.autoStandbyIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, DOCK_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_deskbuddy)
            .setContentTitle(context.getString(R.string.notif_dock_title))
            .setContentText(context.getString(R.string.notif_dock_text))
            .setContentIntent(pending)
            .setFullScreenIntent(pending, true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun cancel(id: Int) = context.getSystemService(NotificationManager::class.java).cancel(id)

    fun post(notification: Notification, id: Int = ID) {
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) context.getSystemService(NotificationManager::class.java).notify(id, notification)
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
