package com.saubh.deskbuddy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.saubh.deskbuddy.appGraph
import com.saubh.deskbuddy.protocol.MediaAction
import com.saubh.deskbuddy.protocol.MediaCommand
import kotlinx.coroutines.launch

/** Transport buttons on the media notification (Android 12 and older draw them from the notification's actions). */
class MediaActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PREVIOUS = "com.saubh.deskbuddy.action.PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.saubh.deskbuddy.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.saubh.deskbuddy.action.NEXT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_PREVIOUS -> MediaAction.PREVIOUS
            ACTION_PLAY_PAUSE -> MediaAction.PLAY_PAUSE
            ACTION_NEXT -> MediaAction.NEXT
            else -> return
        }
        val graph = context.appGraph
        graph.scope.launch { graph.session.send(MediaCommand(action)) }
    }
}
