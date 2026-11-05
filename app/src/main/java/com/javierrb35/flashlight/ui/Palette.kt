package com.javierrb35.flashlight

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Two layers of color:
 *  - Splash brand colors (mascot palette): navy, yellow, coral, turquoise.
 *    They belong to the approved splash design and stay untouched.
 *  - App identity: warm "torch amber" light over warm near-black — the natural
 *    habitat of a flashlight. Dark is the default; light and system themes are
 *    available, plus optional Material You dynamic color on Android 12+.
 */

// ---- Splash brand colors (do not change) ----
val Navy = Color(0xFF102045)
val NavyDeep = Color(0xFF0C1C3E)
val NavySurface = Color(0xFF16274B)
val Ink = Color(0xFF172746)
val Yellow = Color(0xFFFCD048)
val Coral = Color(0xFFFC613A)
val Turquoise = Color(0xFF3FB7B3)
val Canvas = Color(0xFFF4F7FB)
val Muted = Color(0xFF606C83)
val Selector = Color(0xFFE5EDF5)

// ---- App identity: torch amber on warm dark ----

// Light theme
internal val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDF9E),
    onPrimaryContainer = Color(0xFF261A00),
    secondary = Color(0xFF6C5C3F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E0BB),
    onSecondaryContainer = Color(0xFF241A04),
    tertiary = Color(0xFF3F6B57),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1EBD1),
    onTertiaryContainer = Color(0xFF002115),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F0),
    onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFEDE2CF),
    onSurfaceVariant = Color(0xFF4D4639),
    surfaceDim = Color(0xFFE0D9CC),
    surfaceBright = Color(0xFFFFF8F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF2E4),
    surfaceContainer = Color(0xFFF4ECDF),
    surfaceContainerHigh = Color(0xFFEEE7D9),
    surfaceContainerHighest = Color(0xFFE8E1D3),
    outline = Color(0xFF7F7667),
    outlineVariant = Color(0xFFD1C5B4),
    inverseSurface = Color(0xFF343027),
    inverseOnSurface = Color(0xFFF8F0E2),
    inversePrimary = Color(0xFFF5BE48),
    scrim = Color(0xFF000000),
)

// Dark theme — the natural habitat of a flashlight app, and the default.
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFF5BE48),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = Color(0xFFD9C4A0),
    onSecondary = Color(0xFF382E15),
    secondaryContainer = Color(0xFF4F452B),
    onSecondaryContainer = Color(0xFFF5E0BB),
    tertiary = Color(0xFFA5CFB5),
    onTertiary = Color(0xFF0B3826),
    tertiaryContainer = Color(0xFF244F3B),
    onTertiaryContainer = Color(0xFFC1EBD1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17130B),
    onBackground = Color(0xFFEBE1D4),
    surface = Color(0xFF17130B),
    onSurface = Color(0xFFEBE1D4),
    surfaceVariant = Color(0xFF4D4639),
    onSurfaceVariant = Color(0xFFD1C5B4),
    surfaceDim = Color(0xFF17130B),
    surfaceBright = Color(0xFF3C382E),
    surfaceContainerLowest = Color(0xFF120F08),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF241F16),
    surfaceContainerHigh = Color(0xFF2E2920),
    surfaceContainerHighest = Color(0xFF3A342A),
    outline = Color(0xFFA89F8D),
    outlineVariant = Color(0xFF4D4639),
    inverseSurface = Color(0xFFEBE1D4),
    inverseOnSurface = Color(0xFF343027),
    inversePrimary = Color(0xFF7A5900),
    scrim = Color(0xFF000000),
)
