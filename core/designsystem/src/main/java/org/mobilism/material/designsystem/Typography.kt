package org.mobilism.material.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.io.File

enum class AppFont(val title: String) {
    Geist("Geist"),
    Alata("Alata"),
    AtkinsonHyperlegible("Atkinson Hyperlegible"),
    IbmPlexSans("IBM Plex Sans"),
    System("System sans serif"),
    Custom("Custom font"),
}

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

fun fontFamilyFor(font: AppFont, customFont: File? = null): FontFamily = when (font) {
    AppFont.Geist -> Geist
    AppFont.Alata -> FontFamily(Font(R.font.alata_regular))
    AppFont.AtkinsonHyperlegible -> FontFamily(Font(R.font.atkinson_regular), Font(R.font.atkinson_bold, FontWeight.Bold))
    AppFont.IbmPlexSans -> FontFamily(Font(R.font.plex_regular), Font(R.font.plex_medium, FontWeight.Medium),
        Font(R.font.plex_semibold, FontWeight.SemiBold), Font(R.font.plex_bold, FontWeight.Bold))
    AppFont.System -> FontFamily.SansSerif
    AppFont.Custom -> customFont?.takeIf { it.isFile }?.let { FontFamily(Font(it)) } ?: Geist
}

fun appTypography(font: AppFont, customFont: File? = null): Typography {
    val family = fontFamilyFor(font, customFont)
    return Typography().let { type ->
        type.copy(
            displayLarge = type.displayLarge.copy(fontFamily = family),
            displayMedium = type.displayMedium.copy(fontFamily = family),
            displaySmall = type.displaySmall.copy(fontFamily = family),
            headlineLarge = type.headlineLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            headlineMedium = type.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            headlineSmall = type.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            titleLarge = type.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            titleMedium = type.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            titleSmall = type.titleSmall.copy(fontFamily = family),
            bodyLarge = type.bodyLarge.copy(fontFamily = family),
            bodyMedium = type.bodyMedium.copy(fontFamily = family),
            bodySmall = type.bodySmall.copy(fontFamily = family),
            labelLarge = type.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            labelMedium = type.labelMedium.copy(fontFamily = family),
            labelSmall = type.labelSmall.copy(fontFamily = family),
        )
    }
}
