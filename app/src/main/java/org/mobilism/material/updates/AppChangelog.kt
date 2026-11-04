package org.mobilism.material.updates

import android.content.Context
import androidx.core.content.edit
import org.mobilism.material.BuildConfig
import org.mobilism.material.data.AppVersions

internal data class AppChangelog(val version: String, val bullets: List<String>) {
    companion object {
        fun pending(current: String, lastSeen: String?, notes: String): AppChangelog? {
            if (lastSeen != null && !AppVersions.newer(current, lastSeen)) return null
            val bullets = notes.lineSequence().map(String::trim).filter { it.startsWith("- ") }
                .map { it.removePrefix("- ") }.filter(String::isNotBlank).toList()
            return bullets.takeIf { it.isNotEmpty() }?.let { AppChangelog(current, it) }
        }
    }
}

internal class AppChangelogStore(context: Context) {
    private val prefs = context.getSharedPreferences("material-changelog", Context.MODE_PRIVATE)
    fun pending(): AppChangelog? = AppChangelog.pending(BuildConfig.VERSION_NAME,
        prefs.getString("last-seen-version", null), BuildConfig.RELEASE_NOTES)
    fun markSeen(version: String) { prefs.edit { putString("last-seen-version", version) } }
}
