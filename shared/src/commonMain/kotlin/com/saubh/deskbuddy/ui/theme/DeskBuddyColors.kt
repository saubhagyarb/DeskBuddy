package com.saubh.deskbuddy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * DeskBuddy brand palette: indigo primary, lavender-grey secondary, coral tertiary
 * for the one accent that must stand out (connection status, destructive confirm).
 * Container "on" colors use tone 30 in light mode, as M3 Expressive does.
 */
object DeskBuddyColors {

    val Light = lightColorScheme(
        primary = Color(0xFF4F46C8),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE2DFFF),
        onPrimaryContainer = Color(0xFF2D22A6),
        inversePrimary = Color(0xFFC2C0FF),
        secondary = Color(0xFF5D5C72),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE2E0F9),
        onSecondaryContainer = Color(0xFF464559),
        tertiary = Color(0xFFB2405A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD9DE),
        onTertiaryContainer = Color(0xFF8F2F45),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFFCF8FF),
        onBackground = Color(0xFF1B1B21),
        surface = Color(0xFFFCF8FF),
        onSurface = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE4E1EC),
        onSurfaceVariant = Color(0xFF46464F),
        outline = Color(0xFF777680),
        outlineVariant = Color(0xFFC7C5D0),
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF2EFF7),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFFDCD9E0),
        surfaceBright = Color(0xFFFCF8FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF6F2FA),
        surfaceContainer = Color(0xFFF0ECF4),
        surfaceContainerHigh = Color(0xFFEAE7EF),
        surfaceContainerHighest = Color(0xFFE4E1E9),
    )

    val Dark = darkColorScheme(
        primary = Color(0xFFC2C0FF),
        onPrimary = Color(0xFF1B0F98),
        primaryContainer = Color(0xFF352BAF),
        onPrimaryContainer = Color(0xFFE2DFFF),
        inversePrimary = Color(0xFF4F46C8),
        secondary = Color(0xFFC6C4DD),
        onSecondary = Color(0xFF2F2E42),
        secondaryContainer = Color(0xFF464559),
        onSecondaryContainer = Color(0xFFE2E0F9),
        tertiary = Color(0xFFFFB2BE),
        onTertiary = Color(0xFF67102D),
        tertiaryContainer = Color(0xFF8F2F45),
        onTertiaryContainer = Color(0xFFFFD9DE),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF131318),
        onBackground = Color(0xFFE4E1E9),
        surface = Color(0xFF131318),
        onSurface = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF46464F),
        onSurfaceVariant = Color(0xFFC7C5D0),
        outline = Color(0xFF918F9A),
        outlineVariant = Color(0xFF46464F),
        inverseSurface = Color(0xFFE4E1E9),
        inverseOnSurface = Color(0xFF303036),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFF131318),
        surfaceBright = Color(0xFF39393F),
        surfaceContainerLowest = Color(0xFF0E0E13),
        surfaceContainerLow = Color(0xFF1B1B21),
        surfaceContainer = Color(0xFF1F1F25),
        surfaceContainerHigh = Color(0xFF2A292F),
        surfaceContainerHighest = Color(0xFF35343A),
    )
}
