package org.mobilism.material.data

import java.math.BigInteger
import java.util.Locale
import org.jsoup.Jsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Conservative identity/version matching: never infer an update from posting date alone. */
object ReleaseVersions {
    fun packageName(release: Release): String? = Jsoup.parse(release.contentHtml).select("a[href]")
        .mapNotNull { it.attr("href").toHttpUrlOrNull() }
        .firstOrNull { it.host == "play.google.com" && it.encodedPath == "/store/apps/details" }
        ?.queryParameter("id")?.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")) }

    fun identity(release: Release): String = packageName(release)?.let { "package:$it" }
        ?: "title:" + normalizedName(ReleaseTitles.parse(release.title).name)

    private fun normalizedName(value: String) = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    fun sameApp(first: Release, second: Release): Boolean {
        val firstTitle = ReleaseTitles.parse(first.title)
        val secondTitle = ReleaseTitles.parse(second.title)
        if (television(firstTitle) != television(secondTitle)) return false
        val firstPackage = packageName(first)
        val secondPackage = packageName(second)
        if (firstPackage != null && secondPackage != null) return firstPackage == secondPackage
        return normalizedName(ReleaseTitles.parse(first.title).name) == normalizedName(ReleaseTitles.parse(second.title).name)
    }

    private fun television(title: ReleaseTitle) = Regex("(?i)\\b(?:android\\s*)?tv\\b")
        .containsMatchIn((listOf(title.name) + title.tags).joinToString(" "))

    fun isNewer(candidate: Release, current: Release): Boolean {
        if (candidate.id == current.id || !sameApp(candidate, current)) return false
        val old = ReleaseTitles.parse(current.title)
        val next = ReleaseTitles.parse(candidate.title)
        // Stable installations stay on stable releases; beta watchers can advance to stable.
        return newerThan(next, old)
    }

    fun newerThan(candidate: ReleaseTitle, current: ReleaseTitle): Boolean =
        (isPreview(current) || !isPreview(candidate)) && compare(candidate, current)?.let { it > 0 } == true

    fun compare(first: ReleaseTitle, second: ReleaseTitle): Int? {
        val a = numeric(first.version) ?: return null
        val b = numeric(second.version) ?: return null
        val version = compareParts(a, b)
        if (version != 0) return version
        val channel = rank(first).compareTo(rank(second))
        if (channel != 0) return channel
        val buildA = first.build?.takeIf { it.all(Char::isDigit) }?.toBigIntegerOrNull()
        val buildB = second.build?.takeIf { it.all(Char::isDigit) }?.toBigIntegerOrNull()
        return if (buildA != null && buildB != null) buildA.compareTo(buildB) else 0
    }

    private fun numeric(version: String?): List<BigInteger>? = version?.let {
        if (!Regex("(?i)^\\d+(?:\\.\\d+)*(?:[-.]?(?:alpha|beta|rc)\\d*)?(?:\\+[\\w.-]+)?$").matches(it)) return null
        Regex("^\\d+(?:\\.\\d+)*").find(it)?.value?.split('.')?.map(String::toBigInteger)
    }
    private fun compareParts(a: List<BigInteger>, b: List<BigInteger>): Int {
        repeat(maxOf(a.size, b.size)) { index ->
            val result = (a.getOrNull(index) ?: BigInteger.ZERO).compareTo(b.getOrNull(index) ?: BigInteger.ZERO)
            if (result != 0) return result
        }
        return 0
    }
    private fun rank(title: ReleaseTitle): Int {
        val text = (listOfNotNull(title.version) + title.tags).joinToString(" ")
        return when {
            Regex("(?i)(?<![a-z])alpha(?=\\d|\\b)").containsMatchIn(text) -> 0
            Regex("(?i)(?<![a-z])beta(?=\\d|\\b)").containsMatchIn(text) -> 1
            Regex("(?i)(?<![a-z])rc(?=\\d|\\b)").containsMatchIn(text) -> 2
            else -> 3
        }
    }
    fun isPreview(title: ReleaseTitle) = rank(title) < 3
}
