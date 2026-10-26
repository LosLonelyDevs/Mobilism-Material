package org.mobilism.material.data

import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

internal object CommentMarkup {
    private val smile = Regex("\\[smile](.*?)\\[/smile]", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val unicode = Regex("\\\\u([0-9a-fA-F]{4})")

    fun html(value: String): String = smile.replace(value) { match ->
        // The API double-escapes smiley Unicode. Leave literal escapes elsewhere alone.
        val text = Parser.unescapeEntities(match.groupValues[1], false)
        val decoded = unicode.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }
        val valid = decoded.indices.all { index ->
            val char = decoded[index]
            when {
                char.isHighSurrogate() -> index + 1 < decoded.length && decoded[index + 1].isLowSurrogate()
                char.isLowSurrogate() -> index > 0 && decoded[index - 1].isHighSurrogate()
                else -> true
            }
        }
        Entities.escape(if (valid) decoded else text)
    }
}
