package com.saubh.deskbuddy.ui

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * True when the window is at least medium width (600dp): phones in landscape, unfolded
 * foldables, tablets and desktop windows. Screens use it to switch to side-by-side layouts.
 */
@Composable
fun isWideWindow(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
