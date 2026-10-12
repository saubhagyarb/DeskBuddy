package com.saubh.deskbuddy.ui.standby.clock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val WARM = listOf(Color(0xFFFF6B6B), Color(0xFFFFB86B), Color(0xFFB06BFF))
private val BLEED = 12.dp

/** Chunky rounded digits that squash out and pop in over a slowly drifting warm gradient. */
@Composable
fun BubbleMorphFace(time: ClockTime, is24h: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val drift = rememberDrift(reducedMotion)
    BoxWithConstraints(
        modifier.drawBehind {
            val t = drift.value
            // Overdraw by the burn-in walk range, so the gradient still reaches every screen edge while the face is shifted.
            val bleed = BLEED.toPx()
            drawRect(
                Brush.linearGradient(WARM, start = Offset(size.width * t, 0f), end = Offset(size.width * (1f - t), size.height)),
                topLeft = Offset(-bleed, -bleed),
                size = Size(size.width + 2 * bleed, size.height + 2 * bleed),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(LocalDensity.current) { (minOf(maxHeight * 0.5f, maxWidth / 3.4f)).toSp() }
        val style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
        val digits = time.digits(is24h)
        Row(verticalAlignment = Alignment.CenterVertically) {
            digits.forEachIndexed { index, c ->
                if (index == 2) Text(":", style = style.copy(fontSize = fontSize * 0.8f))
                BubbleDigit(c, style, reducedMotion)
            }
        }
    }
}

@Composable
private fun BubbleDigit(digit: Char, style: TextStyle, reducedMotion: Boolean) {
    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            } else {
                (scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0f) + fadeIn()) togetherWith
                    (scaleOut(tween(180), targetScale = 0f) + fadeOut(tween(180))) using SizeTransform(clip = false)
            }
        },
        label = "bubble",
    ) { Text(it.toString(), style = style) }
}

/** 0→1→0 over two minutes, read only while drawing; frozen at the midpoint under reduced motion. */
@Composable
private fun rememberDrift(reducedMotion: Boolean): State<Float> {
    if (reducedMotion) return remember { mutableFloatStateOf(0.5f) }
    return produceState(0f) {
        val start = System.currentTimeMillis()
        while (true) {
            val phase = ((System.currentTimeMillis() - start) % 120_000L) / 60_000f
            value = if (phase <= 1f) phase else 2f - phase
            delay(100L)
        }
    }
}
