package com.saubh.deskbuddy.ui.standby.clock

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val NEON_CYAN = Color(0xFF3DF5FF)
private val NEON_PINK = Color(0xFFFF3DCB)
private const val TAIL_DOTS = 14
private const val TAIL_DEGREES = 40f
private const val FRAME_INTERVAL_MS = 33L

/** Glowing analog hands; a seconds comet orbits the rim. Every animated read happens in the draw phase. */
@Composable
fun NeonOrbitFace(time: ClockTime, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val smooth = rememberSmoothSeconds(enabled = !reducedMotion && !LocalInspectionMode.current, fallback = time.second.toFloat())
    Canvas(modifier) {
        val radius = min(size.width, size.height) * 0.42f
        val c = center
        drawCircle(NEON_CYAN.copy(alpha = 0.12f), radius, c, style = Stroke(radius * 0.02f))
        repeat(12) { i ->
            val a = i * 30f
            drawLine(NEON_CYAN.copy(alpha = 0.35f), polar(c, radius * 0.88f, a), polar(c, radius * 0.96f, a), strokeWidth = radius * 0.015f, cap = StrokeCap.Round)
        }
        glowHand(c, ClockAngles.hour(time), radius * 0.5f, radius * 0.045f, NEON_PINK)
        glowHand(c, ClockAngles.minute(time), radius * 0.78f, radius * 0.03f, NEON_CYAN)
        val secondAngle = (if (reducedMotion) time.second.toFloat() else smooth.value) * 6f
        repeat(TAIL_DOTS) { i ->
            val f = i / (TAIL_DOTS - 1f)
            val angle = secondAngle - TAIL_DEGREES * f
            drawCircle(Color.White.copy(alpha = (1f - f) * 0.9f), radius * 0.03f * (1f - f * 0.6f), polar(c, radius, angle))
        }
        drawCircle(NEON_PINK, radius * 0.035f, c)
    }
}

private fun DrawScope.glowHand(c: Offset, degrees: Float, length: Float, width: Float, color: Color) {
    val tip = polar(c, length, degrees)
    drawLine(color.copy(alpha = 0.25f), c, tip, strokeWidth = width * 3.5f, cap = StrokeCap.Round)
    drawLine(color, c, tip, strokeWidth = width, cap = StrokeCap.Round)
}

/** Degrees clockwise from 12 o'clock → a point on the circle. */
private fun polar(c: Offset, r: Float, degrees: Float): Offset {
    val rad = Math.toRadians((degrees - 90f).toDouble())
    return Offset(c.x + r * cos(rad).toFloat(), c.y + r * sin(rad).toFloat())
}

/** Fractional seconds every frame (for a gliding comet); only the Canvas reads it, so nothing recomposes. */
@Composable
private fun rememberSmoothSeconds(enabled: Boolean, fallback: Float): State<Float> =
    produceState(fallback, enabled) {
        if (!enabled) return@produceState
        var last = 0L
        while (true) withFrameMillis { frame ->
            if (frame - last >= FRAME_INTERVAL_MS) {
                last = frame
                value = (System.currentTimeMillis() % 60_000L) / 1_000f
            }
        }
    }
