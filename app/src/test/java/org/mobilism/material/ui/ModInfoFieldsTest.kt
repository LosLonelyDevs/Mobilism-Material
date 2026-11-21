package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ModInfoFieldsTest {
    private fun parts(html: String, markdown: Boolean = false) = ModInfoFields.parse(html, "https://forum.mobilism.org/", markdown)

    @Test fun separatesOnlyRecognizedFieldsAndKeepsTheOtherMarkup() {
        val result = parts("<b>◉ Premium Unlocked<br>➡️ Languages:af, am, ar, en-XC, es-419,<br>" +
            "iw, sr-Latn, zh-TW<br>➡ Screen DPIs: 120dpi, 160 DPI, 240dpi<br>" +
            "Credit: <a href='https://example.org'>Author</a></b><br>This app has no advertisements")
        assertEquals(4, result.size)
        assertEquals("◉ Premium Unlocked", Jsoup.parse((result[0] as ModInfoPart.Body).html).selectFirst("b")!!.text())
        val languages = result[1] as ModInfoPart.Languages
        assertEquals(listOf("af","am","ar","en-XC","es-419","iw","sr-Latn","zh-TW"), languages.values)
        assertTrue(languages.expandable)
        assertEquals(listOf("120", "160", "240"), (result[2] as ModInfoPart.Densities).values)
        val remaining = Jsoup.parse((result[3] as ModInfoPart.Body).html)
        assertEquals("https://example.org", remaining.selectFirst("b a")!!.attr("href"))
        assertTrue(remaining.text().endsWith("This app has no advertisements"))
    }

    @Test fun recognizesLabelsOnSeparateLinesAndPreservesShortLists() {
        val result = parts("<b>Supported languages:</b><br>English, French<br><b>Screen densities</b><br>320 / 480")
        assertEquals(listOf(ModInfoPart.Languages(listOf("English", "French")), ModInfoPart.Densities(listOf("320", "480"))), result)
        assertFalse((result.first() as ModInfoPart.Languages).expandable)
    }

    @Test fun keepsProseQuotesCodeAndLinkedValuesIntact() {
        listOf("Languages: you can do this", "Screen density: 120dpi on old phones", "<pre>Languages: en, de</pre>",
            "<blockquote>Screen DPIs: 120, 480</blockquote>", "<b>Locales:</b> <a href='/guide'>fr</a>").forEach {
            assertTrue(it, parts(it).isEmpty())
        }
    }
}
