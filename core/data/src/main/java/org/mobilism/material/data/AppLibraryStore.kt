package org.mobilism.material.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences("app-library", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(runCatching { LibraryJson.decode(preferences.getString("entries", "[]")!!) }.getOrDefault(emptyList()))
    val state = mutableState.asStateFlow()

    @Synchronized
    private fun change(transform: (List<LibraryEntry>) -> List<LibraryEntry>) {
        val next = transform(mutableState.value)
        preferences.edit().putString("entries", LibraryJson.encode(next)).apply()
        mutableState.value = next
    }

    fun watch(release: Release, enabled: Boolean) = change { AppLibrary.watch(it, release, enabled) }
    fun downloaded(release: Release, enabled: Boolean) = change { AppLibrary.downloaded(it, release, enabled) }
    fun remove(entry: LibraryEntry) = change { entries -> entries.filterNot { it.release.id == entry.release.id } }
    fun restore(entry: LibraryEntry) = change { entries ->
        if (entries.any { it.release.id == entry.release.id }) entries
        else (listOf(entry.copy(watched = entry.watched && entries.none { it.watched && ReleaseVersions.sameApp(it.release, entry.release) })) + entries)
            .filter { it.watched || it.downloaded }
    }
    fun checked(baseline: Release, update: Release?) = change { entries ->
        entries.map {
            if (it.watched && it.release.id == baseline.id) it.copy(checkedAt = System.currentTimeMillis(), update = update?.let(AppLibrary::compact)) else it
        }
    }
}
