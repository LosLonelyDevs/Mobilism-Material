package org.mobilism.material.ui

import android.text.Html
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.style.BulletSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.text.style.LeadingMarginSpan
import android.text.Spanned
import android.text.util.Linkify
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist
import org.mobilism.material.data.webUrlOrNull
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.ReleaseTitles

internal fun forumText(html: String, baseUrl: String, options: ForumFormattingOptions = ForumFormattingOptions(),
    density: Float = 1f, quoteColor: Int = android.graphics.Color.GRAY, textSizePx: Float = 16f * density,
    headingColor: Int = quoteColor, requirementsColor: Int = quoteColor, descriptionTitle: String? = null): SpannableStringBuilder {
    val allowed = Safelist.basic().addTags("tt").removeProtocols("a", "href", "ftp", "mailto")
    if (options.markdown || options.cleanUpReleaseText) allowed.addTags("h1", "h2", "h3", "h4", "h5", "h6")
    val normalized = ForumCodeBlocks.normalize(html, baseUrl)
    val document = Jsoup.parseBodyFragment(ForumFormatting.format(normalized, baseUrl, options), baseUrl)
    val enhanced = options.markdown || options.cleanUpReleaseText
    if (enhanced) document.outputSettings().prettyPrint(false)
    if (options.markdown) document.select("a[href]").filter { it.attr("href").isBlank() }.forEach { it.removeAttr("href") }
    if (options.markdown || options.cleanUpReleaseText) document.select("ol").toList().asReversed().forEach { list ->
        val start = list.attr("start").toIntOrNull() ?: 1
        val depth = list.parents().count { it.normalName() == "ol" }
        list.children().filter { it.normalName() == "li" }.forEachIndexed { index, item ->
            val prefix = "\u2003".repeat(depth) + "${start.toLong() + index}. "
            val first = item.childNodes().firstOrNull { it !is org.jsoup.nodes.TextNode || !it.isBlank }
            if (first is org.jsoup.nodes.Element && first.normalName() == "p") first.prependText(prefix)
            else {
                val paragraph = org.jsoup.nodes.Element("p").text(prefix)
                item.childNodes().toList().takeWhile { it !is org.jsoup.nodes.Element || !it.isBlock || it.normalName() == "br" }
                    .forEach(paragraph::appendChild)
                item.prependChild(paragraph)
            }
            item.tagName("div")
        }
        list.tagName("div")
    }
    document.select("pre").forEach { block ->
        val text = block.wholeText()
        block.tagName("p").empty().appendElement("tt").apply {
            text.split('\n').forEachIndexed { index, line ->
                if (index > 0) appendElement("br")
                appendText(line.replace(" ", "\u00a0"))
            }
        }
    }
    document.select("code").tagName("tt")
    var splitTitle = false
    val cleaned = if (options.cleanUpReleaseText) {
        val safe = Cleaner(allowed).clean(document)
        safe.outputSettings().prettyPrint(false)
        // Add theme colors after sanitization so only the app supplies presentation attributes.
        val blocks = safe.select("p, h1, h2, h3, h4, h5, h6")
        blocks.forEach { block ->
            if (block.parents().any { it.normalName() in setOf("pre", "code", "tt", "blockquote") }) return@forEach
            if (descriptionTitle != null && block === blocks.first() && block.selectFirst("a, tt, br") == null &&
                safe.body().text().startsWith(block.text())) {
                splitTitle = styleDescriptionTitle(block, descriptionTitle, headingColor, requirementsColor)
                if (splitTitle) return@forEach
            }
            if (ReleaseSections.isAdvertisingNotice(block.text()) && block.selectFirst("tt") == null) {
                block.tagName("p")
                block.select("b, strong").unwrap()
                return@forEach
            }
            val requirements = requirementsLabel.containsMatchIn(block.text()) &&
                !block.childNodes().all { it is Element && it.normalName() == "tt" }
            if (requirements || block.normalName() != "p") {
                val color = if (requirements) requirementsColor else headingColor
                val font = Element("font").attr("color", "#%06x".format(java.util.Locale.ROOT, color and 0xffffff))
                val inner = if (requirements) font.appendElement("small") else font
                inner.appendChildren(block.childNodes().toList())
                block.appendChild(font)
            }
        }
        safe.body().html()
    } else if (enhanced) Jsoup.clean(document.body().html(), baseUrl, allowed, document.outputSettings())
    else Jsoup.clean(document.body().html(), baseUrl, allowed)
    val flags = if (enhanced) Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM or Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST
        else Html.FROM_HTML_MODE_LEGACY
    return SpannableStringBuilder(Html.fromHtml(cleaned, flags)).apply {
        Regex("\n(?:[ \\t\\u00a0]*\n){2,}").findAll(this).toList().asReversed().forEach {
            if (!options.markdown || getSpans(it.range.first, it.range.last + 1, TypefaceSpan::class.java).isEmpty())
                replace(it.range.first, it.range.last + 1, "\n\n")
        }
        fun literalAt(index: Int) = options.markdown && getSpans(index, index + 1, TypefaceSpan::class.java).isNotEmpty()
        while (isNotEmpty() && last().isWhitespace() && !literalAt(length - 1)) delete(length - 1, length)
        while (isNotEmpty() && first().isWhitespace() && !literalAt(0)) delete(0, 1)
        if (options.cleanUpReleaseText) {
            Regex("""\n\n([^\n]+)""").findAll(this).filter { match ->
                val line = match.groups[1]!!
                ReleaseSections.isAdvertisingNotice(line.value) &&
                    getSpans(line.range.first, line.range.last + 1, TypefaceSpan::class.java).isEmpty() &&
                    getSpans(line.range.first, line.range.last + 1, QuoteSpan::class.java).isEmpty()
            }.map { it.range.first }.toList().asReversed().forEach { delete(it, it + 1) }
            val firstEnd = indexOf('\n')
            val titleEnd = if (splitTitle && firstEnd >= 0) indexOf('\n', firstEnd + 1) else firstEnd
            val subtitleStart = titleEnd + 2
            if (titleEnd >= 0 && subtitleStart < length && this[titleEnd + 1] == '\n' &&
                requirementsLabel.containsMatchIn(subSequence(subtitleStart, minOf(subtitleStart + 64, length))) &&
                getSpans(subtitleStart, subtitleStart + 1, RelativeSizeSpan::class.java).any { it.sizeChange < 1f }) {
                delete(titleEnd + 1, subtitleStart)
            }
        }
        if (enhanced) {
            getSpans(0, length, BulletSpan::class.java)
                .sortedWith(compareBy<BulletSpan> { getSpanStart(it) }.thenByDescending { getSpanEnd(it) }).forEach { span ->
                setSpan(ForumBulletSpan(density), getSpanStart(span), getSpanEnd(span), getSpanFlags(span))
                removeSpan(span)
            }
            getSpans(0, length, QuoteSpan::class.java).forEach { span ->
                setSpan(ForumQuoteSpan(density, quoteColor), getSpanStart(span), getSpanEnd(span), getSpanFlags(span))
                removeSpan(span)
            }
            Regex("""(?m)^[\u2003]*\d+\. +[^\n]+""").findAll(this).forEach { line ->
                if (getSpans(line.range.first, line.range.last + 1, TypefaceSpan::class.java).isEmpty()) {
                    val prefix = line.value.substringBefore(' ') + " "
                    setSpan(LeadingMarginSpan.Standard(0, (prefix.length * textSizePx * 0.55f).toInt()),
                        line.range.first, line.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        // Linkify removes existing URL spans. Detect bare URLs separately to preserve named anchors.
        val detected = SpannableString(toString())
        Linkify.addLinks(detected, Linkify.WEB_URLS)
        detected.getSpans(0, detected.length, URLSpan::class.java).forEach { link ->
            val start = detected.getSpanStart(link)
            val url = trimUrlPunctuation(link.url)
            val end = detected.getSpanEnd(link) - (link.url.length - url.length)
            if (end > start && webUrlOrNull(url) != null && getSpans(start, end, URLSpan::class.java).isEmpty()) {
                setSpan(URLSpan(url), start, end, detected.getSpanFlags(link))
            }
        }
    }
}

private val requirementsLabel = Regex("""^(?:(?:system|android)\s+)?requirements?\s*:""", RegexOption.IGNORE_CASE)

private val versionLine = Regex("""(?i)(?:v\d[\w.+-]*|\d+(?:\.\d+)+)(?:\s+v\d[\w.+-]*)*""")
private val nextVersion = Regex("""(?i)(?<=\d)\s+(?=v\d)""")

internal fun styleDescriptionTitle(block: Element, releaseTitle: String, heading: Int, secondary: Int): Boolean {
    val text = block.text()
    val title = ReleaseTitles.parse(block.html(), ContentSection.Apps)
    val releaseName = ReleaseTitles.parse(releaseTitle, ContentSection.Apps).name
    val name = if (title.version != null) title.name else {
        if (!text.startsWith("$releaseName ", ignoreCase = true) ||
            !versionLine.matches(text.substring(releaseName.length).trimStart())) return false
        text.take(releaseName.length)
    }
    if (!text.startsWith(name)) return false
    val versions = text.substring(name.length).trimStart(' ', '-', ':', '|')
    val knownName = releaseName.equals(name, ignoreCase = true)
    val titleLike = block.normalName().startsWith("h") || block.selectFirst("b, strong") != null ||
        block.nextElementSibling()?.text()?.let(requirementsLabel::containsMatchIn) == true
    if (!knownName && (!titleLike || !versionLine.matches(versions))) return false
    fun color(value: Int) = "#%06x".format(java.util.Locale.ROOT, value and 0xffffff)
    block.tagName("p").empty()
    block.appendElement("font").attr("color", color(heading)).appendElement("big").appendElement("b").text(name)
    block.appendElement("br")
    block.appendElement("font").attr("color", color(secondary)).appendElement("small")
        .text(versions.replace(nextVersion, " · "))
    return true
}

private fun trimUrlPunctuation(value: String): String {
    var url = value
    while (url.isNotEmpty()) {
        val last = url.last()
        val opening = when (last) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> null }
        val outsideBracket = opening != null && url.count { it == last } > url.count { it == opening }
        if (last in ".,;:!?\"'" || outsideBracket) url = url.dropLast(1) else break
    }
    return url
}
