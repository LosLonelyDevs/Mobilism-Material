package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mobilism.material.data.SupportedHost
import org.mobilism.material.data.HostMatcher
import org.mobilism.material.data.Alphabetical

enum class CpuArchitecture(val label: String) {
    Arm64("arm64-v8a"), Arm32("armeabi-v7a"), ArmLegacy("armeabi"), X64("x86_64"), X86("x86"), Universal("Universal"),
}

data class DownloadPreferences(
    val architecture: CpuArchitecture? = null,
    val preferredHosts: Set<String> = emptySet(),
    val hosts: List<SupportedHost> = emptyList(),
    val autoWatchDownloads: Boolean = true,
)

class DownloadPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-downloads", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(DownloadPreferences(
        autoWatchDownloads = preferences.getBoolean("auto-watch-downloads", true),
        architecture = CpuArchitecture.entries.firstOrNull { it.name == preferences.getString("architecture", null) },
        preferredHosts = preferences.getStringSet("preferred-hosts", emptySet()).orEmpty().toSet(),
        hosts = preferences.getStringSet("host-catalog", emptySet()).orEmpty().mapNotNull { value ->
            val parts = value.split('|', limit = 2)
            if (parts.size == 2) SupportedHost(parts[0], parts[1]) else null
        }.let { org.mobilism.material.data.Alphabetical.sorted(it) { host -> host.name } },
    ))
    val state = mutableState.asStateFlow()

    fun autoWatchDownloads(enabled: Boolean) {
        preferences.edit().putBoolean("auto-watch-downloads", enabled).apply()
        mutableState.update { it.copy(autoWatchDownloads = enabled) }
    }

    fun architecture(value: CpuArchitecture?) {
        preferences.edit().putString("architecture", value?.name).apply()
        mutableState.update { it.copy(architecture = value) }
    }

    fun hosts(values: Set<String>) {
        preferences.edit().putStringSet("preferred-hosts", values).apply()
        mutableState.update { it.copy(preferredHosts = values.toSet()) }
    }

    fun catalog(values: List<SupportedHost>) {
        mergeCatalog(values, existingFirst = false)
    }

    fun discover(urls: List<String>) {
        mergeCatalog(urls.mapNotNull { url ->
            HostMatcher.key(url)?.takeUnless { HostMatcher.matches(it, "mobilism.org") }
                ?.let { SupportedHost(it, HostMatcher.displayName(url)) }
        }, existingFirst = true)
    }

    @Synchronized
    private fun mergeCatalog(values: List<SupportedHost>, existingFirst: Boolean) {
        val existing = mutableState.value.hosts
        val merged = (if (existingFirst) existing + values else values + existing)
            .mapNotNull { host -> HostMatcher.key(host.domain)?.let { host.copy(domain = it) } }
            .distinctBy { it.domain }.let { Alphabetical.sorted(it) { host -> host.name } }
        if (merged == existing) return
        preferences.edit().putStringSet("host-catalog", merged.map { it.domain + "|" + it.name }.toSet()).apply()
        mutableState.update { it.copy(hosts = merged) }
    }
}
