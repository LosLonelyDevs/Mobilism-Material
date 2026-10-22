package org.mobilism.material.following

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mobilism.material.data.*

data class FollowCheckState(val busy: Boolean = false, val done: Int = 0, val total: Int = 0, val failed: Int = 0)

class FollowingChecker(
    private val library: AppLibraryStore, private val repository: MobilismRepository,
    private val device: InstalledApps, private val preferences: FollowingPreferences, private val notifications: FollowingNotifications,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(FollowCheckState())
    val state = mutableState.asStateFlow()
    private val mutableInstalled = MutableStateFlow<List<InstalledVersion>>(emptyList())
    val installed = mutableInstalled.asStateFlow()

    suspend fun refreshInstalled() = withContext(Dispatchers.IO) {
        val known = library.state.value.filter { it.watched }.mapNotNull { it.linkedPackage ?: ReleaseVersions.packageName(it.release) }
        val apps = (device.list() + known.mapNotNull(device::read)).distinctBy { it.packageName }
        mutableInstalled.value = apps
        library.state.value.filter { it.watched }.forEach { entry ->
            val current = FollowingVersions.matchingInstalled(entry, apps)
            if (entry.linkedPackage == null && current != null) library.linkPackage(entry.release.id, current.packageName)
            if (current != null && FollowingVersions.evaluate(entry, current, emptyList()).update == null) notifications.cancel(entry.release.id)
        }
    }

    suspend fun check(): FollowCheckState = mutex.withLock {
        val watches = library.state.value.filter { it.watched }
        mutableState.value = FollowCheckState(busy = true, total = watches.size)
        try {
            if (repository.auth.value == AuthState.Restoring) repository.restoreSession()
            refreshInstalled()
            for (original in watches) {
                val entry = library.state.value.firstOrNull { it.watched && it.release.id == original.release.id } ?: continue
                try {
                    val candidates = mutableListOf<Release>()
                    val seen = mutableSetOf<Long>()
                    for (page in 1..3) {
                        val response = repository.search(ReleaseTitles.parse(entry.release.title).name, SearchScope.Apps, page)
                        val fresh = response.items.filter { seen.add(it.id) }
                        candidates += fresh
                        if (!response.canLoadMore || fresh.isEmpty()) break
                    }
                    // Read again after the request. An install can finish while the search runs.
                    refreshInstalled()
                    val current = FollowingVersions.matchingInstalled(entry, installed.value)
                    val result = FollowingVersions.evaluate(entry, current, candidates)
                    library.checked(entry, result.latest, result.update)
                    val stillFollowed = library.state.value.firstOrNull { it.watched && it.release.id == entry.release.id && it.linkedPackage == entry.linkedPackage }
                    val update = result.update
                    if (stillFollowed != null && update != null) {
                        val key = FollowingVersions.notificationKey(update)
                        if (preferences.state.value.notifications && stillFollowed.lastNotifiedVersion != key &&
                            notifications.post(stillFollowed, update, FollowingVersions.baseline(entry, current), current != null)) {
                            library.notified(entry.release.id, key)
                        }
                    } else notifications.cancel(entry.release.id)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    library.failed(entry, "Couldn't check for updates. Try again.")
                    mutableState.update { it.copy(failed = it.failed + 1) }
                }
                mutableState.update { it.copy(done = it.done + 1) }
            }
        } finally { mutableState.update { it.copy(busy = false) } }
        mutableState.value
    }
}
