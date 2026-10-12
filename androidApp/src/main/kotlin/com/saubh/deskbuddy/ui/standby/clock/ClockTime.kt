package com.saubh.deskbuddy.ui.standby.clock

import java.util.Calendar
import java.util.Locale

/** Wall-clock time for the faces. Calendar, not java.time: minSdk 24 has no desugaring here. */
data class ClockTime(val hour: Int, val minute: Int, val second: Int) {
    val isPm: Boolean get() = hour >= 12

    /** ASCII digits regardless of the default locale (the LED font only knows 0-9). */
    fun digits(is24h: Boolean): String {
        val h = if (is24h) hour else (hour % 12).let { if (it == 0) 12 else it }
        return String.format(Locale.ROOT, "%02d%02d", h, minute)
    }

    companion object {
        fun now(calendar: Calendar = Calendar.getInstance()): ClockTime =
            ClockTime(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
    }
}
