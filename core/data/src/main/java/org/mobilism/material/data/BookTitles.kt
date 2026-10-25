package org.mobilism.material.data

/** Only move recognizable metadata. Ordinary numbers and subtitles remain part of the title. */
internal object BookTitles {
    private val suffix = Regex("""\s*(?:\[([^\[\]]+)\]|\(([^()]+)\))\s*$""")
    private val formats = Regex("""(?i)^\.?(?:epub|pdf|mobi|azw3?|fb2|cb[rz]|djvu|txt|rtf|mp3|m4[ab]|flac|audiobook)(?:\s*[/,+&]\s*\.?(?:epub|pdf|mobi|azw3?|fb2|cb[rz]|djvu|txt|rtf|mp3|m4[ab]|flac|audiobook))*$""")
    private val seriesNumber = Regex("""(?i)(?:#\s*\d+(?:\.\d+)?|\b(?:book|volume|vol\.?|part)\s+\d+(?:\.\d+)?)(?:\s*[-\u2013]\s*\d+)?\s*$""")
    private val by = Regex("""(?i)\s+by\s+""")

    fun hasBookMetadata(value: String): Boolean =
        suffix.find(value)?.groupValues?.drop(1)?.any {
            formats.matches(it.trim()) && (by.containsMatchIn(value) || it.trim().startsWith('.'))
        } == true

    fun parse(original: String): ReleaseTitle {
        var title = original
        val tags = mutableListOf<String>()
        var series: String? = null
        fun stripMetadata() {
            while (true) {
                val match = suffix.find(title) ?: break
                val value = match.groupValues.drop(1).first { it.isNotEmpty() }.trim()
                when {
                    formats.matches(value) -> tags.add(0, value.removePrefix("."))
                    series == null && seriesNumber.containsMatchIn(value) -> series = value
                    else -> break
                }
                title = title.substring(0, match.range.first).trim()
            }
        }
        stripMetadata()
        val marker = by.findAll(title).lastOrNull()
        val author = marker?.let { title.substring(it.range.last + 1).trim() }
            ?.takeIf { it.any(Char::isLetter) && (it.any(Char::isWhitespace) || Regex("""\p{L}\.""").containsMatchIn(it)) }
        if (author != null) {
            title = title.substring(0, checkNotNull(marker).range.first).trim()
            stripMetadata()
        }
        if (title.isBlank()) return ReleaseTitle(original)
        return ReleaseTitle(title, tags = tags, bookAuthor = author, series = series)
    }
}
