package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.mobilism.material.ReleaseLinks
import org.mobilism.material.data.DownloadHelp

internal data class DownloadNote(val html: String, val help: Boolean = false)

internal object DownloadNotesParser {
    private val helpPrompt = Regex("""(?i)^Trouble downloading\?\s*Read\s+This\s*\.?$""")

    fun parse(html: String, baseUrl: String): List<DownloadNote> {
        val result = mutableListOf<DownloadNote>()
        val pending = mutableListOf<String>()
        fun flush() {
            if (pending.isNotEmpty()) result += DownloadNote(pending.joinToString("<br>"))
            pending.clear()
        }
        ReleaseDownloadLines.read(html, baseUrl).forEach { line ->
            val links = Jsoup.parseBodyFragment(line.html, baseUrl).select("a[href]")
            val help = links.singleOrNull()?.let {
                ReleaseLinks.topicId(it.absUrl("href")) == DownloadHelp.TOPIC_ID
            } == true && helpPrompt.matches(line.text)
            if (help) {
                flush()
                result += DownloadNote(line.html, help = true)
            } else pending += line.html
        }
        flush()
        return result
    }
}
