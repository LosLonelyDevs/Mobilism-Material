package org.mobilism.material.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object ForumCodeBlocks {
    fun normalize(html: String, baseUrl: String): String {
        val document = Jsoup.parseBodyFragment(html, baseUrl)
        document.outputSettings().prettyPrint(false)
        document.select(".codebox").forEach { box ->
            val code = box.selectFirst("code") ?: return@forEach
            // The forum's Select all link runs browser JavaScript and is not part of the post.
            box.replaceWith(Element("pre").appendChild(code.clone()))
        }
        return document.body().html()
    }
}
