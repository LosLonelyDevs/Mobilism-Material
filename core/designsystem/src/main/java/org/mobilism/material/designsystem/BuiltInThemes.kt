package org.mobilism.material.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// Semantic colors come from the supplied palette files. Containers keep each palette's accents.
private fun paletteTheme(
    id: String, name: String, dark: Boolean,
    background: Color, surface: Color, surfaceAlt: Color, text: Color, subtext: Color, outline: Color,
    primary: Color, onPrimary: Color, success: Color, onSuccess: Color,
    warning: Color, onWarning: Color, error: Color, onError: Color, info: Color, onInfo: Color,
): ThemeDefinition {
    val surfaces = listOf(background, surface, surfaceAlt)
    fun readable(accent: Color, minimum: Float = 4.5f): Color = (0..20)
        .map { lerp(accent, text, it / 20f) }
        .firstOrNull { candidate -> surfaces.all { contrast(candidate, it) >= minimum } } ?: text
    fun foreground(preferred: Color, fill: Color): Color = if (contrast(preferred, fill) >= 4.5f) preferred
        else listOf(Color.Black, Color.White).maxBy { contrast(it, fill) }
    val primaryTone = readable(primary)
    val secondaryTone = readable(info)
    val tertiaryTone = readable(warning)
    val successTone = readable(success, 3f)
    val primaryText = foreground(onPrimary, primaryTone)
    val secondaryText = foreground(onInfo, secondaryTone)
    val tertiaryText = foreground(onWarning, tertiaryTone)
    val mix = if (dark) 0.22f else 0.12f
    val colors = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primaryTone, onPrimary = primaryText,
        primaryContainer = lerp(surface, primary, mix), onPrimaryContainer = text, inversePrimary = primary,
        secondary = secondaryTone, onSecondary = secondaryText,
        secondaryContainer = lerp(surface, info, mix), onSecondaryContainer = text,
        tertiary = tertiaryTone, onTertiary = tertiaryText,
        tertiaryContainer = lerp(surface, warning, mix), onTertiaryContainer = text,
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = surfaceAlt, onSurfaceVariant = subtext,
        surfaceTint = primary, inverseSurface = text, inverseOnSurface = background,
        error = error, onError = onError,
        errorContainer = lerp(surface, error, mix), onErrorContainer = text,
        outline = outline, outlineVariant = lerp(surfaceAlt, outline, 0.55f),
        scrim = Color.Black, surfaceBright = if (dark) surfaceAlt else background,
        surfaceDim = if (dark) background else surfaceAlt,
        surfaceContainerLowest = background, surfaceContainerLow = lerp(background, surface, 0.5f),
        surfaceContainer = surface, surfaceContainerHigh = surfaceAlt,
        surfaceContainerHighest = lerp(surfaceAlt, text, 0.06f),
        primaryFixed = primaryTone, primaryFixedDim = primaryTone, onPrimaryFixed = primaryText, onPrimaryFixedVariant = primaryText,
        secondaryFixed = secondaryTone, secondaryFixedDim = secondaryTone, onSecondaryFixed = secondaryText, onSecondaryFixedVariant = secondaryText,
        tertiaryFixed = tertiaryTone, tertiaryFixedDim = tertiaryTone, onTertiaryFixed = tertiaryText, onTertiaryFixedVariant = tertiaryText,
    )
    return ThemeDefinition(id, name, dark, colors, successTone, foreground(onSuccess, successTone),
        reading = tertiaryTone, onReading = tertiaryText, featured = secondaryTone, onFeatured = secondaryText,
        personalization = primaryTone, onPersonalization = primaryText)
}

private fun contrast(first: Color, second: Color): Float {
    val a = first.luminance()
    val b = second.luminance()
    return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
}

