package org.mobilism.material.data

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

internal data class ForumComments(val page: CommentPage, val nextStart: Int?)

internal object ForumReleaseParser {
    private const val BASE_URL = "https://forum.mobilism.org/"
    private val postId = Regex("p([1-9][0-9]*)")
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, uuuu, h:mm a", Locale.ENGLISH)

    fun release(html: String, id: Long): Release {
        val document = document(html, id)
        val title = document.selectFirst(".page-category-topic a[href], h2.topic-title a[href]")!!
        val post = posts(document).firstOrNull() ?: invalid()
        val content = content(post)
        val images = images(content)
        val forums = document.select(".pagination a[href*='viewforum.php'], .breadcrumbs a[href*='viewforum.php']")
        val section = forums.mapNotNull { link -> ContentSection.entries.find { it.label.equals(link.text(), true) } }.lastOrNull()
            ?: invalid()
        val url = title.absUrl("href").toHttpUrlOrNull() ?: invalid()
        val category = url.queryParameter("f")?.toLongOrNull()?.takeIf { it > 0 }
        val totalPosts = Regex("([0-9,]+)\\s+posts?\\b", RegexOption.IGNORE_CASE)
            .find(document.select(".pagination").text())?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()
        return Release(
            id = id, title = title.text(), author = author(post), thumbnail = images.firstOrNull(),
            publishedAt = date(post, document), replies = ((totalPosts ?: posts(document).size.toLong()) - 1).coerceAtLeast(0),
            views = 0, contentHtml = content.html(), images = images,
            browserUrl = "${BASE_URL}viewtopic.php?" + (category?.let { "f=$it&" } ?: "") + "t=$id",
            section = section, likeId = id(post), categoryId = category,
        )
    }

    fun comments(html: String, topicId: Long, originalPostId: Long, start: Int): ForumComments {
        val document = document(html, topicId)
        val posts = posts(document)
        if (posts.isEmpty()) invalid()
        val comments = posts.filter { id(it) != originalPostId }.map { post ->
            val content = content(post)
            val quotes = content.select("blockquote").filter { it.parents().none { p -> p.normalName() == "blockquote" } }.map { quote ->
                val attribution = quote.selectFirst("cite, .quote-author")
                val name = attribution?.text().orEmpty().removeSuffix(" wrote:").trim()
                attribution?.remove()
                CommentQuote(name, quote.html()).also { quote.remove() }
            }
            ReleaseComment(id(post), author(post),
                post.selectFirst(".user-profile-avatar img[src], .postprofile img[src]")?.absUrl("src")?.let(::webUrlOrNull),
                date(post, document), content.html(), images(content), quotes)
        }.distinctBy { it.id }
        val next = document.select(".pagination a[href]").mapNotNull { link ->
            val url = link.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            if (url.host != "forum.mobilism.org" || url.queryParameter("t")?.toLongOrNull() != topicId) null
            else url.queryParameter("start")?.toIntOrNull()?.takeIf { it > start }
        }.minOrNull()
        return ForumComments(CommentPage(comments, next != null), next)
    }

    private fun document(html: String, id: Long): Document {
        val document = Jsoup.parse(html, BASE_URL)
        document.outputSettings().prettyPrint(false)
        val title = document.selectFirst(".page-category-topic a[href], h2.topic-title a[href]") ?: invalid()
        val url = title.absUrl("href").toHttpUrlOrNull() ?: invalid()
        if (url.host != "forum.mobilism.org" || url.queryParameter("t")?.toLongOrNull() != id || title.text().isBlank()) invalid()
        return document
    }

    private fun posts(document: Document) = document.select("div.post[id], div[id]:has(> article .postbody)")
        .filter { postId.matches(it.id()) }

    private fun id(post: Element) = postId.matchEntire(post.id())!!.groupValues[1].toLong()

    private fun author(post: Element) = post.selectFirst(".user-profile-username, .postprofile dt, .author .username, .author .username-coloured")
        ?.text().orEmpty()

    private fun date(post: Element, document: Document): Long {
        val timestamp = post.selectFirst(".postbody .hidden-phone .muted em, .postbody .author")?.text().orEmpty()
            .replace(Regex("(?<=\\d)(?:st|nd|rd|th)\\b"), "")
        val date = Regex("[A-Z][a-z]{2} [0-9]{1,2}, [0-9]{4}, [0-9]{1,2}:[0-9]{2} [ap]m", RegexOption.IGNORE_CASE)
            .find(timestamp)?.value ?: return 0
        val offset = if (document.selectFirst("abbr[title='Daylight Saving Time']") != null) ZoneOffset.ofHours(1) else ZoneOffset.UTC
        return runCatching { LocalDateTime.parse(date.replace(Regex("[ap]m$")) { it.value.uppercase() }, dateFormat).toEpochSecond(offset) }.getOrDefault(0)
    }

    private fun content(post: Element): Element {
        val content = post.selectFirst(".postbody .content")?.clone() ?: invalid()
        content.select("script, style, iframe, form").remove()
        content.select("img.smilies").forEach { it.replaceWith(TextNode(it.attr("alt"))) }
        content.select("a[href]").forEach { link ->
            val url = webUrlOrNull(link.absUrl("href"))
            if (url == null) link.removeAttr("href") else link.attr("href", url)
        }
        content.select("img[src]").forEach { image ->
            val url = webUrlOrNull(image.absUrl("src"))
            if (url == null) image.remove() else image.attr("src", url)
        }
        // Forum BBCode uses styled spans where the API returns semantic tags.
        content.select("span[style]").forEach { span ->
            val style = span.attr("style").lowercase().replace(Regex("\\s+"), "")
            when {
                "font-weight:bold" in style || "font-weight:700" in style -> span.tagName("b")
                "text-decoration:underline" in style -> span.tagName("u")
                "font-style:italic" in style -> span.tagName("i")
            }
        }
        if (content.text().isBlank() && content.selectFirst("img") == null) invalid()
        return content
    }

    private fun images(content: Element) = content.select("img[src]").mapNotNull { webUrlOrNull(it.attr("src")) }.distinct()

    private fun invalid(): Nothing = throw ApiFailure(FailureKind.InvalidResponse, "The forum did not return this release. Try opening it in your browser.")
}
