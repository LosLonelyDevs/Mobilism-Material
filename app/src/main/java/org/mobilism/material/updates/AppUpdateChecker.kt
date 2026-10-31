package org.mobilism.material.updates

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import org.mobilism.material.BuildConfig
import org.mobilism.material.data.AppRelease
import org.mobilism.material.data.AppReleaseUpdates
import org.mobilism.material.data.AppVersions

internal interface AppUpdateSettings {
    var enabled: Boolean
    var lastAutomaticAttempt: Long
    var notifiedVersion: String?
    var permissionRequested: Boolean
}

private class SavedAppUpdateSettings(context: Context) : AppUpdateSettings {
    private val prefs = context.getSharedPreferences("material-app-updates", Context.MODE_PRIVATE)
    override var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit { putBoolean("enabled", value) } }
    override var lastAutomaticAttempt: Long
        get() = prefs.getLong("last-automatic-attempt", 0)
        set(value) { prefs.edit(commit = true) { putLong("last-automatic-attempt", value) } }
    override var notifiedVersion: String?
        get() = prefs.getString("notified-version", null)
        set(value) { prefs.edit { putString("notified-version", value) } }
    override var permissionRequested: Boolean
        get() = prefs.getBoolean("permission-requested", false)
        set(value) { prefs.edit { putBoolean("permission-requested", value) } }
}

internal data class AppUpdateState(
    val enabled: Boolean = true,
    val checking: Boolean = false,
    val checked: Boolean = false,
    val available: AppRelease? = null,
    val error: String? = null,
)

internal class AppUpdateChecker(
    private val settings: AppUpdateSettings,
    private val latest: suspend () -> AppRelease,
    private val post: (AppRelease) -> Boolean,
    private val cancelNotification: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val installed: String = BuildConfig.VERSION_NAME,
) {
    constructor(context: Context, notifications: AppUpdateNotifications = AppUpdateNotifications(context)) : this(
        SavedAppUpdateSettings(context), AppReleaseUpdates()::latest, notifications::post, notifications::cancel)

    private val mutable = MutableStateFlow(AppUpdateState(enabled = settings.enabled))
    val state = mutable.asStateFlow()
    private val checking = Mutex()
    init {
        // Installing an update must clear its alert even while the next check is throttled.
        settings.notifiedVersion?.let { if (!AppVersions.newer(it, installed)) cancelNotification() }
    }
    var permissionRequested: Boolean
        get() = settings.permissionRequested
        set(value) { settings.permissionRequested = value }

    fun setEnabled(enabled: Boolean) = synchronized(settings) {
        settings.enabled = enabled
        mutable.update { it.copy(enabled = enabled) }
        if (!enabled) cancelNotification()
    }

    suspend fun check(automatic: Boolean = false) {
        if (!checking.tryLock()) return
        try {
            if (automatic) {
                if (!settings.enabled) return
                val time = now()
                val previous = settings.lastAutomaticAttempt
                if (previous > time) {
                    settings.lastAutomaticAttempt = time
                    return
                }
                if (previous > 0 && time - previous < AUTOMATIC_INTERVAL_MS) return
                // Count attempts too, so repeated launches cannot hammer GitHub during an outage.
                settings.lastAutomaticAttempt = time
            }
            mutable.update { it.copy(checking = true, error = null) }
            val release = latest().takeIf { AppVersions.newer(it.version, installed) }
            mutable.update { it.copy(checked = true, available = release) }
            if (release == null) cancelNotification()
            else if (automatic) notifyAvailable()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutable.update { it.copy(error = "Couldn't check for updates. Try again.") }
        } finally {
            mutable.update { it.copy(checking = false) }
            checking.unlock()
        }
    }

    fun notifyAvailable() = synchronized(settings) {
        val release = state.value.available ?: return@synchronized
        val newerThanNotified = settings.notifiedVersion?.let { AppVersions.newer(release.version, it) } ?: true
        if (settings.enabled && newerThanNotified && post(release))
            settings.notifiedVersion = release.version
    }

    companion object { const val AUTOMATIC_INTERVAL_MS = 12 * 60 * 60 * 1000L }
}
