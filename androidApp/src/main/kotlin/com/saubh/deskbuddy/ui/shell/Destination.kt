package com.saubh.deskbuddy.ui.shell

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.saubh.deskbuddy.R
import com.saubh.deskbuddy.ui.theme.KonnektIcons

/** Top-level destinations of the connected app, in navigation order. */
enum class Destination(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, KonnektIcons.Home),
    MEDIA(R.string.nav_media, KonnektIcons.MusicNote),
    APPS(R.string.nav_apps, KonnektIcons.Apps),
    SHARE(R.string.nav_share, KonnektIcons.ContentPaste),
    KEYBOARD(R.string.nav_keyboard, KonnektIcons.Keyboard),
}
