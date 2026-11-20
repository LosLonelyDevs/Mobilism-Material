package org.mobilism.material.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.mobilism.material.data.ChallengeRecovery
import org.mobilism.material.data.BackgroundMobilismRead
import org.mobilism.material.data.VerificationSite

internal data class ConnectionAttempt(val id: Long, val site: VerificationSite, val interactive: Boolean = false,
    val needsAttention: Boolean = false)

internal class ConnectionRecoveryController(private val now: () -> Long = System::currentTimeMillis) : ChallengeRecovery {
    private class Pending(val attempt: ConnectionAttempt, val proactive: Boolean) {
        val browserReady = CompletableDeferred<Boolean>()
        val result = CompletableDeferred<Boolean>()
        var readers = 0
        var profileReaders = 0
        var profileFailed = false
        var interactive = attempt.interactive
    }
    private val pending = linkedMapOf<VerificationSite, Pending>()
    private val failedAt = mutableMapOf<VerificationSite, Long>()
    private val succeededAt = mutableMapOf<VerificationSite, Long>()
    private val interactionRequired = mutableSetOf<VerificationSite>()
    private val mutableAttempt = MutableStateFlow<ConnectionAttempt?>(null)
    val attempt = mutableAttempt.asStateFlow()
    private val mutableCompleted = MutableStateFlow<Map<VerificationSite, Long>>(emptyMap())
    val completed = mutableCompleted.asStateFlow()
    private var foreground = false
    private var nextId = 0L

    override suspend fun recover(site: VerificationSite): Boolean = request(site, manual = false)

    override suspend fun retryProfile(read: suspend () -> String): String? {
        var html: String? = null
        val recovered = request(VerificationSite.Forum, manual = false) { html = read() }
        return if (recovered) html else null
    }

    suspend fun verifyManually(site: VerificationSite): Boolean = request(site, manual = true)

    private suspend fun request(site: VerificationSite, manual: Boolean, confirmProfile: (suspend () -> Unit)? = null): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!foreground || coroutineContext[BackgroundMobilismRead.Key] != null) return@withContext false
        if (!manual && site in interactionRequired && site !in pending) return@withContext false
        val interactive = manual
        if (!interactive && now() - (failedAt[site] ?: Long.MIN_VALUE / 2) < RETRY_DELAY) return@withContext false
        // Requests already in flight may report the old challenge just after another read recovered.
        if (!interactive && site !in pending && now() - (succeededAt[site] ?: Long.MIN_VALUE / 2) < 2_000) {
            confirmProfile?.invoke()
            return@withContext true
        }
        val entry = pending.getOrPut(site) { Pending(ConnectionAttempt(++nextId, site, interactive), proactive = false) }
        if (interactive) entry.interactive = true
        entry.readers++
        if (confirmProfile != null) entry.profileReaders++
        publish()
        if (interactive && mutableAttempt.value?.id == entry.attempt.id) showInteraction(entry.attempt.id)
        try {
            if (confirmProfile != null) {
                if (!entry.browserReady.await()) return@withContext false
                confirmProfile()
                finish(entry.attempt.id, true)
            }
            entry.result.await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            entry.profileFailed = true
            throw failure
        } finally {
            entry.readers--
            if (confirmProfile != null) {
                entry.profileReaders--
                if (entry.profileReaders == 0 && entry.browserReady.isCompleted && !entry.result.isCompleted)
                    finish(entry.attempt.id, false, cooldown = entry.profileFailed)
            }
            if (entry.readers == 0 && !entry.proactive && !entry.result.isCompleted)
                finish(entry.attempt.id, false, cooldown = false)
        }
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            val entries = pending.values.toList()
            pending.clear()
            mutableAttempt.value = null
            entries.forEach {
                it.result.complete(false)
                it.browserReady.complete(false)
            }
        }
    }

    fun canPrepare(site: VerificationSite): Boolean = foreground && site !in pending && site !in interactionRequired &&
        now() - (failedAt[site] ?: Long.MIN_VALUE / 2) >= RETRY_DELAY

    fun prepare(site: VerificationSite) {
        if (!canPrepare(site)) return
        pending[site] = Pending(ConnectionAttempt(++nextId, site), proactive = true)
        publish()
    }

    fun offerInteraction(id: Long) {
        val entry = pending.values.firstOrNull { it.attempt.id == id } ?: return
        val current = mutableAttempt.value ?: return
        if (current.id != id || current.interactive || current.needsAttention || entry.browserReady.isCompleted) return
        if (entry.readers == 0) {
            defer(id)
        } else mutableAttempt.value = current.copy(needsAttention = true)
    }

    fun defer(id: Long) {
        val current = mutableAttempt.value?.takeIf { it.id == id } ?: return
        finish(id, false)
        interactionRequired += current.site
    }

    fun showInteraction(id: Long) {
        val entry = pending.values.firstOrNull { it.attempt.id == id } ?: return
        if (mutableAttempt.value?.id != id || entry.browserReady.isCompleted) return
        entry.interactive = true
        mutableAttempt.value = entry.attempt.copy(interactive = true)
    }

    fun browserPageReady(id: Long) {
        val entry = pending.values.firstOrNull { it.attempt.id == id } ?: return
        if (entry.profileReaders == 0) finish(id, true) else entry.browserReady.complete(true)
    }

    fun finish(id: Long, success: Boolean, cooldown: Boolean = true) {
        val entry = pending.values.firstOrNull { it.attempt.id == id } ?: return
        pending.remove(entry.attempt.site)
        if (success) {
            interactionRequired.remove(entry.attempt.site)
            succeededAt[entry.attempt.site] = now()
            failedAt.remove(entry.attempt.site)
            mutableCompleted.value = mutableCompleted.value + (entry.attempt.site to id)
        } else if (cooldown) {
            if (entry.readers > 0) interactionRequired.remove(entry.attempt.site)
            failedAt[entry.attempt.site] = now()
        }
        if (mutableAttempt.value?.id == id) mutableAttempt.value = null
        entry.result.complete(success)
        entry.browserReady.complete(success)
        publish()
    }

    private fun publish() {
        if (mutableAttempt.value == null) mutableAttempt.value = pending.values.firstOrNull()?.let {
            it.attempt.copy(interactive = it.interactive)
        }
    }

    private companion object { const val RETRY_DELAY = 30_000L }
}

internal class ClearanceRenewalPolicy(private val history: android.content.SharedPreferences? = null) {
    private val attempted = mutableMapOf<VerificationSite, Long>()

    fun shouldRenew(site: VerificationSite, expiresAt: Long?, present: Boolean, now: Long): Boolean {
        if (!present || expiresAt == null || expiresAt <= now || expiresAt - now > 120_000) return false
        if (attempted[site] == expiresAt || history?.getLong(site.name, 0L) == expiresAt) return false
        attempted[site] = expiresAt
        history?.edit()?.putLong(site.name, expiresAt)?.apply()
        return true
    }
}
