package com.saubh.deskbuddy.ui.media

import java.util.Locale

/** "m:ss" or "h:mm:ss"; "--:--" for unknown. */
fun formatClock(ms: Long): String {
    if (ms < 0) return "--:--"
    val total = ms / 1_000
    val h = total / 3_600
    val m = (total % 3_600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
