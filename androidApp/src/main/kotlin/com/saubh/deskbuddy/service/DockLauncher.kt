package com.saubh.deskbuddy.service

import android.content.Context
import com.saubh.deskbuddy.MainActivity

/**
 * Brings the app up in standby from the background. Android 10+ silently ignores a background
 * `startActivity` unless the user allowed "Display over other apps", so without it the dock
 * notification (full-screen intent) is posted instead.
 */
class DockLauncher(private val context: Context) {

    private val notifications by lazy { SessionNotifications(context) }

    fun open() {
        val opened = DockPermissions.canOpenFromBackground(context) &&
            runCatching { context.startActivity(MainActivity.autoStandbyIntent(context)) }.isSuccess
        if (!opened) notifications.post(notifications.dockPrompt(), SessionNotifications.DOCK_ID)
    }

    /** Undocked, or the app is on screen anyway. */
    fun dismiss() = notifications.cancel(SessionNotifications.DOCK_ID)
}
