package org.mobilism.material

import android.app.Application
import org.mobilism.material.data.DefaultMobilismRepository
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.settings.AppearanceStore
import org.mobilism.material.data.AppLibraryStore
import org.mobilism.material.following.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class MobilismApplication : Application() {
    val repository by lazy { DefaultMobilismRepository(this) }
    val appearance by lazy { AppearanceStore(this) }
    val debrid by lazy { DebridRepository(this) }
    val library by lazy { AppLibraryStore(this) }
    val followPreferences by lazy { FollowingPreferences(this) }
    val followNotifications by lazy { FollowingNotifications(this) }
    val following by lazy { FollowingChecker(library, repository, InstalledApps(this), followPreferences, followNotifications) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            combine(library.state, followPreferences.state) { entries, preferences ->
                followNotifications.retain(entries.filter { it.watched }.map { it.release.id }.toSet())
                (preferences.backgroundChecks && entries.any { it.watched }) to preferences.intervalHours
            }.distinctUntilChanged().collect { (enabled, hours) -> FollowingSchedule.update(this@MobilismApplication, enabled, hours) }
        }
    }
}
