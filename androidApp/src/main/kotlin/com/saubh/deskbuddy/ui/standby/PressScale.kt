package com.saubh.deskbuddy.ui.standby

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/** Shrinks to 0.96 the moment a finger lands (not on release); the scale is read in the layer, so pressing never recomposes. */
@Composable
fun Modifier.pressScale(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
