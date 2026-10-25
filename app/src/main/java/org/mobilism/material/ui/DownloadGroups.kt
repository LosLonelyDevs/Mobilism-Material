package org.mobilism.material.ui

import org.mobilism.material.data.HostMatcher
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.settings.CpuArchitecture
import org.mobilism.material.settings.DownloadPreferences

data class DownloadGroup(
    val title: String, val links: List<DownloadLink>, val collapsed: Boolean,
    val id: String = title, val notesHtml: String = "",
)

object DownloadGroups {
    val architecturePattern = Regex("""(?i)\b(arm64[-_]v8a|arm64|aarch64|armeabi[-_]v7a|armeabi|armv?7[al]?|x86[_-]64|x86|universal)\b""")

    fun architectures(label: String): Set<CpuArchitecture> = architecturePattern.findAll(label).map { match ->
        when (match.value.lowercase()) {
            "arm64-v8a", "arm64_v8a", "arm64", "aarch64" -> CpuArchitecture.Arm64
            "armeabi-v7a", "armeabi_v7a", "arm7", "arm7a", "arm7l", "armv7", "armv7a", "armv7l" -> CpuArchitecture.Arm32
            "armeabi" -> CpuArchitecture.ArmLegacy
            "x86_64", "x86-64" -> CpuArchitecture.X64
            "x86" -> CpuArchitecture.X86
            else -> CpuArchitecture.Universal
        }
    }.toSet()

    fun label(value: String): String? = architectures(value).takeIf { it.isNotEmpty() }
        ?.let { Alphabetical.sorted(it) { architecture -> architecture.label } }?.joinToString(" / ") { it.label }

    fun isArchitectureOnly(value: String): Boolean = architectures(value).isNotEmpty() &&
        architecturePattern.replace(value, "").trim(' ', ':', '/', '+', ',', '|', '-', '[', ']', '(', ')').isEmpty()

    fun normalizedLabel(value: String): String = if (isArchitectureOnly(value)) label(value).orEmpty()
        else value.trim().trimEnd(':').trim()

    fun group(links: List<DownloadLink>, preferences: DownloadPreferences): List<DownloadGroup> =
        links.groupBy { link -> link.groupId?.let { "section-$it" }
            ?: "label-${normalizedLabel(link.section.orEmpty()).lowercase(java.util.Locale.ROOT)}" }.map { (id, items) ->
            val title = normalizedLabel(items.first().section.orEmpty()).ifEmpty { "Download links" }
            val architectures = architectures(items.first().section.orEmpty())
            val preferred = preferences.architecture
            DownloadGroup(
                title = title,
                links = Alphabetical.sorted(items) { HostMatcher.displayName(it.url) }.sortedBy { link -> preferences.preferredHosts.none {
                    HostMatcher.key(link.url) == it || HostMatcher.matches(link.url, it)
                } },
                // Unlabeled downloads stay visible: their compatibility is unknown.
                collapsed = preferred != null && architectures.isNotEmpty() && preferred !in architectures &&
                    CpuArchitecture.Universal !in architectures,
                id = id,
                notesHtml = items.map { it.notesHtml }.filter { it.isNotBlank() }.distinct().joinToString("<br>"),
            )
        }.let { Alphabetical.sorted(it) { group -> group.title } }.sortedBy { it.collapsed }
}
