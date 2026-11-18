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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import org.mobilism.material.data.DiagnosticEvent
import org.mobilism.material.data.DiagnosticSource
import org.mobilism.material.data.DiagnosticOperation
import org.mobilism.material.data.DiagnosticPhase
import org.mobilism.material.data.DiagnosticFailure
import org.mobilism.material.settings.DeveloperPreferencesStore
import org.mobilism.material.settings.DiagnosticLogStore
import org.mobilism.material.messages.*
import org.mobilism.material.data.AuthState

class MobilismApplication : Application(), org.mobilism.material.data.UserAgentPolicyProvider,
    org.mobilism.material.data.RequestTrafficProvider, coil3.SingletonImageLoader.Factory {
    override val requestTraffic = org.mobilism.material.data.RequestTrafficSink {
        if (org.mobilism.material.usage.DailyUsageSchedule.configured) dailyUsage.recordTraffic(it)
    }
    override fun newImageLoader(context: android.content.Context): coil3.ImageLoader =
        org.mobilism.material.images.appImageLoader(context)

    override val userAgentPolicy get() = remoteControl.userAgentPolicy
    val memberIdentities by lazy { org.mobilism.material.members.MemberIdentityStore(repository::profile,
        limits = { remoteControl.limits }) }
    val recentSearches by lazy { org.mobilism.material.settings.RecentSearchStore(this) }
    val messageAlerts by lazy { MessageAlertStore(this, limits = { remoteControl.limits }) }
    val messageNotifications by lazy { MessageNotifications(this) }
    val messageReads by lazy { org.mobilism.material.settings.MessageReadStore(this, messageNotifications::cancelRead) }
    val messageChecker by lazy { MessageNotificationChecker(repository,
        username = { (repository.auth.value as? AuthState.SignedIn)?.username },
        allowed = { messageAlerts.enabled.value && messageNotifications.allowed() },
        history = messageAlerts::history, readHistory = messageReads::forAccount,
        post = { account, message ->
            if ((repository.auth.value as? AuthState.SignedIn)?.username == account && messageAlerts.enabled.value &&
                message.messageId !in messageReads.forAccount(account)) messageNotifications.post(account, message) else false
        }, cancelRead = messageNotifications::cancelRead, policy = messageAlerts.polls) }
    val dailyUsage by lazy { org.mobilism.material.usage.DailyUsageStore(this) }
    val contentPreferences by lazy { org.mobilism.material.data.ContentPreferencesStore(this) }
    val developer by lazy { DeveloperPreferencesStore(this) }
    val diagnostics by lazy { DiagnosticLogStore(java.io.File(filesDir, "diagnostics"),
        { true }, isVerbose = { developer.state.value.enabled && developer.state.value.verboseLogging }) }
    val customCategories by lazy { org.mobilism.material.data.CustomCategoryStore(this) }
    internal val connectionRecovery by lazy { org.mobilism.material.ui.ConnectionRecoveryController() }
    internal val remoteControl by lazy { org.mobilism.material.remote.RemoteControlStore.create(this, diagnostics) }
    val repository by lazy { DefaultMobilismRepository(this, contentPreferences, diagnostics,
        customCategories = customCategories, customCategoriesEnabled = { developer.state.value.customCategoriesActive },
        challengeRecovery = connectionRecovery, requestLimits = { remoteControl.limits }, requestTraffic = requestTraffic) }
    val appearance by lazy { AppearanceStore(this) }
    val releaseDisplay by lazy { org.mobilism.material.settings.ReleaseDisplayStore(this) }
    val debrid by lazy { DebridRepository(this, isEnabled = {
        developer.state.value.debridEnabled
    }, diagnostics = diagnostics, requestLimits = { remoteControl.limits }) }
    val library by lazy { AppLibraryStore(this) }
    val followPreferences by lazy { FollowingPreferences(this) }
    val followNotifications by lazy { FollowingNotifications(this) }
    val following by lazy { FollowingChecker(library, repository, InstalledApps(this), followPreferences, followNotifications,
        limits = { remoteControl.limits }) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val foreground = MutableStateFlow(false)
    private val visibleActivities = mutableSetOf<Any>()
    @Synchronized internal fun setForeground(activity: Any, visible: Boolean) {
        if (visible) visibleActivities.add(activity) else visibleActivities.remove(activity)
        foreground.value = visibleActivities.isNotEmpty()
    }
    internal val appUpdates by lazy { org.mobilism.material.updates.AppUpdateChecker(this) }
    private val updateLaunchStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    internal fun checkAppUpdatesOnColdStart() {
        if (updateLaunchStarted.compareAndSet(false, true)) scope.launch { appUpdates.check(automatic = true) }
    }

    override fun onCreate() {
        super.onCreate()
        org.mobilism.material.remote.RemoteNoticeSchedule.start(this)
        scope.launch {
            repository.auth.collect { auth ->
                remoteControl.accountChanged((auth as? AuthState.SignedIn)?.username, known = auth != AuthState.Restoring)
            }
        }
        scope.launch {
            combine(repository.auth, messageAlerts.enabled) { auth, enabled -> auth to enabled }
                .distinctUntilChanged().collect { (auth, enabled) ->
                    if (auth != AuthState.Restoring) {
                        val account = (auth as? AuthState.SignedIn)?.username
                        MessageSchedule.update(this@MobilismApplication, enabled && account != null)
                        messageNotifications.retainAccount(account.takeIf { enabled })
                    }
                }
        }
        scope.launch {
            diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.Startup, DiagnosticPhase.Completed))
            combine(developer.state.map { it.debridEnabled }, foreground) { enabled, visible -> enabled to visible }
                .distinctUntilChanged().collectLatest { (enabled, visible) ->
                    if (!enabled) debrid.deactivate()
                    else if (visible) {
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
