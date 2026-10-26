package org.mobilism.material.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mobilism.material.designsystem.ThemeCatalog

class ThemeContrastTest {
    @Test fun amoledUsesBlackBaseSurfacesAndKeepsExistingDefault() {
        val theme = ThemeCatalog.resolve("amoled-black")
        assertTrue(theme.isDark)
        assertTrue(theme in ThemeCatalog.forMode(true))
        assertTrue(theme !in ThemeCatalog.forMode(false))
        for (surface in listOf(theme.colors.background, theme.colors.surface,
            theme.colors.surfaceContainer, theme.colors.surfaceContainerLowest, theme.colors.surfaceContainerLow)) {
            org.junit.Assert.assertEquals(Color.Black, surface)
        }
        org.junit.Assert.assertEquals(0f, theme.colors.surfaceTint.alpha)
        org.junit.Assert.assertEquals("catppuccin-frappe", ThemeCatalog.default.id)
    }

    @Test fun builtInThemesKeepHeadingsAndDownloadButtonsReadable() {
        for (theme in ThemeCatalog.builtIn) {
            val c = theme.colors
            for (surface in listOf(c.background, c.surfaceContainerHigh)) {
                for (heading in listOf(c.primary, c.secondary, theme.reading)) {
                    assertTrue("${theme.name}: heading contrast", ratio(heading, surface) >= 4.5f)
                }
                assertTrue("${theme.name}: download button contrast", ratio(c.primary, surface) >= 3f)
                assertTrue("${theme.name}: supported download contrast", ratio(theme.success, surface) >= 3f)
            }
            assertTrue("${theme.name}: button label contrast", ratio(c.onPrimary, c.primary) >= 4.5f)
            assertTrue("${theme.name}: supported button label contrast", ratio(theme.onSuccess, theme.success) >= 4.5f)
        }
    }

    private fun ratio(a: Color, b: Color): Float =
        (maxOf(a.luminance(), b.luminance()) + 0.05f) / (minOf(a.luminance(), b.luminance()) + 0.05f)
}
