package org.mobilism.material.ui

import org.jsoup.Jsoup

/** Advertising footers support a guess, but are not enough to turn nearby marketing into mod info. */
internal object ReleaseModHeuristics {
    private val titleTag = Regex("""\[([^\[\]]+)]""")
    private val modifiedTag = Regex("""(?i)^(?:mod(?:ded)?|patched|unlocked|premium|pro)(?:\s.*)?$""")
    private val version = Regex("""(?i)^(.+?)\s+v?\d+(?:\.\d+)+(?:[-+][\w.-]+)?$""")
    private val bullet = Regex("""^(?:[-•●◉✓✔★]|\d+[.)])\s*""")
    private val decoration = Regex("""^[\s\p{So}\p{Sk}\p{Cf}*#_\[\]<>|=~\-]+|[\s\p{So}\p{Sk}\p{Cf}*#_\[\]<>|=~\-:]+$""")
    private val strongFeatures = listOf(
        Regex("""(?i)^(?:all\s+)?(?:premium|pro|vip|paid|subscription|full)(?:\s+\w+){0,2}\s+(?:unlocked|activated|enabled|free)\b"""),
        Regex("""(?i)^(?:unlocked|activated|enabled)\s+(?:all\s+)?(?:premium|pro|vip|paid|full|subscription)\b"""),
        Regex("""(?i)^(?:all\s+)?(?:ads?|advertisements?|analytics|trackers?|tracking|telemetry|debug\s+(?:info(?:rmation)?|logs?)|license(?:\s+verification)?|signature\s+(?:checks?|verification)|billing|restrictions?)(?:\s*[/,&+]\s*\w+|\s+\w+){0,2}\s+(?:removed|disabled|blocked|bypassed|patched)\b"""),
        Regex("""(?i)^(?:removed|disabled|blocked|bypassed)\s+(?:all\s+)?(?:ads?|advertisements?|analytics|trackers?|tracking|telemetry|license|signature|billing|restrictions?)\b"""),
        Regex("""(?i)^(?:no\s+(?:root|license\s+(?:check|verification))\s+(?:needed|required)|unlimited\s+(?:coins|gems|credits|lives)|mod\s+menu|patched\s+(?:license|signature))\b"""),
    )
    private val weakFeatures = Regex("""(?i)\b(?:amoled|accent\s+colou?r|ghost\s+mode|spoof|custom\s+theme|hide\s+\w+|block\s+\w+)\b""")
    private val purchaseCondition = Regex("""(?i)\b(?:(?:when|after|once|if|by|with|requires?)\s+(?:you\s+)?(?:subscrib\w*|purchas\w*|pay\w*|buy\w*)|(?:with|requires?)\s+(?:a\s+)?(?:paid\s+)?subscription|upgrade\s+to\s+(?:premium|pro))\b""")
    private val note = Regex("""(?i)^(?:notes?\s*:|important\s*:|installation\s*:|install\b|uninstall\b|changes\s+and\s+bug\s+fixes|mod\s+changelog)""")

    fun classify(lines: List<ReleaseLine>, releaseTitle: String) {
        val title = Jsoup.parseBodyFragment(releaseTitle).text()
        val appName = Regex("""(?i)^(.+?)\s+v\d""").find(title)?.groupValues?.get(1)?.lowercase()
        val tags = titleTag.findAll(title).map { it.groupValues[1].trim() }.toList()
        val names = tags.filterNot(modifiedTag::matches).map { it.lowercase() }.toSet()
        val modifiedRelease = tags.any(modifiedTag::matches)
        fun eligible(index: Int) = index in lines.indices && !lines[index].boundary && lines[index].kind == ReleaseSection.Description
        fun label(index: Int) = lines[index].text.trim().replace(decoration, "").trim()
        fun featureText(index: Int) = lines[index].text.trim().replaceFirst(bullet, "").trim()
        fun strong(index: Int): Boolean {
            val text = featureText(index)
            return !purchaseCondition.containsMatchIn(text) && strongFeatures.any { it.containsMatchIn(text) }
        }
        fun isBullet(index: Int) = lines[index].listItem || bullet.containsMatchIn(lines[index].text.trim())
        fun headerName(index: Int) = (version.matchEntire(label(index))?.groupValues?.get(1) ?: label(index)).lowercase()
        fun header(index: Int): Boolean {
            val text = label(index)
            return text.length <= 90 && text.split(Regex("""\s+""")).size <= 10 && !isBullet(index) && !strong(index) &&
                !text.endsWith('.') && !lines[index].linkOnly &&
                (lines[index].styled || version.matches(text) || headerName(index) in names)
        }
        fun nearbyAdvertising(start: Int, end: Int): Boolean {
            fun scan(from: Int, step: Int): Boolean {
                var index = from
                repeat(7) {
                    if (index !in lines.indices) return false
                    if (lines[index].advertising) return true
                    if (lines[index].boundary || lines[index].kind == ReleaseSection.Changes) return false
                    index += step
                }
                return false
            }
            return scan(start - 1, -1) || scan(end, 1)
        }

        var start = 0
        while (start < lines.size) {
            if (!eligible(start)) { start++; continue }
            val hasHeader = header(start)
            var end = if (hasHeader) start + 1 else start
            val firstFeature = end
            while (eligible(end) && (isBullet(end) || strong(end))) end++
            if (end == firstFeature) { start++; continue }
            val features = firstFeature until end
            val strongCount = features.count(::strong)
            val weakCount = features.count { weakFeatures.containsMatchIn(featureText(it)) }.coerceAtMost(2)
            val named = hasHeader && headerName(start) in names
            val versioned = hasHeader && version.matches(label(start)) && headerName(start) != appName
            val advertising = nearbyAdvertising(start, end)
            val structured = features.any(::isBullet)
            val score = strongCount.coerceAtMost(2) * 3 + weakCount +
                (if (named) 3 else 0) + (if (hasHeader && lines[start].styled) 1 else 0) +
                (if (versioned) 1 else 0) + (if (structured) 1 else 0) +
                (if (modifiedRelease) 1 else 0) + (if (advertising) 3 else 0)
            val hasModClue = strongCount > 0 || named || (versioned && weakCount == 2 && advertising && modifiedRelease)
            if (hasModClue && score >= 6) {
                for (index in start until end) lines[index].kind = ReleaseSection.Mod
                // Keep adjacent source links and installation notes with the feature list.
                var notes = 0
                while (eligible(end) && notes < 4 && (lines[end].linkOnly || note.containsMatchIn(label(end)))) {
                    lines[end++].kind = ReleaseSection.Mod
                    notes++
                }
                start = end
            } else start = end
        }
    }
}
