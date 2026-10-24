package org.mobilism.material.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Palette from catppuccin-frappe.json, with lighter text accents for raised surfaces.
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
    val peach = Color(0xFFEF9F76)
    val yellow = Color(0xFFE5C890)
    val pink = Color(0xFFF4B8E4)
}

val CatppuccinFrappe = ThemeDefinition(
    id = "catppuccin-frappe",
    name = "Catppuccin Frappé",
    isDark = true,
    success = Color(0xFFA6D189),
    onSuccess = Color(0xFF232634),
    reading = lerp(Frappe.peach, Frappe.text, 0.12f), onReading = Frappe.crust,
    featured = Frappe.yellow, onFeatured = Frappe.crust,
    personalization = Frappe.pink, onPersonalization = Frappe.crust,
    colors = with(Frappe) {
        darkColorScheme(
            primary = lerp(blue, text, 0.3f), onPrimary = crust,
            primaryContainer = lerp(base, blue, 0.22f), onPrimaryContainer = lavender,
            inversePrimary = base,
            secondary = lerp(mauve, text, 0.2f), onSecondary = crust,
            secondaryContainer = lerp(base, mauve, 0.22f), onSecondaryContainer = mauve,
            tertiary = teal, onTertiary = crust,
            tertiaryContainer = lerp(base, teal, 0.22f), onTertiaryContainer = sky,
            background = mantle, onBackground = text,
            surface = base, onSurface = text,
            surfaceVariant = surface0, onSurfaceVariant = subtext1,
            surfaceTint = blue, inverseSurface = text, inverseOnSurface = base,
            error = red, onError = crust,
            errorContainer = lerp(base, red, 0.22f), onErrorContainer = maroon,
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
