package com.saubh.deskbuddy.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The two things only the user can allow before docking can bring the app up on its own:
 * "Display over other apps" (Android 10+ blocks background activity starts otherwise) and no
 * battery optimisation (Android 12+ blocks starting the connection service from the background).
 */
object DockPermissions {

    fun canOpenFromBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)

    fun ignoresBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun overlaySettings(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    /** The direct "Allow" dialog; [batterySettingsList] is the fallback where an OEM removed it. */
    fun batteryOptimizationRequest(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    fun batterySettingsList(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
