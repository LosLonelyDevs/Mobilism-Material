package org.mobilism.material.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mobilism.material.data.SupportedHost

enum class CpuArchitecture(val label: String) {
    Arm64("arm64-v8a"), Arm32("armeabi-v7a"), ArmLegacy("armeabi"), X64("x86_64"), X86("x86"), Universal("Universal"),
}

data class DownloadPreferences(
    val architecture: CpuArchitecture? = null,
    val preferredHosts: Set<String> = emptySet(),
    val hosts: List<SupportedHost> = emptyList(),
)

class DownloadPreferencesStore(context: Context) {
    private val preferences = context.getSharedPreferences("material-downloads", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(DownloadPreferences(
        architecture = CpuArchitecture.entries.firstOrNull { it.name == preferences.getString("architecture", null) },
        preferredHosts = preferences.getStringSet("preferred-hosts", emptySet()).orEmpty().toSet(),
        hosts = preferences.getStringSet("host-catalog", emptySet()).orEmpty().mapNotNull { value ->
            val parts = value.split('|', limit = 2)
            if (parts.size == 2) SupportedHost(parts[0], parts[1]) else null
        }.sortedBy { it.name.lowercase() },
    ))
    val state = mutableState.asStateFlow()

    fun architecture(value: CpuArchitecture?) {
        preferences.edit().putString("architecture", value?.name).apply()
        mutableState.update { it.copy(architecture = value) }
    }

    fun hosts(values: Set<String>) {
        preferences.edit().putStringSet("preferred-hosts", values).apply()
        mutableState.update { it.copy(preferredHosts = values.toSet()) }
    }

    fun catalog(values: List<SupportedHost>) {
        preferences.edit().putStringSet("host-catalog", values.map { it.domain + "|" + it.name }.toSet()).apply()
        mutableState.update { it.copy(hosts = values) }
    }
}
