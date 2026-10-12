package com.saubh.deskbuddy.ui.standby.clock

/** Hand angles in degrees, clockwise from 12. */
object ClockAngles {
    fun hour(time: ClockTime): Float = (time.hour % 12 + time.minute / 60f) * 30f
    fun minute(time: ClockTime): Float = (time.minute + time.second / 60f) * 6f
}
