package com.saubh.deskbuddy.ui.standby

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.saubh.deskbuddy.ui.standby.clock.ClockMode

/** "Strong ease-out": quick to respond, long gentle settle. */
val StrongEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
const val PAGE_ENTRY_MS = 220
private const val PAGE_ENTRY_SCALE = 0.97f

/** Fade (and, unless motion is reduced, a 0.97 → 1 scale) of the page a mode change brought in. */
class PageEntry(val page: Int, val progress: Animatable<Float, AnimationVector1D>, val reducedMotion: Boolean)

/**
 * One page per mode, each filling the screen. PC pages keep [contentPadding] clear for the floating chrome (fixed,
 * so toggling the chrome never reflows them); the clock page is full-bleed and the chrome floats over it.
 */
@Composable
fun StandbyPages(
    pager: PagerState,
    state: StandbyUiState,
    actions: StandbyActions,
    wide: Boolean,
    entry: PageEntry,
    contentPadding: PaddingValues,
    clockDotsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    VerticalPager(
        state = pager,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pager,
            snapAnimationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
        ),
        modifier = modifier,
    ) { page ->
        val mode = StandbyMode.entries[page]
        val entryModifier = if (page == entry.page) Modifier.pageEntry(entry) else Modifier
        val pageModifier = entryModifier.fillMaxSize().padding(contentPadding)
        when {
            mode == StandbyMode.CLOCK -> ClockMode(state.clockFace, actions::onSelectFace, entryModifier.fillMaxSize(), showDots = clockDotsVisible)
            !state.connected -> Box(pageModifier, contentAlignment = Alignment.Center) {
                DisconnectedCard(state.pcName, state.reconnectAttempt)
            }
            mode == StandbyMode.MEDIA_APPS -> MediaAppsMode(state, actions, wide, pageModifier)
            mode == StandbyMode.MULTIMEDIA -> MultimediaMode(state, actions, wide, pageModifier)
            else -> DevelopmentMode(state, actions, wide, pageModifier)
        }
    }
}

private fun Modifier.pageEntry(entry: PageEntry): Modifier = graphicsLayer {
    val p = entry.progress.value
    alpha = p
    if (!entry.reducedMotion) {
        val scale = PAGE_ENTRY_SCALE + (1f - PAGE_ENTRY_SCALE) * p
        scaleX = scale
        scaleY = scale
    }
}
