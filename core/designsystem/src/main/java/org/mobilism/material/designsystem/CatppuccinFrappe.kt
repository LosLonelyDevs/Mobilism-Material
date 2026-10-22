package org.mobilism.material.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Exact palette and dark semantic colors from the supplied catppuccin-frappe.json.
// No wallpaper/dynamic colors override this default.
private object Frappe {
    val base = Color(0xFF303446)
    val mantle = Color(0xFF292C3C)
    val crust = Color(0xFF232634)
    val surface0 = Color(0xFF414559)
    val surface1 = Color(0xFF51576D)
    val surface2 = Color(0xFF626880)
    val overlay0 = Color(0xFF737994)
    val text = Color(0xFFC6D0F5)
    val subtext0 = Color(0xFFA5ADCE)
    val subtext1 = Color(0xFFB5BFE2)
    val blue = Color(0xFF8CAAEE)
    val lavender = Color(0xFFBABBF1)
    val mauve = Color(0xFFCA9EE6)
    val teal = Color(0xFF81C8BE)
    val sky = Color(0xFF99D1DB)
    val red = Color(0xFFE78284)
    val maroon = Color(0xFFEA999C)
}

val CatppuccinFrappe = ThemeDefinition(
    id = "catppuccin-frappe",
    name = "Catppuccin Frappé",
    isDark = true,
    success = Color(0xFFA6D189),
    onSuccess = Color(0xFF232634),
    colors = with(Frappe) {
        darkColorScheme(
            primary = blue, onPrimary = crust,
            primaryContainer = surface0, onPrimaryContainer = lavender,
            inversePrimary = base,
            secondary = mauve, onSecondary = crust,
            secondaryContainer = surface0, onSecondaryContainer = mauve,
            tertiary = teal, onTertiary = crust,
            tertiaryContainer = surface0, onTertiaryContainer = sky,
            background = mantle, onBackground = text,
            surface = base, onSurface = text,
            surfaceVariant = surface0, onSurfaceVariant = subtext1,
            surfaceTint = blue, inverseSurface = text, inverseOnSurface = base,
            error = red, onError = crust,
            errorContainer = surface0, onErrorContainer = maroon,
            outline = overlay0, outlineVariant = surface1, scrim = crust,
            surfaceBright = surface1, surfaceDim = mantle,
            surfaceContainerLowest = crust, surfaceContainerLow = mantle,
            surfaceContainer = base, surfaceContainerHigh = surface0,
            surfaceContainerHighest = surface1,
            primaryFixed = lavender, primaryFixedDim = blue,
            onPrimaryFixed = crust, onPrimaryFixedVariant = base,
            secondaryFixed = mauve, secondaryFixedDim = mauve,
            onSecondaryFixed = crust, onSecondaryFixedVariant = base,
            tertiaryFixed = sky, tertiaryFixedDim = teal,
            onTertiaryFixed = crust, onTertiaryFixedVariant = base,
        )
    },
)
