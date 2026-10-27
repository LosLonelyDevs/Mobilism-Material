package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.mobilism.material.data.HostMatcher
import org.mobilism.material.data.webUrlOrNull

data class DownloadLink(
    val url: String, val section: String? = null, val groupId: Int? = null, val notesHtml: String = "",
)
data class ReleaseContent(val bodyHtml: String, val downloads: List<DownloadLink>, val downloadNotesHtml: String = "")

/** Mirrors inherit the preceding variant, not just its CPU architecture. */
object ReleaseContentParser {
    private val plainUrl = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val mirror = Regex("""(?i)^mirrors?\s*:?$""")
    private val note = Regex("""(?i)^(?:notes?|password|instructions?|warning|important|install(?:ation)?|requires?)\b""")
    private val deliveryWords = Regex("""(?i)\b(?:direct|downloads?|links?|mirrors?|apk|files?|host(?:ed)?|via|from|on)\b""")
    private data class Block(val labels: List<DownloadLine>, val urls: MutableList<String>, val notes: MutableList<String>)

    fun parse(html: String, apiUrls: List<String>, baseUrl: String): ReleaseContent {
        val api = apiUrls.mapNotNull(::downloadUrl).distinct()
        val lines = ReleaseDownloadLines.read(html, baseUrl)
        val boundary = lines.indexOfFirst { ReleaseDownloadLines.marker.matches(it.text) }
        if (boundary < 0) return ReleaseContent(removeUrls(html, api.toSet(), baseUrl), api.map(::DownloadLink))

        val blocks = mutableListOf<Block>()
        val pending = mutableListOf<DownloadLine>()
        val globalNotes = mutableListOf<String>()
        var parents = emptyList<DownloadLine>()
        var previousLabels = emptyList<DownloadLine>()
        var mirrorPending = false
        lines.drop(boundary + 1).forEach { line ->
            if (mirror.matches(line.text) && Jsoup.parseBodyFragment(line.html).selectFirst("a[href]") == null) {
                mirrorPending = true
                return@forEach
            }
            val fragment = Jsoup.parseBodyFragment(line.html, baseUrl).body()
            val anchors = fragment.select("a[href]")
            val plainUrls = mutableListOf<String>()
            fragment.forEachNode { node ->
                if (node is TextNode && generateSequence(node.parent()) { it.parent() }.none { it.normalName() == "a" }) {
                    plainUrls += urlsIn(node.wholeText)
                }
            }
            val candidates = (anchors.mapNotNull { downloadUrl(it.absUrl("href")) } +
                plainUrls).distinct()
            val withoutAnchors = fragment.clone().apply { select("a").remove() }.text()
            val prose = plainUrl.replace(withoutAnchors, "").trim().trim(':')
            val inlineLabel = if (candidates.isEmpty()) null else
                ReleaseDownloadLines.read(removeUrls(line.html, candidates.toSet(), baseUrl), baseUrl).singleOrNull()
                    ?.takeIf { isLabel(it) && !mirror.matches(it.text) &&
                        (it.text.endsWith(':') || DownloadGroups.isArchitectureOnly(it.text)) }
            // An explanatory sentence with a link is a note, not another download host.
            val downloadLine = candidates.isNotEmpty() &&
                (prose.isBlank() || mirror.matches(prose) || inlineLabel != null || candidates.any { it in api })
            if (!downloadLine) {
                pending += line
                return@forEach
            }
            inlineLabel?.let { pending += it }
            pending.removeAll { isDeliveryLabel(it, candidates) }

            if (mirrorPending && blocks.isNotEmpty() && pending.none(::isLabel)) {
                blocks.last().notes += pending.map { it.html }
                pending.clear()
            }
            mirrorPending = false

            if (blocks.isEmpty() || pending.isNotEmpty()) {
                val labels = pending.filter(::isLabel)
                val notes = pending.filterNot(::isLabel).map { it.html }.toMutableList()
                val fullLabels = when {
                    labels.size > 1 -> {
                        parents = labels.dropLast(1)
                        labels
                    }
                    labels.size == 1 -> {
                        val next = labels.single()
                        val previous = previousLabels.lastOrNull()
                        parents = when {
                            next.headingLevel != null -> parents.takeWhile { (it.headingLevel ?: Int.MAX_VALUE) < next.headingLevel }
                            previous == null || DownloadGroups.isArchitectureOnly(next.text) != DownloadGroups.isArchitectureOnly(previous.text) -> emptyList()
                            else -> parents
                        }
                        parents + next
                    }
                    else -> emptyList()
                }
                previousLabels = fullLabels
                val previous = blocks.lastOrNull()
                // Only adjacent repetitions can be mirrors. A later variant may reuse the same label.
                if (previous == null || title(previous.labels) != title(fullLabels) || previous.notes != notes) {
                    blocks += Block(fullLabels, mutableListOf(), notes)
                }
                pending.clear()
            }
            val block = blocks.last()
            block.urls += candidates.filterNot { it in block.urls }
            val remainder = removeUrls(line.html, candidates.toSet(), baseUrl, keepLabels = true)
            val remainderText = Jsoup.parseBodyFragment(remainder).text()
            if (inlineLabel == null && remainderText.isNotBlank() && !mirror.matches(remainderText)) block.notes += remainder
        }
        pending.forEach { line ->
            // Forum help is shared across variants; other trailing instructions belong to the last block.
            if (blocks.isEmpty() || Jsoup.parseBodyFragment(line.html, baseUrl).select("a[href]")
                    .any { HostMatcher.matches(it.absUrl("href"), "mobilism.org") }) globalNotes += line.html
            else blocks.last().notes += line.html
        }
        val downloads = blocks.flatMapIndexed { index, block ->
            block.urls.map { DownloadLink(it, title(block.labels), index, block.notes.joinToString("<br>")) }
        }.toMutableList()
        val found = downloads.map { it.url }.toSet()
        downloads += api.filterNot { it in found }.map(::DownloadLink)
        return ReleaseContent(lines.take(boundary).joinToString("<br>") { it.html }, downloads, globalNotes.joinToString("<br>"))
    }

