package org.mobilism.material.ui

import android.text.Html
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.text.util.Linkify
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.mobilism.material.data.webUrlOrNull

internal fun forumText(html: String, baseUrl: String): SpannableStringBuilder {
    val allowed = Safelist.basic().removeProtocols("a", "href", "ftp", "mailto")
    val cleaned = Jsoup.clean(html, baseUrl, allowed)
    return SpannableStringBuilder(Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY)).apply {
        Regex("\n(?:[ \\t\\u00a0]*\n){2,}").findAll(this).toList().asReversed().forEach {
            replace(it.range.first, it.range.last + 1, "\n\n")
        }
        while (isNotEmpty() && last().isWhitespace()) delete(length - 1, length)
        while (isNotEmpty() && first().isWhitespace()) delete(0, 1)
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
