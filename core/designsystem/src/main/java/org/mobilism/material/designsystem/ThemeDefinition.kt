package org.mobilism.material.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.util.Locale

/** Theme identity and semantic roles stay independent of every feature screen. */
@Immutable
data class ThemeDefinition(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val colors: ColorScheme,
    val success: Color = colors.tertiary,
    val onSuccess: Color = colors.onTertiary,
    val reading: Color = colors.tertiary,
    val onReading: Color = colors.onTertiary,
    val featured: Color = colors.secondary,
    val onFeatured: Color = colors.onSecondary,
    val personalization: Color = colors.primary,
    val onPersonalization: Color = colors.onPrimary,
)

data class ThemePair(val name: String, val light: ThemeDefinition, val dark: ThemeDefinition)

object ThemeCatalog {
    val default = CatppuccinFrappe
    val pairs = listOf(ThemePair("Catppuccin", CatppuccinLatte, default),
        ThemePair("Rosé Pine", RosePineDawn, RosePine), ThemePair("Solarized", SolarizedLight, SolarizedDark),
        ThemePair("Atom One", AtomOneLight, AtomOneDark), ThemePair("Tokyo", TokyoDay, TokyoNight),
        ThemePair("Dracula", Alucard, Dracula))
    val builtIn: List<ThemeDefinition> = pairs.flatMap { listOf(it.light, it.dark) } + AmoledBlack

    fun forMode(dark: Boolean, custom: List<ThemeDefinition> = emptyList()): List<ThemeDefinition> =
        (builtIn + custom).filter { it.isDark == dark }.sortedBy { it.name.lowercase(Locale.ROOT) }

    fun resolve(id: String, custom: List<ThemeDefinition> = emptyList()): ThemeDefinition =
        (builtIn + custom).firstOrNull { it.id == id } ?: default

    /** Versioned import contract; storage and document picking belong to the app. */
    fun parse(json: String): ThemeDefinition {
        require(json.length <= 64 * 1024) { "Theme exceeds 64 KiB." }
        val root = JSONObject(json)
        require(root.getInt("schemaVersion") == 1) { "Unsupported theme schema." }
        val id = root.getString("id")
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{1,63}"))) { "Invalid theme ID." }
        require(builtIn.none { it.id == id }) { "Built-in themes cannot be replaced." }
        val name = root.getString("name").trim()
        require(name.length in 1..60) { "Theme name must have 1 to 60 characters." }
        val dark = root.getBoolean("dark")
        val roles = root.getJSONObject("colors")
        fun color(role: String): Color {
            val value = roles.getString(role)
            require(value.matches(Regex("#[0-9a-fA-F]{6}"))) { "Invalid color: $role" }
            return Color(android.graphics.Color.parseColor(value))
        }
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val colors = base.copy(
            primary = color("primary"), onPrimary = color("onPrimary"),
            primaryContainer = color("primaryContainer"),
            onPrimaryContainer = color("onPrimaryContainer"),
            inversePrimary = color("inversePrimary"),
            secondary = color("secondary"), onSecondary = color("onSecondary"),
            secondaryContainer = color("secondaryContainer"),
            onSecondaryContainer = color("onSecondaryContainer"),
            tertiary = color("tertiary"), onTertiary = color("onTertiary"),
            tertiaryContainer = color("tertiaryContainer"),
            onTertiaryContainer = color("onTertiaryContainer"),
            background = color("background"), onBackground = color("onBackground"),
            surface = color("surface"), onSurface = color("onSurface"),
            surfaceVariant = color("surfaceVariant"),
            onSurfaceVariant = color("onSurfaceVariant"),
            surfaceTint = color("surfaceTint"),
            inverseSurface = color("inverseSurface"),
            inverseOnSurface = color("inverseOnSurface"),
            error = color("error"), onError = color("onError"),
            errorContainer = color("errorContainer"),
            onErrorContainer = color("onErrorContainer"),
            outline = color("outline"), outlineVariant = color("outlineVariant"),
            scrim = color("scrim"), surfaceBright = color("surfaceBright"),
            surfaceDim = color("surfaceDim"),
            surfaceContainer = color("surfaceContainer"),
            surfaceContainerHigh = color("surfaceContainerHigh"),
            surfaceContainerHighest = color("surfaceContainerHighest"),
            surfaceContainerLow = color("surfaceContainerLow"),
            surfaceContainerLowest = color("surfaceContainerLowest"),
            primaryFixed = color("primaryFixed"), onPrimaryFixed = color("onPrimaryFixed"),
            primaryFixedDim = color("primaryFixedDim"),
            onPrimaryFixedVariant = color("onPrimaryFixedVariant"),
            secondaryFixed = color("secondaryFixed"), onSecondaryFixed = color("onSecondaryFixed"),
            secondaryFixedDim = color("secondaryFixedDim"),
            onSecondaryFixedVariant = color("onSecondaryFixedVariant"),
            tertiaryFixed = color("tertiaryFixed"), onTertiaryFixed = color("onTertiaryFixed"),
            tertiaryFixedDim = color("tertiaryFixedDim"),
            onTertiaryFixedVariant = color("onTertiaryFixedVariant"),
        )
        return ThemeDefinition(id, name, dark, colors,
            success = if (roles.has("success")) color("success") else colors.tertiary,
            onSuccess = if (roles.has("onSuccess")) color("onSuccess") else colors.onTertiary,
            reading = if (roles.has("reading")) color("reading") else colors.tertiary,
            onReading = if (roles.has("onReading")) color("onReading") else colors.onTertiary,
            featured = if (roles.has("featured")) color("featured") else colors.secondary,
            onFeatured = if (roles.has("onFeatured")) color("onFeatured") else colors.onSecondary,
            personalization = if (roles.has("personalization")) color("personalization") else colors.primary,
            onPersonalization = if (roles.has("onPersonalization")) color("onPersonalization") else colors.onPrimary,
        )
    }
}
