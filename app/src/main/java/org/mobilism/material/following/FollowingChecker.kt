package org.mobilism.material.following

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.mobilism.material.data.*

data class FollowCheckState(val busy: Boolean = false, val done: Int = 0, val total: Int = 0, val failed: Int = 0)

class FollowingChecker(
    private val library: AppLibraryStore, private val repository: MobilismRepository,
    private val device: InstalledApps, private val preferences: FollowingPreferences, private val notifications: FollowingNotifications,
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val latestApps: suspend (List<String>, Boolean) -> LatestAppsSnapshot = { _, _ -> LatestAppsSnapshot(CacheOutcome.Unconfigured) },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    private val publishMatches: suspend (LatestAppsPage) -> Unit = {},
    private val searchError: () -> String,
) {
    private val mutex = Mutex()
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

    suspend fun check(background: Boolean = false, onProgress: (FollowCheckState) -> Unit = {}): FollowCheckState = withContext(Dispatchers.Default) {
        checkUpdates(background, onProgress)
    }

    private suspend fun checkUpdates(background: Boolean, onProgress: (FollowCheckState) -> Unit): FollowCheckState {
        if (!mutex.tryLock()) return FollowCheckState()
        var progress = FollowCheckState()
        fun report(next: FollowCheckState) {
            progress = next
            onProgress(next)
        }
        try {
            val watched = library.state.value.filter { it.watched }
            if (watched.isEmpty()) return FollowCheckState()
            val policy = limits()
            val enabled = policy.followCacheBatchSize > 0
            val readCache = enabled && preferences.beginCacheCheck(System.currentTimeMillis(), policy, background)
            val names = watched.map { ReleaseTitles.parse(it.release.title).name }.distinctBy(::latestAppKey)
            val requested = if (readCache && background) preferences.backgroundBatch(names, policy.followCacheBatchSize) else names
            report(FollowCheckState(busy = true))
            val snapshot = if (!readCache) LatestAppsSnapshot(if (enabled) CacheOutcome.Cooldown else CacheOutcome.Disabled)
                else try { latestApps(requested, background) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { LatestAppsSnapshot(CacheOutcome.Failed) }
            val candidates = snapshot.releases
            val byName = candidates.groupBy { latestAppKey(ReleaseTitles.parse(it.title).name) }
            val wanted = requested.map(::latestAppKey).toSet()
            refreshInstalled()
            val watches = library.state.value.filter { it.watched }.sortedBy { it.checkedAt ?: 0L }
            val hits = mutableSetOf<Long>()
            val searched = mutableSetOf<Long>()
            for (entry in watches) {
                if (snapshot.outcome != CacheOutcome.Available) break
                val key = latestAppKey(ReleaseTitles.parse(entry.release.title).name)
                if (key !in wanted) continue
                searched.add(entry.release.id)
                val matches = byName[key] ?: continue
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
            val due = FollowCheckPolicy.due(watches.filter { it.release.id !in hits &&
                (snapshot.outcome != CacheOutcome.Available || it.release.id in searched) }, now, policy)
            val direct = if (due.isNotEmpty() && preferences.beginCheck(now, policy, background)) due else emptyList()
            if (direct.isEmpty() && snapshot.outcome == CacheOutcome.Cooldown) {
                diagnostics.recordCache(CacheDiagnostic(AppCache.Following, CacheOutcome.Cooldown, background = background,
                    fallbackSearches = 0), DiagnosticOperation.FollowingCheck)
                return FollowCheckState()
            }
            report(FollowCheckState(busy = true, total = hits.size + direct.size, done = hits.size))
            if (direct.isNotEmpty() && repository.auth.value == AuthState.Restoring) repository.restoreSession()
            var searches = 0
            val observations = mutableListOf<LatestAppsPage>()
            for (original in direct) {
                val entry = library.state.value.firstOrNull { it.watched && it.release.id == original.release.id } ?: continue
                try {
                    searches++
                    val observation = FollowSearchObservation()
                    val page = withContext(observation) {
                        repository.search(ReleaseTitles.parse(entry.release.title).name, SearchScope.Apps, 1)
                    }
                    refreshInstalled()
                    applyResult(entry, FollowingVersions.matchingInstalled(entry, installed.value), page.items)
                    report(progress.copy(done = progress.done + 1))
                    if (enabled) observation.contribution(entry.release, page)?.let(observations::add)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    library.failed(entry, searchError())
                    report(progress.copy(failed = progress.failed + 1))
                    break
                }
            }
            // A slow search batch can outlast the endpoint's two-minute upload window.
            val cutoff = System.currentTimeMillis() / 1000 - 115
            for (pages in observations.filter { it.readAt >= cutoff }.groupBy { it.adultFiltered }.values) {
                val matches = pages.flatMap { it.releases }.distinctBy { it.id }
                try {
                    publishMatches(pages.first().copy(releases = matches, readAt = pages.minOf { it.readAt }))
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* The fallback search already updated the device. */ }
            }
            val present = if (snapshot.outcome == CacheOutcome.Available) wanted.count(byName::containsKey) else 0
            val missing = if (snapshot.outcome == CacheOutcome.Available) snapshot.missingNames ?: (wanted.size - present) else 0
            diagnostics.recordCache(CacheDiagnostic(AppCache.Following, snapshot.outcome, hits = present,
                misses = missing, items = candidates.size, background = background, fallbackSearches = searches),
                DiagnosticOperation.FollowingCheck, snapshot.httpStatus)
        } finally {
            mutex.unlock()
            report(progress.copy(busy = false))
        }
        return progress
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
            if (saved.lastNotifiedVersion != key &&
                notifications.post(saved, update, FollowingVersions.baseline(saved, current), current != null))
                library.notified(entry.release.id, key)
        }
    }
}
