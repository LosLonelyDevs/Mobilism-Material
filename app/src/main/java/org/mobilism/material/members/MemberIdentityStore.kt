package org.mobilism.material.members

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.ApiRequestLimits
import org.mobilism.material.data.AccountRole
import org.mobilism.material.data.ProfileReference
import org.mobilism.material.data.UserProfile

data class MemberIdentity(val avatar: String?, val administrator: Boolean, val majorAndroidReleaser: Boolean = false)

class MemberIdentityStore(
    private val load: suspend (ProfileReference) -> UserProfile,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val cache: MemberIdentityCache? = null,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Any()
    private val expires = linkedMapOf<String, Long>()
    private val requests = Mutex()
    private var lastRequest: Long? = null
    private var failedAt: Long? = null
    private var failures = 0
    private var previousLimits = limits()
    private val mutableIdentities = MutableStateFlow<Map<String, MemberIdentity>>(emptyMap())
    val identities = mutableIdentities.asStateFlow()

    suspend fun restoreCached(username: String): Boolean {
        val key = key(username)
        if (key.isEmpty()) return false
        if (fresh(key)) return key in identities.value
        return cache != null && withContext(Dispatchers.IO) { restore(key, cache) }
    }

    suspend fun request(username: String) {
        val name = username.trim()
        val key = key(name)
        if (key.isEmpty()) return
        if (restoreCached(name)) return
        requests.withLock {
            if (!due(key)) return@withLock
            val remaining = lastRequest?.let { it + limits().memberLookupSpacingMs - now() } ?: 0
            if (remaining > 0) wait(remaining)
            if (!due(key)) return@withLock
            lastRequest = now()
            try {
                val profile = load(ProfileReference(username = name))
                remember(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                synchronized(lock) {
                    // A profile opened manually may have verified access while this request was running.
                    if ((expires[key] ?: 0) <= now()) {
                        mutableIdentities.value = mutableIdentities.value - key
                        val missing = failure is ApiFailure && failure.httpStatus in setOf(400, 404)
                        val cooldown = if (missing) limits().memberMissingCacheTtlMs else {
                            failures = (failures + 1).coerceAtMost(5)
                            failedAt = now()
                            failureCooldown()
                        }
                        expires[key] = now() + cooldown
                        trim()
                    }
                }
            }
        }
    }

    private fun failureCooldown(): Long {
        val policy = limits()
        return (policy.memberFailureCooldownMs shl (failures - 1).coerceAtLeast(0))
            .coerceAtMost(maxOf(policy.memberFailureCooldownMs, policy.memberFailureMaxCooldownMs))
    }

    private fun refreshLimits() {
        val policy = limits()
        if (policy == previousLimits) return
        if (policy.memberCacheTtlMs != previousLimits.memberCacheTtlMs) {
            expires.clear()
            mutableIdentities.value = emptyMap()
        } else if (policy.memberMissingCacheTtlMs != previousLimits.memberMissingCacheTtlMs ||
            policy.memberFailureCooldownMs != previousLimits.memberFailureCooldownMs ||
            policy.memberFailureMaxCooldownMs != previousLimits.memberFailureMaxCooldownMs) {
            expires.keys.removeAll { it !in mutableIdentities.value }
        }
        previousLimits = policy
    }

    private fun restore(key: String, cache: MemberIdentityCache): Boolean = synchronized(lock) {
        if (fresh(key)) return@synchronized key in mutableIdentities.value
        val entry = cache.read(key, limits().memberCacheTtlMs) ?: return@synchronized false
        mutableIdentities.value = mutableIdentities.value + (key to entry.identity)
        expires[key] = now() + entry.remainingMillis
        trim()
        true
    }

    private fun fresh(key: String) = synchronized(lock) {
        refreshLimits()
        (expires[key] ?: 0) > now()
    }

    private fun due(key: String) = synchronized(lock) {
        !fresh(key) && failedAt?.let { now() < it + failureCooldown() } != true
    }

    fun remember(profile: UserProfile) = synchronized(lock) {
        refreshLimits()
        val key = key(profile.username)
        val identity = MemberIdentity(profile.avatar, AccountRole.Administrator in profile.roles, profile.majorAndroidReleaser)
        mutableIdentities.value = mutableIdentities.value + (key to identity)
        cache?.put(key, identity, limits().memberCacheTtlMs)
        expires.remove(key)
        expires[key] = now() + limits().memberCacheTtlMs
        failures = 0
        failedAt = null
        trim()
    }

    private fun trim() {
        while (expires.size > 256) {
            val oldest = expires.keys.first()
            expires.remove(oldest)
            mutableIdentities.value = mutableIdentities.value - oldest
        }
    }

    companion object { fun key(username: String): String = username.trim().lowercase(Locale.ROOT) }
}
