package com.saubh.deskbuddy.ui.standby

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One step on press-down, then repeats per [RepeatSchedule] while held; [onRelease] once on lift or cancel.
 * Position changes are consumed while the finger is down so a parent pager cannot start scrolling mid-hold.
 * Accessibility services get a plain click (one step + release). When [enabled] is false nothing is handled.
 */
@Composable
fun Modifier.repeatingPress(
    interaction: MutableInteractionSource,
    onStep: () -> Unit,
    onRelease: () -> Unit,
    enabled: Boolean = true,
): Modifier {
    val step by rememberUpdatedState(onStep)
    val release by rememberUpdatedState(onRelease)
    if (!enabled) return semantics {
        role = Role.Button
        disabled()
    }
    return semantics {
        role = Role.Button
        onClick { step(); release(); true }
    }.pointerInput(interaction) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume() // handled: the standby screen must not read this press as a blank-area tap
                val press = PressInteraction.Press(down.position)
                interaction.tryEmit(press)
                step()
                val repeater = launch {
                    var repeats = 0
                    while (isActive) {
                        delay(RepeatSchedule.nextDelay(repeats, SystemClock.uptimeMillis() - down.uptimeMillis))
                        step()
                        repeats++
                    }
                }
                var lifted = false
                try {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            lifted = true
                            break
                        }
                        if (change.isConsumed) break
                        change.consume()
                    }
                } finally {
                    repeater.cancel()
                    interaction.tryEmit(if (lifted) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                    release()
                }
            }
        }
    }
}
