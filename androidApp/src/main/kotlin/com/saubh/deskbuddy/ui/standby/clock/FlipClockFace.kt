package com.saubh.deskbuddy.ui.standby.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.saubh.deskbuddy.R
import kotlin.math.PI

import kotlin.math.sin

/** Split-flap HH and MM cards; on change the top flap folds down onto the new value. */
@Composable
fun FlipClockFace(time: ClockTime, is24h: Boolean, reducedMotion: Boolean, modifier: Modifier = Modifier) {
    val digits = time.digits(is24h)
    // AM/PM sits centred right under the cards; the cards take whatever height the label leaves.
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            val cardHeight = min(maxHeight * 0.8f, ((maxWidth - 20.dp) / 2) / 0.82f)
            val card = Modifier.height(cardHeight).aspectRatio(0.82f)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                FlipCard(digits.substring(0, 2), reducedMotion, card)
                FlipCard(digits.substring(2, 4), reducedMotion, card)
            }
        }
        if (!is24h) {
            Text(
                stringResource(if (time.isPm) R.string.clock_pm else R.string.clock_am),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlipCard(value: String, reducedMotion: Boolean, modifier: Modifier) {
    var shown by remember { mutableStateOf(value) }
    var previous by remember { mutableStateOf(value) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == shown) return@LaunchedEffect
        previous = shown
        shown = value
        if (reducedMotion) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    val density = LocalDensity.current.density
    Box(modifier) {
        // Static halves: new on top, old on the bottom until the lower flap lands on it.
        Half(shown, top = true)
        Half(previous, top = false)
        // Upper flap: the old value folding down (0 → -90°) during the first half of the motion.
        Half(previous, top = true, Modifier.graphicsLayer {
            val p = progress.value
            rotationX = -180f * p.coerceAtMost(0.5f)
            alpha = if (p < 0.5f) 1f else 0f
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            cameraDistance = 12f * density
        }, shade = { progress.value })
        // Lower flap: the new value landing (90° → 0°, a little past with the spring).
        Half(shown, top = false, Modifier.graphicsLayer {
            val p = progress.value
            rotationX = 180f * (1f - p).coerceAtMost(0.5f)
            alpha = if (p >= 0.5f) 1f else 0f
            transformOrigin = TransformOrigin(0.5f, 0.5f)
            cameraDistance = 12f * density
        }, shade = { progress.value })
    }
}

@Composable
private fun Half(text: String, top: Boolean, modifier: Modifier = Modifier, shade: () -> Float = { 1f }) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .drawWithContent {
                val mid = size.height / 2f
                clipRect(top = if (top) 0f else mid, bottom = if (top) mid else size.height) {
                    this@drawWithContent.drawContent()
                    // Darkest mid-fold, as light leaves the flap.
                    drawRect(Color.Black.copy(alpha = 0.35f * sin(shade() * PI).toFloat()))
                }
                drawLine(Color.Black.copy(alpha = 0.6f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2.dp.toPx())
            }
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(LocalDensity.current) { (maxHeight * 0.62f).toSp() }
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    }
}
