@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.saubh.deskbuddy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/**
 * M3 Expressive theme shared by the phone and desktop apps: brand color scheme,
 * spring-based expressive motion, the extended shape scale and emphasized type styles.
 */
@Composable
fun DeskBuddyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DeskBuddyColors.Dark else DeskBuddyColors.Light,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(),
        typography = Typography(),
        content = content,
    )
}