val AtomOneLight = paletteTheme(
    id = "atom-one-light", name = "Atom One Light", dark = false,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFF0F0F1),
    surfaceAlt = Color(0xFFE5E5E6),
    text = Color(0xFF383A42),
    subtext = Color(0xFF535560),
    outline = Color(0xFFB0B1B4),
    primary = Color(0xFF3A6CDA),
    onPrimary = Color(0xFFFAFAFA),
    success = Color(0xFF50A14F),
    onSuccess = Color(0xFF1A1A1A),
    warning = Color(0xFFC18401),
    onWarning = Color(0xFF1A1A1A),
    error = Color(0xFFE45649),
    onError = Color(0xFF1A1A1A),
    info = Color(0xFF0177AA),
    onInfo = Color(0xFFFAFAFA),
)

val AtomOneDark = paletteTheme(
    id = "atom-one-dark", name = "Atom One Dark", dark = true,
    background = Color(0xFF21252B),
    surface = Color(0xFF282C34),
    surfaceAlt = Color(0xFF2F343F),
    text = Color(0xFFABB2BF),
    subtext = Color(0xFF9197A3),
    outline = Color(0xFF5C6370),
    primary = Color(0xFF61AFEF),
    onPrimary = Color(0xFF21252B),
    success = Color(0xFF98C379),
    onSuccess = Color(0xFF21252B),
    warning = Color(0xFFE5C07B),
    onWarning = Color(0xFF21252B),
    error = Color(0xFFE06C75),
    onError = Color(0xFF21252B),
    info = Color(0xFF56B6C2),
    onInfo = Color(0xFF21252B),
)

val TokyoDay = paletteTheme(
    id = "tokyo-day", name = "Tokyo Day", dark = false,
    background = Color(0xFFE1E2E7),
    surface = Color(0xFFEDEDF2),
    surfaceAlt = Color(0xFFD8DBE6),
    text = Color(0xFF1B1D2B),
    subtext = Color(0xFF414970),
    outline = Color(0xFFA6ACBF),
    primary = Color(0xFF2155C4),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF486627),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF8C5B16),
    onWarning = Color(0xFFFFFFFF),
    error = Color(0xFFC4283C),
    onError = Color(0xFFFFFFFF),
    info = Color(0xFF066A83),
    onInfo = Color(0xFFFFFFFF),
)

val TokyoNight = paletteTheme(
    id = "tokyo-night", name = "Tokyo Night", dark = true,
    background = Color(0xFF16161E),
    surface = Color(0xFF1A1B26),
    surfaceAlt = Color(0xFF24283B),
    text = Color(0xFFC0CAF5),
    subtext = Color(0xFFA9B1D6),
    outline = Color(0xFF414868),
    primary = Color(0xFF7AA2F7),
    onPrimary = Color(0xFF16161E),
    success = Color(0xFF9ECE6A),
    onSuccess = Color(0xFF16161E),
    warning = Color(0xFFE0AF68),
    onWarning = Color(0xFF16161E),
    error = Color(0xFFF7768E),
    onError = Color(0xFF16161E),
    info = Color(0xFF7DCFFF),
    onInfo = Color(0xFF16161E),
)

val Alucard = paletteTheme(
    id = "alucard", name = "Alucard", dark = false,
    background = Color(0xFFFFFBEB),
    surface = Color(0xFFFFFFFA),
    surfaceAlt = Color(0xFFF3EDD6),
    text = Color(0xFF1F1F1F),
    subtext = Color(0xFF4A4636),
    outline = Color(0xFFD2CCAE),
    primary = Color(0xFF644AC9),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF14710A),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF846E15),
    onWarning = Color(0xFFFFFFFF),
    error = Color(0xFFCB3A2A),
    onError = Color(0xFFFFFFFF),
    info = Color(0xFF036A96),
    onInfo = Color(0xFFFFFFFF),
)

val Dracula = paletteTheme(
    id = "dracula", name = "Dracula", dark = true,
    background = Color(0xFF21222C),
    surface = Color(0xFF282A36),
    surfaceAlt = Color(0xFF343746),
    text = Color(0xFFF8F8F2),
    subtext = Color(0xFFD2D4DE),
    outline = Color(0xFF565973),
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF21222C),
    success = Color(0xFF50FA7B),
    onSuccess = Color(0xFF21222C),
    warning = Color(0xFFFFB86C),
    onWarning = Color(0xFF21222C),
    error = Color(0xFFFF5555),
    onError = Color(0xFF21222C),
    info = Color(0xFF8BE9FD),
    onInfo = Color(0xFF21222C),
)

