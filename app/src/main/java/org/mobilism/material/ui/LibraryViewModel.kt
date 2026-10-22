package org.mobilism.material.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mobilism.material.data.AppLibraryStore
import org.mobilism.material.data.MobilismRepository
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.data.ReleaseVersions
import org.mobilism.material.data.SearchScope

data class InstalledApp(val packageName: String, val label: String, val version: String, val icon: Drawable, val system: Boolean)
data class InstalledState(val apps: List<InstalledApp> = emptyList(), val busy: Boolean = false, val error: ScreenError? = null)
data class UpdateCheckState(val busy: Boolean = false, val done: Int = 0, val total: Int = 0, val failed: Int = 0, val unknown: Int = 0)

class LibraryViewModel(context: Context, val library: AppLibraryStore, private val repository: MobilismRepository) : ViewModel() {
    private val packageManager = context.applicationContext.packageManager
    private val ownPackage = context.packageName
    private val mutableInstalled = MutableStateFlow(InstalledState())
    val installed = mutableInstalled.asStateFlow()
    private val mutableCheck = MutableStateFlow(UpdateCheckState())
    val check = mutableCheck.asStateFlow()
    private var installedJob: Job? = null
    private var checkJob: Job? = null

    fun refreshInstalled() {
        if (installedJob?.isActive == true) return
        installedJob = viewModelScope.launch {
            mutableInstalled.update { it.copy(busy = true, error = null) }
            try {
                val apps = withContext(Dispatchers.IO) {
                    val manager = packageManager
                    @Suppress("DEPRECATION")
                    manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .distinctBy { it.activityInfo.packageName }.mapNotNull { activity ->
                            val info = activity.activityInfo.applicationInfo
                            if (info.packageName == ownPackage) return@mapNotNull null
                            runCatching {
                                @Suppress("DEPRECATION")
                                val version = manager.getPackageInfo(info.packageName, 0).versionName.orEmpty()
                                InstalledApp(info.packageName, info.loadLabel(manager).toString(), version, info.loadIcon(manager),
                                    info.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                            }.getOrNull()
                        }.sortedBy { it.label.lowercase() }
                }
                mutableInstalled.value = InstalledState(apps)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableInstalled.update { it.copy(busy = false, error = error.forScreen()) } }
        }
    }

    fun checkUpdates() {
        if (checkJob?.isActive == true) return
        val watches = library.state.value.filter { it.watched }
        if (watches.isEmpty()) return
        checkJob = viewModelScope.launch {
            mutableCheck.value = UpdateCheckState(busy = true, total = watches.size)
            try {
                for (entry in watches) {
                    val baseline = entry.release
                    val title = ReleaseTitles.parse(baseline.title)
                    if (title.version == null || ReleaseVersions.compare(title, title) == null) {
                        mutableCheck.update { it.copy(done = it.done + 1, unknown = it.unknown + 1) }
                        continue
                    }
                    try {
                        val matches = mutableListOf<Release>()
                        val seen = mutableSetOf<Long>()
                        for (page in 1..3) {
                            val response = repository.search(title.name, SearchScope.Apps, page)
                            val fresh = response.items.filter { seen.add(it.id) }
                            matches += fresh.filter { ReleaseVersions.isNewer(it, baseline) }
                            if (!response.canLoadMore || fresh.isEmpty()) break
                        }
                        val newest = matches.maxWithOrNull { a, b ->
                            ReleaseVersions.compare(ReleaseTitles.parse(a.title), ReleaseTitles.parse(b.title)) ?: 0
                        }
                        // A prior verified match can fall outside the current search window.
                        val previous = entry.update?.takeIf { ReleaseVersions.isNewer(it, baseline) }
                        val update = if (previous != null && (newest == null ||
                            (ReleaseVersions.compare(ReleaseTitles.parse(previous.title), ReleaseTitles.parse(newest.title)) ?: 0) >= 0)) previous else newest
                        library.checked(baseline, update)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { mutableCheck.update { it.copy(failed = it.failed + 1) } }
                    mutableCheck.update { it.copy(done = it.done + 1) }
                }
            } finally { mutableCheck.update { it.copy(busy = false) } }
        }
    }
}
