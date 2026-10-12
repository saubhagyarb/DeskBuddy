package com.saubh.deskbuddy.ui.standby.clock

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Pins the clock to one time (screenshot tests); null, the default, ticks with the system clock. */
val LocalClockTimeOverride = staticCompositionLocalOf<ClockTime?> { null }

/** Ticks on each second boundary, so every face changes digits on the same frame as the system clock. */
@Composable
fun rememberClockTime(): State<ClockTime> {
    val fixed = LocalClockTimeOverride.current
    val time = remember(fixed) { mutableStateOf(fixed ?: ClockTime.now()) }
    LaunchedEffect(fixed) {
        if (fixed != null) return@LaunchedEffect
        while (true) {
            time.value = ClockTime.now()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    return time
}

@Composable
fun rememberIs24Hour(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}

/** Shifts the whole face a little every minute (±8 dp random walk) against OLED burn-in. */
@Composable
fun rememberBurnInOffset(): State<IntOffset> {
    val density = LocalDensity.current
    val offset = remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(density) {
        var dp = 0 to 0
        while (true) {
            delay(60_000L)
            dp = BurnInWalk.next(dp.first, dp.second)
            offset.value = with(density) { IntOffset(dp.first.dp.roundToPx(), dp.second.dp.roundToPx()) }
        }
    }
    return offset
}
