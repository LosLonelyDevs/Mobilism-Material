package org.mobilism.material.members

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.mobilism.material.data.AccountRole
import org.mobilism.material.data.ProfileReference
import org.mobilism.material.data.UserProfile

data class MemberIdentity(val avatar: String?, val administrator: Boolean, val majorAndroidReleaser: Boolean = false)

class MemberIdentityStore(
    private val scope: CoroutineScope,
    private val load: suspend (ProfileReference) -> UserProfile,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val pending = mutableSetOf<String>()
    private val expires = linkedMapOf<String, Long>()
    private val permits = Semaphore(2)
    private val mutableIdentities = MutableStateFlow<Map<String, MemberIdentity>>(emptyMap())
    val identities = mutableIdentities.asStateFlow()

    fun request(username: String) {
        val name = username.trim()
        val key = key(name)
        if (key.isEmpty()) return
        synchronized(lock) {
            if ((expires[key] ?: 0) > now() || !pending.add(key)) return
        }
        scope.launch {
            try {
                val profile = permits.withPermit { load(ProfileReference(username = name)) }
                remember(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                synchronized(lock) {
                    // A profile opened manually may have verified access while this request was running.
                    if ((expires[key] ?: 0) <= now()) {
                        mutableIdentities.value = mutableIdentities.value - key
                        expires[key] = now() + 60_000
                        trim()
                    }
                }
            } finally { synchronized(lock) { pending.remove(key) } }
        }
    }

    fun remember(profile: UserProfile) = synchronized(lock) {
        val key = key(profile.username)
        mutableIdentities.value = mutableIdentities.value + (key to MemberIdentity(
            profile.avatar, AccountRole.Administrator in profile.roles, profile.majorAndroidReleaser))
        expires.remove(key)
        expires[key] = now() + 15 * 60_000
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
