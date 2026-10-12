package com.saubh.deskbuddy.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * KonneKt brand palette: teal primary for the link between phone and PC, slate-teal secondary,
 * and warm amber tertiary for the few things that must stand out (power actions, "PC is typing").
 * Container "on" colors use tone 30 in light mode, as M3 Expressive does. Surface containers step
 * monotonically in tone so tonal elevation stays readable in both themes.
 */
object KonnektColors {

    val Light = lightColorScheme(
        primary = Color(0xFF006A65),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9DF2EA),
        onPrimaryContainer = Color(0xFF00504C),
        inversePrimary = Color(0xFF80D5CE),
        secondary = Color(0xFF4A6360),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCE8E4),
        onSecondaryContainer = Color(0xFF334B48),
        tertiary = Color(0xFF8B5000),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDCBE),
        onTertiaryContainer = Color(0xFF6A3B00),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        background = Color(0xFFF4FBF9),
        onBackground = Color(0xFF161D1C),
        surface = Color(0xFFF4FBF9),
        onSurface = Color(0xFF161D1C),
        surfaceVariant = Color(0xFFDAE5E2),
        onSurfaceVariant = Color(0xFF3F4947),
        outline = Color(0xFF6F7977),
        outlineVariant = Color(0xFFBEC9C6),
        inverseSurface = Color(0xFF2B3231),
        inverseOnSurface = Color(0xFFECF2F0),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFFD5DBDA),
        surfaceBright = Color(0xFFF4FBF9),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F3),
        surfaceContainer = Color(0xFFE9EFED),
        surfaceContainerHigh = Color(0xFFE3EAE8),
        surfaceContainerHighest = Color(0xFFDDE4E2),
    )

    val Dark = darkColorScheme(
        primary = Color(0xFF80D5CE),
        onPrimary = Color(0xFF003734),
        primaryContainer = Color(0xFF00504C),
        onPrimaryContainer = Color(0xFF9DF2EA),
        inversePrimary = Color(0xFF006A65),
        secondary = Color(0xFFB0CCC8),
        onSecondary = Color(0xFF1C3532),
        secondaryContainer = Color(0xFF334B48),
        onSecondaryContainer = Color(0xFFCCE8E4),
        tertiary = Color(0xFFFFB870),
        onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF6A3B00),
        onTertiaryContainer = Color(0xFFFFDCBE),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E1514),
        onBackground = Color(0xFFDDE4E2),
        surface = Color(0xFF0E1514),
        onSurface = Color(0xFFDDE4E2),
        surfaceVariant = Color(0xFF3F4947),
        onSurfaceVariant = Color(0xFFBEC9C6),
        outline = Color(0xFF899390),
        outlineVariant = Color(0xFF3F4947),
        inverseSurface = Color(0xFFDDE4E2),
        inverseOnSurface = Color(0xFF2B3231),
        scrim = Color(0xFF000000),
        surfaceDim = Color(0xFF0E1514),
        surfaceBright = Color(0xFF343B3A),
        surfaceContainerLowest = Color(0xFF090F0F),
        surfaceContainerLow = Color(0xFF161D1C),
        surfaceContainer = Color(0xFF1A2120),
        surfaceContainerHigh = Color(0xFF252B2A),
        surfaceContainerHighest = Color(0xFF2F3635),
    )
}
