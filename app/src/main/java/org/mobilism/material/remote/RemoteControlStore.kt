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

internal class RemoteControlStore(
    private val fetch: suspend () -> RemoteAppConfig,
    private val installedCode: Int = BuildConfig.VERSION_CODE,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val readDismissed: () -> Set<String> = { emptySet() },
    private val saveDismissed: (Set<String>) -> Unit = {},
    private val installedVersion: String = BuildConfig.VERSION_NAME,
    private val androidApi: Int = 0,
) {
    @Volatile private var config = RemoteAppConfig()
    @Volatile private var fetchedAt: Long? = null
    private var attemptedAt: Long? = null
    private val lock = Mutex()
    private val audienceLock = Any()
    private var username: String? = null
    private var identityKnown = false
    private val dismissed = readDismissed().toMutableSet()
    private val sessionDismissed = mutableSetOf<String>()
    private val foregroundDismissed = mutableSetOf<String>()
    private val mutable = MutableStateFlow<AppNotice?>(null)
    val notice = mutable.asStateFlow()

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

    private fun dismissalKey(notice: AppNotice): String =
        "${username?.takeIf { identityKnown }?.let(NoticeAudience::usernameHash) ?: "guest"}:${notice.dismissalKey}"

    fun tick() = synchronized(audienceLock) {
        val context = NoticeContext(username, installedVersion, installedCode, androidApi, identityKnown)
        mutable.value = currentConfig().notices.filter { it.applies(installedCode, wallTime()) && it.audience.matches(context) }
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

        fun create(context: Context): RemoteControlStore {
            val prefs = context.getSharedPreferences("remote-notices", Context.MODE_PRIVATE)
            return RemoteControlStore(RemoteConfigClient(BuildConfig.DAILY_USAGE_ENDPOINT)::fetch,
                readDismissed = { prefs.getStringSet("dismissed", emptySet()).orEmpty().toSet() },
                saveDismissed = { prefs.edit().putStringSet("dismissed", it).apply() }, androidApi = android.os.Build.VERSION.SDK_INT)
        }
    }
}
