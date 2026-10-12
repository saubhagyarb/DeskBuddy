package com.saubh.deskbuddy.ui.standby.clock

import android.content.Context
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.min

private val LED_ON = Color(0xFFFFB000)
private const val WAVE_MS_PER_COLUMN = 20
private val DOTS_CLEARANCE = 40.dp

/** Retro LED dots; on change, columns switch to the new digits in a left-to-right wave. Date and battery underneath. */
@Composable
fun DotMatrixFace(time: ClockTime, is24h: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val digits = time.digits(is24h)
    val text = "${digits.substring(0, 2)}:${digits.substring(2)}"
    var previous by remember { mutableStateOf(DotMatrixFont.columns(text)) }
    var current by remember { mutableStateOf(previous) }
    val wave = remember { Animatable(1f) }
    var shownText by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (text == shownText) return@LaunchedEffect
        shownText = text
        val next = DotMatrixFont.columns(text)
        previous = current
        current = next
        if (reducedMotion) return@LaunchedEffect
        wave.snapTo(0f)
        wave.animateTo(1f, tween(next.size * WAVE_MS_PER_COLUMN, easing = LinearEasing))
    }
    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current
    val battery by produceState(remember(context) { batteryPercent(context, inspecting) }) {
        while (true) {
            delay(60_000L)
            value = batteryPercent(context, inspecting)
        }
    }
    // Vertical padding keeps the date and battery clear of the face dots; the matrix shrinks before anything overlaps.
    Column(
        modifier.padding(vertical = DOTS_CLEARANCE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Canvas(Modifier.weight(1f, fill = false).fillMaxWidth(0.85f).aspectRatio(current.size / 7f)) {
            val cols = current.size
            val cell = min(size.width / cols, size.height / 7f)
            val dot = cell * 0.38f
            // Centred, for when the height (not the width) limits the cell size.
            val origin = Offset((size.width - cols * cell) / 2, (size.height - 7 * cell) / 2)
            val switched = wave.value * cols
            for (c in 0 until cols) {
                val column = if (c < switched) current[c] else previous.getOrElse(c) { current[c] }
                for (r in 0 until 7) {
                    val center = origin + Offset(c * cell + cell / 2, r * cell + cell / 2)
                    drawCircle(if (column[r]) LED_ON else LED_ON.copy(alpha = 0.08f), dot, center)
                }
            }
        }
        val date = remember(time.hour, time.minute, inspecting) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(if (inspecting) Date(PREVIEW_DATE_MS) else Date()) }
        Text(date, style = MaterialTheme.typography.titleMedium, color = LED_ON.copy(alpha = 0.8f))
        if (battery >= 0) Text(stringResource(R.string.standby_battery, battery), style = MaterialTheme.typography.labelLarge, color = LED_ON.copy(alpha = 0.6f))
    }
}

private fun batteryPercent(context: Context, inspecting: Boolean): Int = if (inspecting) -1 else
    (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

/** A fixed date for previews and screenshots, so the reference image does not change daily. */
private const val PREVIEW_DATE_MS = 1_760_000_000_000L
