package org.mobilism.material.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

enum class AppFont(val title: String) {
    Geist("Geist"),
    System("System sans serif"),
}

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

fun appTypography(font: AppFont): Typography {
    val family = if (font == AppFont.Geist) Geist else FontFamily.SansSerif
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
