package org.mobilism.material.ui

import org.mobilism.material.data.HostMatcher
import org.mobilism.material.settings.CpuArchitecture
import org.mobilism.material.settings.DownloadPreferences

data class DownloadGroup(val title: String, val links: List<DownloadLink>, val collapsed: Boolean)

object DownloadGroups {
    val architecturePattern = Regex("""(?i)\b(arm64[-_]v8a|arm64|aarch64|armeabi[-_]v7a|armeabi|armv7[al]?|x86[_-]64|x86|universal)\b""")

    fun architectures(label: String): Set<CpuArchitecture> = architecturePattern.findAll(label).map { match ->
        when (match.value.lowercase()) {
            "arm64-v8a", "arm64_v8a", "arm64", "aarch64" -> CpuArchitecture.Arm64
            "armeabi-v7a", "armeabi_v7a", "armv7", "armv7a", "armv7l" -> CpuArchitecture.Arm32
            "armeabi" -> CpuArchitecture.ArmLegacy
            "x86_64", "x86-64" -> CpuArchitecture.X64
            "x86" -> CpuArchitecture.X86
            else -> CpuArchitecture.Universal
        }
    }.toSet()

    fun label(value: String): String? = architectures(value).takeIf { it.isNotEmpty() }
        ?.sortedBy { it.ordinal }?.joinToString(" / ") { it.label }

    fun group(links: List<DownloadLink>, preferences: DownloadPreferences): List<DownloadGroup> =
        links.groupBy { link -> architectures(link.section.orEmpty()) }.map { (architectures, items) ->
            val preferred = preferences.architecture
            DownloadGroup(
                title = architectures.sortedBy { it.ordinal }.joinToString(" / ") { it.label }.ifEmpty { "Unspecified architecture" },
                links = items.sortedBy { link -> preferences.preferredHosts.none {
                    HostMatcher.key(link.url) == it || HostMatcher.matches(link.url, it)
                } },
                // Unlabeled downloads stay visible: their compatibility is unknown.
                collapsed = preferred != null && architectures.isNotEmpty() && preferred !in architectures &&
                    CpuArchitecture.Universal !in architectures,
            )
        }.sortedBy { it.collapsed }
}
