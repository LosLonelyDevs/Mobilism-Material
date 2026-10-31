package org.mobilism.material.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val MaterialYou = ThemeDefinition("material-you", "Material You", false, CatppuccinLatte.colors)

@Composable
fun resolveAppTheme(theme: ThemeDefinition): ThemeDefinition {
    if (theme.id != MaterialYou.id) return theme
    val dark = isSystemInDarkTheme()
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) ThemeCatalog.default.colors else CatppuccinLatte.colors
    return ThemeDefinition(MaterialYou.id, MaterialYou.name, dark, colors)
}
