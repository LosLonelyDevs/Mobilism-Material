package org.mobilism.material.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Standard-contrast color roles from GAYERFyTV.
internal val GayerfyTokyoNightColors = darkColorScheme(
    primary = Color(0xFF7AA2F7), onPrimary = Color(0xFF16161E),
    primaryContainer = Color(0xFF405179), onPrimaryContainer = Color(0xFFC0CAF5),
    secondary = Color(0xFF7DCFFF), onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF41647C), onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9ECE6A), onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF4F633E), onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFF7768E), onError = Color(0xFF16161E),
    errorContainer = Color(0xFF743E4D), onErrorContainer = Color(0xFFC0CAF5),
    background = Color(0xFF16161E), onBackground = Color(0xFFC0CAF5),
    surface = Color(0xFF1A1B26), onSurface = Color(0xFFC0CAF5),
    surfaceVariant = Color(0xFF24283B), onSurfaceVariant = Color(0xFFA9B1D6),
    outline = Color(0xFF414868), outlineVariant = Color(0xFF2F344D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFC0CAF5), inverseOnSurface = Color(0xFF16161E),
    inversePrimary = Color(0xFF415684),
    surfaceDim = Color(0xFF111117), surfaceBright = Color(0xFF2C3148),
    surfaceContainerLowest = Color(0xFF111117), surfaceContainerLow = Color(0xFF181822),
    surfaceContainer = Color(0xFF1A1B26), surfaceContainerHigh = Color(0xFF212435),
    surfaceContainerHighest = Color(0xFF292D43),
)

internal val GayerfyFrappeColors = darkColorScheme(
    primary = Color(0xFF8CAAEE), onPrimary = Color(0xFF232634),
    primaryContainer = Color(0xFF536187), onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFCA9EE6), onSecondary = Color(0xFF232634),
    secondaryContainer = Color(0xFF6D5C83), onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF81C8BE), onTertiary = Color(0xFF232634),
    tertiaryContainer = Color(0xFF4E6E73), onTertiaryContainer = Color(0xFFFFFFFF),
    error = Color(0xFFE78284), onError = Color(0xFF232634),
    errorContainer = Color(0xFF79505A), onErrorContainer = Color(0xFFFFFFFF),
    background = Color(0xFF292C3C), onBackground = Color(0xFFC6D0F5),
    surface = Color(0xFF303446), onSurface = Color(0xFFC6D0F5),
    surfaceVariant = Color(0xFF414559), onSurfaceVariant = Color(0xFFB5BFE2),
    outline = Color(0xFF737994), outlineVariant = Color(0xFF51576D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFC6D0F5), inverseOnSurface = Color(0xFF292C3C),
    inversePrimary = Color(0xFF4A5A7D),
    surfaceDim = Color(0xFF1F212E), surfaceBright = Color(0xFF4F546A),
    surfaceContainerLowest = Color(0xFF1F212E), surfaceContainerLow = Color(0xFF2C3040),
    // One step darker keeps small blue headings above 4.5:1 contrast.
    surfaceContainer = Color(0xFF303446), surfaceContainerHigh = Color(0xFF3B3F52),
    surfaceContainerHighest = Color(0xFF484D61),
)
