package org.mobilism.material.members

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.AccountRole
import org.mobilism.material.data.ProfileReference
import org.mobilism.material.data.UserProfile

data class MemberIdentity(val avatar: String?, val administrator: Boolean, val majorAndroidReleaser: Boolean = false)

class MemberIdentityStore(
    private val load: suspend (ProfileReference) -> UserProfile,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Any()
    private val expires = linkedMapOf<String, Long>()
    private val requests = Mutex()
    private var nextRequest = 0L
    private var blockedUntil = 0L
    private var failures = 0
    private val mutableIdentities = MutableStateFlow<Map<String, MemberIdentity>>(emptyMap())
    val identities = mutableIdentities.asStateFlow()

    suspend fun request(username: String) {
        val name = username.trim()
        val key = key(name)
        if (key.isEmpty()) return
        if (!due(key)) return
        requests.withLock {
            if (!due(key)) return@withLock
            val remaining = nextRequest - now()
            if (remaining > 0) wait(remaining)
            if (!due(key)) return@withLock
            nextRequest = now() + 1_000
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
                        val cooldown = if (missing) 60 * 60_000L else {
                            failures = (failures + 1).coerceAtMost(5)
                            (60_000L shl (failures - 1)).coerceAtMost(15 * 60_000L).also { blockedUntil = now() + it }
                        }
                        expires[key] = now() + cooldown
                        trim()
                    }
                }
            }
        }
    }

    private fun due(key: String) = synchronized(lock) { now() >= blockedUntil && (expires[key] ?: 0) <= now() }

    fun remember(profile: UserProfile) = synchronized(lock) {
        val key = key(profile.username)
        mutableIdentities.value = mutableIdentities.value + (key to MemberIdentity(
            profile.avatar, AccountRole.Administrator in profile.roles, profile.majorAndroidReleaser))
        expires.remove(key)
        expires[key] = now() + 60 * 60_000
        failures = 0
        blockedUntil = 0
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