    private fun title(labels: List<DownloadLine>): String? = labels.joinToString(" / ") {
        DownloadGroups.normalizedLabel(it.text)
    }.ifBlank { null }

    private fun isLabel(line: DownloadLine): Boolean {
        val text = line.text.trim()
        return text.isNotEmpty() && text.length <= 140 && !note.containsMatchIn(text) &&
            Jsoup.parseBodyFragment(line.html).selectFirst("a[href]") == null &&
            (line.styled || DownloadGroups.isArchitectureOnly(text) ||
                (text.length <= 80 && !text.contains(Regex("""[.!?]|:\s+\S"""))))
    }

    private fun isDeliveryLabel(line: DownloadLine, urls: List<String>): Boolean {
        if (!isLabel(line) || DownloadGroups.architectures(line.text).isNotEmpty()) return false
        val hostNames = urls.flatMap { url ->
            val domain = HostMatcher.domain(url).orEmpty()
            listOf(HostMatcher.displayName(url), domain) + domain.split('.').dropLast(1)
        }.filter { it.length > 2 }.distinct().sortedByDescending { it.length }
        var remaining = line.text
        hostNames.forEach { host ->
            remaining = Regex("(?i)(?<![\\p{L}\\p{N}])${Regex.escape(host)}(?![\\p{L}\\p{N}])").replace(remaining, " ")
        }
        return deliveryWords.replace(remaining, "").none { it.isLetterOrDigit() }
    }

    private fun downloadUrl(raw: String): String? = webUrlOrNull(raw)?.takeUnless { HostMatcher.matches(it, "mobilism.org") }
    private fun urlsIn(text: String): List<String> = plainUrl.findAll(text).mapNotNull { downloadUrl(it.value.trimEnd('.', ',', ';')) }.toList()

    private fun removeUrls(html: String, urls: Set<String>, baseUrl: String, keepLabels: Boolean = false): String {
        val body = Jsoup.parseBodyFragment(html, baseUrl).body()
        body.select("a[href]").filter { webUrlOrNull(it.absUrl("href")) in urls }.forEach {
            val label = it.text()
            if (keepLabels && label.isNotBlank() && !label.startsWith("http", ignoreCase = true) &&
                !label.matches(Regex("""(?i)(?:mirrors?|download|file|here)\s*:?"""))) it.tagName("span").removeAttr("href")
            else it.remove()
        }
        body.forEachNode { node ->
            if (node is TextNode) node.text(plainUrl.replace(node.wholeText) {
                if (webUrlOrNull(it.value.trimEnd('.', ',', ';')) in urls) "" else it.value
            })
        }
        return body.html()
    }
}
