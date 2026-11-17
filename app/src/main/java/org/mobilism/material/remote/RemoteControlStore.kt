package org.mobilism.material.remote

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.mobilism.material.BuildConfig
import org.mobilism.material.data.ApiRequestLimits
import org.mobilism.material.data.AppNotice
import org.mobilism.material.data.RemoteAppConfig
import org.mobilism.material.data.RemoteConfigClient
import org.mobilism.material.data.NoticeAudience
import org.mobilism.material.data.NoticeContext
import org.mobilism.material.data.DiagnosticLogger
import org.mobilism.material.data.DiagnosticEvent
import org.mobilism.material.data.DiagnosticSource
import org.mobilism.material.data.DiagnosticOperation
import org.mobilism.material.data.DiagnosticPhase

internal class RemoteControlStore(
    private val fetch: suspend () -> RemoteAppConfig,
    private val installedCode: Int = BuildConfig.VERSION_CODE,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val readDismissed: () -> Set<String> = { emptySet() },
    private val saveDismissed: (Set<String>) -> Unit = {},
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val androidApi: Int = 0,
    private val syncNotifications: (String?, List<AppNotice>) -> Unit = { _, _ -> },
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
) {
    @Volatile private var config = RemoteAppConfig()
    @Volatile private var fetchedAt: Long? = null
    private var attemptedAt: Long? = null
    private val lock = Mutex()
    private val audienceLock = Any()
    private var username: String? = null
    private var identityKnown = false
    private var requestedNotice: Pair<String, String>? = null
    private val dismissed = readDismissed().toMutableSet()
    private val sessionDismissed = mutableSetOf<String>()
    private val foregroundDismissed = mutableSetOf<String>()
    private val mutable = MutableStateFlow<AppNotice?>(null)
    val notice = mutable.asStateFlow()
    val userAgentPolicy = org.mobilism.material.data.UserAgentPolicy(installedVersion) { currentConfig().userAgentRules }

    // No disk cache of remote values: a cold start or failed refresh uses shipped defaults.
    val limits: ApiRequestLimits get() = currentConfig().limits
    val hasConfiguration: Boolean get() = fetchedAt?.let { now() - it in 0 until MAX_AGE_MS } == true
    private fun currentConfig(): RemoteAppConfig {
        val age = fetchedAt?.let { now() - it }
        return if (age != null && age in 0 until MAX_AGE_MS) config else RemoteAppConfig()
    }

    fun accountChanged(username: String?, known: Boolean = true) = synchronized(audienceLock) {
        this.username = username
        identityKnown = known
        tick()
    }

    fun foreground() = synchronized(audienceLock) {
        foregroundDismissed.clear()
        tick()
    }

    fun openNotice(account: String, id: String) = synchronized(audienceLock) {
        requestedNotice = account to id
        tick()
    }

    private fun dismissalKey(notice: AppNotice): String =
        "${username?.takeIf { identityKnown }?.let(NoticeAudience::usernameHash) ?: "guest"}:${notice.dismissalKey}"

    fun tick() = synchronized(audienceLock) {
        // Clear response-triggered overrides on config failure, even between API requests.
        userAgentPolicy.prefix()
        val context = NoticeContext(username, installedVersion, installedCode, androidApi, identityKnown)
        val matching = currentConfig().notices.filter { it.applies(installedCode, wallTime()) && it.audience.matches(context) }
        syncNotifications(if (identityKnown) username?.let(NoticeAudience::usernameHash) ?: "guest" else null,
            matching.takeIf { identityKnown }.orEmpty())
        val requested = requestedNotice?.let { (account, id) ->
            if (!identityKnown || !hasConfiguration) null
            else if (account != (username?.let(NoticeAudience::usernameHash) ?: "guest")) {
                requestedNotice = null
                null
            } else matching.firstOrNull { it.id == id }.also { if (it == null) requestedNotice = null }
        }
        mutable.value = matching.firstOrNull { it.required } ?: requested ?: matching
            .sortedByDescending { it.required }.firstOrNull { notice ->
                notice.required || when (notice.frequency) {
                    "once" -> dismissalKey(notice) !in dismissed
                    "launch" -> dismissalKey(notice) !in sessionDismissed
                    else -> dismissalKey(notice) !in foregroundDismissed
                }
            }
    }

    fun dismiss(notice: AppNotice) = synchronized(audienceLock) {
        if (notice.required) return@synchronized
        diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.NoticeDisplay,
            DiagnosticPhase.Dismissed, noticeKey = notice.dismissalKey))
        if (requestedNotice?.second == notice.id) requestedNotice = null
        when (notice.frequency) {
            "once" -> {
                dismissed.add(dismissalKey(notice))
                while (dismissed.size > 100) dismissed.remove(dismissed.first())
                saveDismissed(dismissed.toSet())
            }
            "launch" -> sessionDismissed.add(dismissalKey(notice))
            else -> foregroundDismissed.add(dismissalKey(notice))
        }
        tick()
    }

    fun rendered(notice: AppNotice) {
        diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.NoticeDisplay,
            DiagnosticPhase.Rendered, noticeKey = notice.dismissalKey))
    }

    suspend fun refresh(force: Boolean = false) {
        tick()
        if (!lock.tryLock()) return
        try {
            val age = attemptedAt?.let { now() - it }
            if (age != null && age in 0 until (if (force) 15_000L else REFRESH_MS)) return
            attemptedAt = now()
            try {
                config = fetch()
                fetchedAt = now()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                config = RemoteAppConfig()
                fetchedAt = null
            }
            tick()
        } finally { lock.unlock() }
    }

    companion object {
        const val REFRESH_MS = 5 * 60_000L
        const val MAX_AGE_MS = 15 * 60_000L

        fun create(context: Context, diagnostics: DiagnosticLogger = DiagnosticLogger.None): RemoteControlStore {
            val prefs = context.getSharedPreferences("remote-notices", Context.MODE_PRIVATE)
            val notifications = RemoteNoticeNotifications(context, diagnostics)
            return RemoteControlStore(RemoteConfigClient(BuildConfig.DAILY_USAGE_ENDPOINT,
                org.mobilism.material.data.AppUserAgent.identity(context))::fetch,
                readDismissed = { prefs.getStringSet("dismissed", emptySet()).orEmpty().toSet() },
                saveDismissed = { prefs.edit().putStringSet("dismissed", it).apply() }, androidApi = android.os.Build.VERSION.SDK_INT,
                syncNotifications = notifications::sync, diagnostics = diagnostics)
        }
    }
}
