package org.mobilism.material.data

import java.util.Locale

data class InstalledVersion(val packageName: String, val name: String, val version: String, val versionCode: Long)
data class FollowingVersionResult(val latest: Release?, val update: Release?, val comparable: Boolean)

object FollowingVersions {
    fun baseline(entry: LibraryEntry, installed: InstalledVersion?): ReleaseTitle = installed?.let {
        ReleaseTitle(it.name, it.version.trim().removePrefix("v"), it.versionCode.toString())
    } ?: ReleaseTitles.parse(entry.release.title)

    fun matchingInstalled(entry: LibraryEntry, apps: List<InstalledVersion>): InstalledVersion? {
        val known = entry.linkedPackage ?: ReleaseVersions.packageName(entry.release)
        if (known != null) return apps.firstOrNull { it.packageName == known }
        val name = normalize(ReleaseTitles.parse(entry.release.title).name)
        return apps.filter { normalize(it.name) == name }.singleOrNull()
    }

    fun evaluate(entry: LibraryEntry, installed: InstalledVersion?, candidates: List<Release>): FollowingVersionResult {
        val current = baseline(entry, installed)
        val linked = ReleaseVersions.packageName(entry.release) ?: entry.linkedPackage ?: installed?.packageName
        val latest = (candidates + listOfNotNull(entry.latest, entry.update, entry.release)).distinctBy { it.id }
            .filter { candidate ->
                val candidatePackage = ReleaseVersions.packageName(candidate)
                ReleaseVersions.sameApp(candidate, entry.release) &&
                    (linked == null || candidatePackage == null || candidatePackage == linked) &&
                    (ReleaseVersions.isPreview(current) || !ReleaseVersions.isPreview(ReleaseTitles.parse(candidate.title))) &&
                    ReleaseVersions.compare(ReleaseTitles.parse(candidate.title), ReleaseTitles.parse(candidate.title)) != null
            }.maxWithOrNull { a, b -> ReleaseVersions.compare(ReleaseTitles.parse(a.title), ReleaseTitles.parse(b.title)) ?: 0 }
        return FollowingVersionResult(latest, latest?.takeIf { ReleaseVersions.newerThan(ReleaseTitles.parse(it.title), current) },
            ReleaseVersions.compare(current, current) != null)
    }

    fun notificationKey(release: Release): String {
        val title = ReleaseTitles.parse(release.title)
        return listOf(title.version?.lowercase(Locale.ROOT), title.build, ReleaseVersions.isPreview(title).toString()).joinToString("|")
    }

    private fun normalize(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