val CatppuccinLatte = paletteTheme(
    id = "catppuccin-latte", name = "Catppuccin Latte", dark = false,
    background = Color(0xFFEFF1F5),
    surface = Color(0xFFE6E9EF),
    surfaceAlt = Color(0xFFDCE0E8),
    text = Color(0xFF4C4F69),
    subtext = Color(0xFF5C5F77),
    outline = Color(0xFF9CA0B0),
    primary = Color(0xFF1C61E9),
    onPrimary = Color(0xFFEFF1F5),
    success = Color(0xFF40A02B),
    onSuccess = Color(0xFF11111B),
    warning = Color(0xFFDF8E1D),
    onWarning = Color(0xFF11111B),
    error = Color(0xFFD20F39),
    onError = Color(0xFFEFF1F5),
    info = Color(0xFF209FB5),
    onInfo = Color(0xFF11111B),
)

val RosePineDawn = paletteTheme(
    id = "rose-pine-dawn", name = "Rosé Pine Dawn", dark = false,
    background = Color(0xFFFAF4ED),
    surface = Color(0xFFFFFAF3),
    surfaceAlt = Color(0xFFF2E9E1),
    text = Color(0xFF433E59),
    subtext = Color(0xFF5D5878),
    outline = Color(0xFFCFC8BB),
    primary = Color(0xFF5F4D8C),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF286983),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFFEA9D34),
    onWarning = Color(0xFF3A3650),
    error = Color(0xFFA53F5D),
    onError = Color(0xFFFFFFFF),
    info = Color(0xFF3D7C88),
    onInfo = Color(0xFFFFFFFF),
)

val RosePine = paletteTheme(
    id = "rose-pine", name = "Rosé Pine", dark = true,
    background = Color(0xFF191724),
    surface = Color(0xFF1F1D2E),
    surfaceAlt = Color(0xFF26233A),
    text = Color(0xFFE0DEF4),
    subtext = Color(0xFFC0BCD8),
    outline = Color(0xFF524F67),
    primary = Color(0xFFC4A7E7),
    onPrimary = Color(0xFF191724),
    success = Color(0xFF31748F),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFFF6C177),
    onWarning = Color(0xFF191724),
    error = Color(0xFFEB6F92),
    onError = Color(0xFF191724),
    info = Color(0xFF9CCFD8),
    onInfo = Color(0xFF191724),
)

val SolarizedLight = paletteTheme(
    id = "solarized-light", name = "Solarized Light", dark = false,
    background = Color(0xFFFDF6E3),
    surface = Color(0xFFF7F0DB),
    surfaceAlt = Color(0xFFEEE8D5),
    text = Color(0xFF073642),
    subtext = Color(0xFF34555E),
    outline = Color(0xFFCDC6AD),
    primary = Color(0xFF1A6FB5),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF5F6F00),
    onSuccess = Color(0xFFFFFFFF),
    warning = Color(0xFF8A6800),
    onWarning = Color(0xFFFFFFFF),
    error = Color(0xFFC42A26),
    onError = Color(0xFFFFFFFF),
    info = Color(0xFF1C7E76),
    onInfo = Color(0xFFFFFFFF),
)

val SolarizedDark = paletteTheme(
    id = "solarized-dark", name = "Solarized Dark", dark = true,
    background = Color(0xFF002B36),
    surface = Color(0xFF073642),
    surfaceAlt = Color(0xFF0E4A59),
    text = Color(0xFFC5CECE),
    subtext = Color(0xFF93A1A1),
    outline = Color(0xFF1C5A6B),
    primary = Color(0xFF2176C4),
    onPrimary = Color(0xFFFFFFFF),
    success = Color(0xFF859900),
    onSuccess = Color(0xFF002B36),
    warning = Color(0xFFB58900),
    onWarning = Color(0xFF002B36),
    error = Color(0xFFDC322F),
    onError = Color(0xFFFFFFFF),
    info = Color(0xFF2AA198),
    onInfo = Color(0xFF002B36),
)
