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
    fun downloaded(release: Release, enabled: Boolean, autoWatch: Boolean = false) =
        change { AppLibrary.downloaded(it, release, enabled, autoWatch) }
    fun remove(entry: LibraryEntry) = change { entries -> entries.filterNot { it.release.id == entry.release.id } }
    fun restore(entry: LibraryEntry) = change { AppLibrary.restore(it, entry) }

    fun checked(snapshot: LibraryEntry, latest: Release?, update: Release?) = change { entries -> entries.map {
        if (it.watched && it.release.id == snapshot.release.id && it.linkedPackage == snapshot.linkedPackage)
            it.copy(checkedAt = System.currentTimeMillis(), latest = latest?.let(AppLibrary::compact),
                update = update?.let(AppLibrary::compact), checkError = null)
        else it
    } }
    fun failed(snapshot: LibraryEntry, message: String) = change { entries -> entries.map {
        if (it.watched && it.release.id == snapshot.release.id && it.linkedPackage == snapshot.linkedPackage) it.copy(checkError = message) else it
    } }
    fun linkPackage(id: Long, packageName: String?) = change { entries -> entries.map {
        if (it.release.id == id && it.linkedPackage != packageName) it.copy(linkedPackage = packageName,
            update = null, latest = null, checkedAt = null, checkError = null, lastNotifiedVersion = null) else it
    } }
    fun notified(id: Long, version: String) = change { entries -> entries.map {
        if (it.watched && it.release.id == id) it.copy(lastNotifiedVersion = version) else it
    } }
}
