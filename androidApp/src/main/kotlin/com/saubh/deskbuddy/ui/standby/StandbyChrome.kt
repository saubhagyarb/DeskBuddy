package com.saubh.deskbuddy.ui.standby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.protocol.PowerAction
import com.saubh.deskbuddy.ui.theme.KonnektIcons
import com.saubh.deskbuddy.ui.theme.KonnektTheme

private const val CHROME_ENTER_MS = 220
private const val CHROME_EXIT_MS = 160

/** Always-dark standby surface: paints the background and gives every Text a light content colour. */
@Composable
fun StandbySurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    KonnektTheme(darkTheme = true) {
        Surface(
            modifier,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            content = content,
        )
    }
}

/** The screen edge a piece of chrome slides in from (and back out to). */
enum class ChromeEdge { START, TOP, BOTTOM, TOP_END, NONE }

private fun <T> enterSpec(): FiniteAnimationSpec<T> = tween(CHROME_ENTER_MS, easing = StrongEaseOut)
private fun <T> exitSpec(): FiniteAnimationSpec<T> = tween(CHROME_EXIT_MS, easing = StrongEaseOut)

/** Fade plus a quarter-size slide from [edge]; a plain fade under reduced motion. */
fun chromeEnter(edge: ChromeEdge, reducedMotion: Boolean): EnterTransition {
    val fade = fadeIn(enterSpec())
    if (reducedMotion) return fade
    return fade + when (edge) {
        ChromeEdge.START -> slideInHorizontally(enterSpec()) { -it / 4 }
        ChromeEdge.TOP -> slideInVertically(enterSpec()) { -it / 4 }
        ChromeEdge.BOTTOM -> slideInVertically(enterSpec()) { it / 4 }
        ChromeEdge.TOP_END -> slideIn(enterSpec()) { IntOffset(it.width / 4, -it.height / 4) }
        ChromeEdge.NONE -> EnterTransition.None
    }
}

/** The reverse of [chromeEnter]. */
fun chromeExit(edge: ChromeEdge, reducedMotion: Boolean): ExitTransition {
    val fade = fadeOut(exitSpec())
    if (reducedMotion) return fade
    return fade + when (edge) {
        ChromeEdge.START -> slideOutHorizontally(exitSpec()) { -it / 4 }
        ChromeEdge.TOP -> slideOutVertically(exitSpec()) { -it / 4 }
        ChromeEdge.BOTTOM -> slideOutVertically(exitSpec()) { it / 4 }
        ChromeEdge.TOP_END -> slideOut(exitSpec()) { IntOffset(it.width / 4, -it.height / 4) }
        ChromeEdge.NONE -> ExitTransition.None
    }
}

/**
 * One floating piece of chrome. Hidden chrome leaves composition, so it can neither be tapped nor swallow taps.
 * Taps that land on the chrome itself (its padding, a disabled button) never count as a blank-area tap.
 */
@Composable
fun ChromeItem(
    visible: Boolean,
    edge: ChromeEdge,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = chromeEnter(edge, reducedMotion),
        exit = chromeExit(edge, reducedMotion),
    ) {
        Box(Modifier.consumeAllTaps()) { content() }
    }
}

/** Marks every pointer change inside as handled (Main pass, after the children), so the screen's blank-tap check skips it. */
private fun Modifier.consumeAllTaps(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
    }
}

/**
 * Calls [onPress] on every press and [onBlankTap] on a tap nobody else handled. Children see the Main pass
 * before this parent, so a button, key, chip or slider has already consumed its down or up by the time this
 * looks; a pager consumes the moves of a swipe, and anything past touch slop is not a tap either.
 */
@Composable
fun Modifier.blankTaps(onPress: () -> Unit, onBlankTap: () -> Unit): Modifier {
    val press by rememberUpdatedState(onPress)
    val tap by rememberUpdatedState(onBlankTap)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            press()
            var blank = !down.isConsumed
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.size > 1) blank = false
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) blank = false
                if (!change.pressed) {
                    if (blank) tap()
                    break
                }
            }
        }
    }
}

/** Power (Restart / Shut down) and close: a column on the right side. */
@Composable
fun StandbyTopActions(
    connected: Boolean,
    onPower: (PowerAction) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing), horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = { onPower(PowerAction.SHUTDOWN) }, enabled = connected, modifier = Modifier.size(64.dp)) {
            Icon(KonnektIcons.Power, contentDescription = stringResource(R.string.power_shutdown), modifier = Modifier.size(32.dp))
        }
        FilledTonalIconButton(onClick = { onPower(PowerAction.RESTART) }, enabled = connected, modifier = Modifier.size(64.dp)) {
            Icon(KonnektIcons.RestartAlt, contentDescription = stringResource(R.string.power_restart), modifier = Modifier.size(32.dp))
        }
        FilledTonalIconButton(onClick = onExit, modifier = Modifier.size(64.dp)) {
            Icon(KonnektIcons.Close, contentDescription = stringResource(R.string.cd_exit_standby), modifier = Modifier.size(32.dp))
        }
    }
}
