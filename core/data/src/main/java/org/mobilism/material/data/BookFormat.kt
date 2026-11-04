package org.mobilism.material.data

enum class BookFormat(val label: String) {
    Epub("EPUB"), Pdf("PDF"), Mobi("MOBI"), Azw("AZW"), Azw3("AZW3"), Fb2("FB2"),
    Cbr("CBR"), Cbz("CBZ"), Djvu("DJVU"), Txt("TXT"), Rtf("RTF"),
    M4b("M4B"), Mp3("MP3"), M4a("M4A"), Flac("FLAC");

    companion object {
        fun forRelease(release: Release): Set<BookFormat> {
            if (release.section != null && release.section != ContentSection.Books) return emptySet()
            val tokens = ReleaseTitles.parse(release.title, ContentSection.Books).tags
                .flatMap { it.split('/', ',', '+', '&') }.map { it.trim().removePrefix(".") }
            return entries.filterTo(linkedSetOf()) { format -> tokens.any { it.equals(format.label, ignoreCase = true) } }
        }
    }
}
