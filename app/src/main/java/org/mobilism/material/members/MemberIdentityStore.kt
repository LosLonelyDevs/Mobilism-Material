package org.mobilism.material.members

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Any()
    private val expires = linkedMapOf<String, Long>()
    private val requests = Mutex()
    private var lastRequest: Long? = null
    private var failedAt: Long? = null
    private var failures = 0
    private var previousLimits = ApiRequestLimits.Defaults
    private val mutableIdentities = MutableStateFlow<Map<String, MemberIdentity>>(emptyMap())
    val identities = mutableIdentities.asStateFlow()

    suspend fun request(username: String) {
        val name = username.trim()
        val key = key(name)
        if (key.isEmpty()) return
        if (!due(key)) return
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
        if (policy != previousLimits) {
            expires.clear()
            mutableIdentities.value = emptyMap()
            previousLimits = policy
        }
    }

    private fun due(key: String) = synchronized(lock) {
        refreshLimits()
        failedAt?.let { now() < it + failureCooldown() } != true && (expires[key] ?: 0) <= now()
    }

    fun remember(profile: UserProfile) = synchronized(lock) {
        refreshLimits()
        val key = key(profile.username)
        mutableIdentities.value = mutableIdentities.value + (key to MemberIdentity(
            profile.avatar, AccountRole.Administrator in profile.roles, profile.majorAndroidReleaser))
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
