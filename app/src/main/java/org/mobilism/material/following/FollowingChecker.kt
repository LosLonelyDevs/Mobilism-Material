package org.mobilism.material.following

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.mobilism.material.data.*

data class FollowCheckState(val busy: Boolean = false, val done: Int = 0, val total: Int = 0, val failed: Int = 0)

class FollowingChecker(
    private val library: AppLibraryStore, private val repository: MobilismRepository,
    private val device: InstalledApps, private val preferences: FollowingPreferences, private val notifications: FollowingNotifications,
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val latestApps: suspend () -> LatestAppsSnapshot = { LatestAppsSnapshot(CacheOutcome.Unconfigured) },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
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

    suspend fun check(background: Boolean = false): FollowCheckState = withContext(Dispatchers.Default) {
        checkUpdates(background)
    }

    private suspend fun checkUpdates(background: Boolean): FollowCheckState {
        if (!mutex.tryLock()) return mutableState.value
        try {
            if (library.state.value.none { it.watched }) return FollowCheckState()
            val policy = limits()
            mutableState.value = FollowCheckState(busy = true)
            val snapshot = if (policy.followCacheBatchSize == 0) LatestAppsSnapshot(CacheOutcome.Disabled) else try { latestApps() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { LatestAppsSnapshot(CacheOutcome.Failed) }
            val candidates = snapshot.releases
            val byName = candidates.groupBy { ReleaseTitles.parse(it.title).name.lowercase(java.util.Locale.ROOT) }
            refreshInstalled()
            val watches = library.state.value.filter { it.watched }.sortedBy { it.checkedAt ?: 0L }
            val hits = mutableSetOf<Long>()
            var compared = 0
            for (entry in watches) {
                if (snapshot.outcome != CacheOutcome.Available || hits.size >= policy.followCacheBatchSize) break
                compared++
                val matches = byName[ReleaseTitles.parse(entry.release.title).name.lowercase(java.util.Locale.ROOT)] ?: continue
                val current = FollowingVersions.matchingInstalled(entry, installed.value)
                val baseline = FollowingVersions.baseline(entry, current)
                val compatible = matches.filter { release ->
                    val title = ReleaseTitles.parse(release.title)
                    ReleaseVersions.sameApp(release, entry.release) &&
                        (ReleaseVersions.isPreview(baseline) || !ReleaseVersions.isPreview(title)) &&
                        (ReleaseVersions.compare(title, baseline) ?: -1) >= 0
                }
                if (compatible.isEmpty()) continue
                applyResult(entry, current, compatible)
                hits.add(entry.release.id)
            }
            val now = System.currentTimeMillis()
            val due = FollowCheckPolicy.due(watches.filterNot { it.release.id in hits }, now, policy)
            val direct = if (due.isNotEmpty() && preferences.beginCheck(now, policy, background)) due else emptyList()
            mutableState.value = FollowCheckState(busy = true, total = hits.size + direct.size, done = hits.size)
            if (direct.isNotEmpty() && repository.auth.value == AuthState.Restoring) repository.restoreSession()
            var searches = 0
            for (original in direct) {
                val entry = library.state.value.firstOrNull { it.watched && it.release.id == original.release.id } ?: continue
                try {
                    searches++
                    val results = repository.search(ReleaseTitles.parse(entry.release.title).name, SearchScope.Apps, 1).items
                    refreshInstalled()
                    applyResult(entry, FollowingVersions.matchingInstalled(entry, installed.value), results)
                    mutableState.update { it.copy(done = it.done + 1) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    library.failed(entry, "Couldn't check for updates. Try again.")
                    mutableState.update { it.copy(failed = it.failed + 1) }
                    break
                }
            }
            diagnostics.recordCache(CacheDiagnostic(AppCache.Following, snapshot.outcome, hits = hits.size,
                misses = compared - hits.size, items = candidates.size, background = background, fallbackSearches = searches),
                DiagnosticOperation.FollowingCheck, snapshot.httpStatus)
        } finally {
            mutableState.update { it.copy(busy = false) }
            mutex.unlock()
        }
        return mutableState.value
    }

    private fun applyResult(entry: LibraryEntry, current: InstalledVersion?, candidates: List<Release>) {
        val followed = library.state.value.firstOrNull { it.watched && it.release.id == entry.release.id &&
            it.linkedPackage == entry.linkedPackage } ?: return
        val result = FollowingVersions.evaluate(followed, current, candidates)
        library.checked(followed, result.latest, result.update)
        val saved = library.state.value.firstOrNull { it.watched && it.release.id == followed.release.id &&
            it.linkedPackage == followed.linkedPackage } ?: return
        val update = result.update
        if (update == null) {
            notifications.cancel(entry.release.id)
        } else {
            val key = FollowingVersions.notificationKey(update)
            if (preferences.state.value.notifications && saved.lastNotifiedVersion != key &&
                notifications.post(saved, update, FollowingVersions.baseline(saved, current), current != null))
                library.notified(entry.release.id, key)
        }
    }
}
