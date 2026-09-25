package com.saubh.deskbuddy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.BatteryManager
import android.util.Log
import android.view.OrientationEventListener
import com.saubh.deskbuddy.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AutoStandbyManager(private val context: Context) {

    private val _isAutoStandby = MutableStateFlow(false)
    val isAutoStandby: StateFlow<Boolean> = _isAutoStandby.asStateFlow()

    private var orientationListener: OrientationEventListener? = null
    private var isCharging = false

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isCharging = true
                    startOrientationListener()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCharging = false
                    stopOrientationListener()
                    updateState(false)
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(powerReceiver, filter)

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        isCharging = batteryManager.isCharging

        if (isCharging) {
            startOrientationListener()
        }
    }

    fun stop() {
        context.unregisterReceiver(powerReceiver)
        stopOrientationListener()
    }

    private fun startOrientationListener() {
        if (orientationListener == null) {
            orientationListener = object : OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN) return
                    // Landscape is typically around 90 or 270. We give a +/- 30 degrees tolerance.
                    val isLandscape = (orientation in 60..120) || (orientation in 240..300)
                    if (isCharging) {
                        updateState(isLandscape)
                    }
                }
            }
        }
        orientationListener?.enable()
    }

    private fun stopOrientationListener() {
        orientationListener?.disable()
        orientationListener = null
    }

    private fun updateState(autoStandby: Boolean) {
        if (_isAutoStandby.value == autoStandby) return
        _isAutoStandby.value = autoStandby

        if (autoStandby) {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("auto_standby", true)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("AutoStandby", "Failed to launch MainActivity", e)
            }
        }
    }
}
