package org.mobilism.material.data

data class CachedInstalledRelease(val id: Long, val name: String, val version: String, val packageName: String?)

class InstalledReleaseCatalog(releases: List<CachedInstalledRelease> = emptyList()) {
    private val packages = releases.filter { it.packageName != null }.groupBy { it.packageName }
    private val names = releases.groupBy { latestAppKey(it.name) }

    fun match(packageName: String, name: String, uniqueLabel: Boolean): CachedInstalledRelease? {
        packages[packageName]?.let { return it.first() } // The snapshot is newest first.
        if (!uniqueLabel) return null
        return names[latestAppKey(name)]?.singleOrNull()?.takeIf { it.packageName == null }
    }
}
