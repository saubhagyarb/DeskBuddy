package com.saubh.deskbuddy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.saubh.deskbuddy.appGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** After the phone restarts (or the app updates), resume staying connected if a PC is paired. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val graph = context.appGraph
        graph.scope.launch {
            try {
                if (graph.prefs.saved.first().isNotEmpty()) ConnectionService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
