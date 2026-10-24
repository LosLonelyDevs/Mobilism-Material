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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collectLatest
import org.mobilism.material.data.DiagnosticEvent
import org.mobilism.material.data.DiagnosticSource
import org.mobilism.material.data.DiagnosticOperation
import org.mobilism.material.data.DiagnosticPhase
import org.mobilism.material.data.DiagnosticFailure
import org.mobilism.material.settings.DeveloperPreferencesStore
import org.mobilism.material.settings.DiagnosticLogStore

class MobilismApplication : Application() {
    val contentPreferences by lazy { org.mobilism.material.data.ContentPreferencesStore(this) }
    val developer by lazy { DeveloperPreferencesStore(this) }
    val diagnostics by lazy { DiagnosticLogStore(java.io.File(filesDir, "diagnostics"),
        { developer.state.value.enabled && developer.state.value.verboseLogging }) }
    val repository by lazy { DefaultMobilismRepository(this, contentPreferences, diagnostics) }
    val appearance by lazy { AppearanceStore(this) }
    val debrid by lazy { DebridRepository(this, isEnabled = {
        developer.state.value.debridEnabled
    }, diagnostics = diagnostics) }
    val library by lazy { AppLibraryStore(this) }
    val followPreferences by lazy { FollowingPreferences(this) }
    val followNotifications by lazy { FollowingNotifications(this) }
    val following by lazy { FollowingChecker(library, repository, InstalledApps(this), followPreferences, followNotifications) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.Startup, DiagnosticPhase.Completed))
            developer.state.map { it.debridEnabled }.distinctUntilChanged()
                .onEach { if (!it) debrid.deactivate() }.collectLatest { enabled ->
                    if (enabled) {
                        try { debrid.load() }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.Hosts,
                                DiagnosticPhase.Failed, failure = DiagnosticFailure.Unexpected))
                        }
                    }
                }
        }
        scope.launch {
            developer.state.collect {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.Settings, DiagnosticPhase.Completed))
            }
        }
        scope.launch {
            combine(library.state, followPreferences.state) { entries, preferences ->
                followNotifications.retain(entries.filter { it.watched }.map { it.release.id }.toSet())
                (preferences.backgroundChecks && entries.any { it.watched }) to preferences.intervalHours
            }.distinctUntilChanged().collect { (enabled, hours) -> FollowingSchedule.update(this@MobilismApplication, enabled, hours) }
        }
    }
}
