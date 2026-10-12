package com.saubh.deskbuddy.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Whether DeskBuddy Keyboard is enabled / the current keyboard, and the settings shortcut. */
object ImeStatus {
    fun isEnabled(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == context.packageName }

    fun isCurrent(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
            .startsWith(context.packageName + "/")

    fun openSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
