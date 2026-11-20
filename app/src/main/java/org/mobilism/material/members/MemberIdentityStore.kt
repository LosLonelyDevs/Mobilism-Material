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
import org.mobilism.material.data.AppCache
import org.mobilism.material.data.CacheOutcome
import org.mobilism.material.data.DiagnosticLogger
import org.mobilism.material.data.cacheLookup
import org.mobilism.material.data.ProfileReference
import org.mobilism.material.data.UserProfile

enum class MemberBadge(val key: String, val label: String) {
    Administrator("admin", "Administrator"), Moderator("moderator", "Moderator"),
    Developer("developer", "Developer"), MajorReleaser("major-android-releaser", "Major Android Releaser")
}

internal fun UserProfile.memberBadges(): Set<MemberBadge> = buildSet {
    if (AccountRole.Administrator in roles) add(MemberBadge.Administrator)
    if (AccountRole.Moderator in roles) add(MemberBadge.Moderator)
    if (AccountRole.Developer in roles) add(MemberBadge.Developer)
    if (majorAndroidReleaser) add(MemberBadge.MajorReleaser)
}

data class MemberIdentity(val avatar: String?, val badges: Set<MemberBadge>,
    val badgesKnown: Boolean = true) {
    val administrator get() = MemberBadge.Administrator in badges
    val majorAndroidReleaser get() = MemberBadge.MajorReleaser in badges
    internal val hasDisplayDetails get() = avatar != null || badges.isNotEmpty()
    internal fun displayLifetime(freshMillis: Long): Long =
        if (hasDisplayDetails) maxOf(freshMillis, 30L * 24 * 60 * 60_000)
        else freshMillis
}

private val stockAvatarFile = Regex("""(?i)(?:no[-_ ]?(?:avatar|image|photo|picture)(?:[-_ ]?available)?|(?:default|blank)[-_ ]?avatar|avatar[-_ ]?(?:default|blank))(?:[-_]\d+(?:x\d+)?)?(?:\.(?:png|jpe?g|gif|webp|svg|avif))?""")

internal fun String?.displayAvatar(): String? = this?.trim()?.takeIf { url ->
    url.isNotEmpty() && !url.equals("null", ignoreCase = true) &&
        !stockAvatarFile.matches(url.substringBefore('?').substringBefore('#').substringAfterLast('/'))
}

class MemberIdentityStore(
    private val load: suspend (ProfileReference) -> UserProfile,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
    private val limits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    private val cache: MemberIdentityCache? = null,
    private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Any()
    private val expires = linkedMapOf<String, Long>()
    private val retryAfter = mutableMapOf<String, Long>()
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
        val memory = fresh(key)
        val badgesKnown = if (memory) identities.value[key]?.badgesKnown == true
            else cache != null && withContext(Dispatchers.IO) { restore(key, cache) }
        val outcome = when {
            identities.value[key] == null -> CacheOutcome.Miss
            memory -> CacheOutcome.MemoryHit
            else -> CacheOutcome.DiskHit
        }
        diagnostics.cacheLookup(AppCache.MemberIdentity, outcome)
        return badgesKnown
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
                    if (dueForMember(key)) {
                        val missing = failure is ApiFailure && failure.httpStatus in setOf(400, 404)
                        val cooldown = if (missing) limits().memberMissingCacheTtlMs else {
                            failures = (failures + 1).coerceAtMost(5)
                            failedAt = now()
                            failureCooldown()
                        }
                        retryAfter[key] = now() + cooldown
                        if (key !in mutableIdentities.value) expires[key] = now() + cooldown
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
            val empty = mutableIdentities.value.filterValues { !it.hasDisplayDetails }.keys
            expires.keys.removeAll(empty)
            mutableIdentities.value = mutableIdentities.value - empty
        }
        if (policy.memberMissingCacheTtlMs != previousLimits.memberMissingCacheTtlMs ||
            policy.memberFailureCooldownMs != previousLimits.memberFailureCooldownMs ||
            policy.memberFailureMaxCooldownMs != previousLimits.memberFailureMaxCooldownMs) {
            expires.keys.removeAll { it !in mutableIdentities.value }
            retryAfter.clear()
        }
        previousLimits = policy
    }

    private fun restore(key: String, cache: MemberIdentityCache): Boolean = synchronized(lock) {
        if (fresh(key)) return@synchronized identities.value[key]?.badgesKnown == true
        val entry = cache.read(key, limits().memberCacheTtlMs) ?: return@synchronized false
        mutableIdentities.value = mutableIdentities.value + (key to entry.identity)
        expires[key] = now() + entry.remainingMillis
        trim()
        entry.identity.badgesKnown
    }

    private fun fresh(key: String) = synchronized(lock) {
        refreshLimits()
        if ((expires[key] ?: 0) > now()) true else {
            if (key in mutableIdentities.value) mutableIdentities.value = mutableIdentities.value - key
            false
        }
    }

    private fun dueForMember(key: String): Boolean =
        (retryAfter[key] ?: 0) <= now() && !(fresh(key) && mutableIdentities.value[key]?.badgesKnown == true)

    private fun due(key: String) = synchronized(lock) {
        dueForMember(key) && failedAt?.let { now() < it + failureCooldown() } != true
    }

    suspend fun rememberAvatar(username: String, avatar: String) = withContext(Dispatchers.IO) {
        val key = key(username)
        if (key.isEmpty() || avatar.isBlank()) return@withContext
        synchronized(lock) {
            cache?.let { restore(key, it) }
            val known = mutableIdentities.value[key]
            if (known?.avatar == avatar || (known?.badgesKnown == true && known.avatar.displayAvatar() != null)) return@synchronized
            save(key, known?.copy(avatar = avatar) ?: MemberIdentity(avatar, emptySet(), badgesKnown = false))
        }
    }

    fun remember(profile: UserProfile) = synchronized(lock) {
        refreshLimits()
        val key = key(profile.username)
        val observed = mutableIdentities.value[key]?.takeUnless { it.badgesKnown }?.avatar
        val identity = MemberIdentity(profile.avatar.displayAvatar() ?: observed.displayAvatar() ?: profile.avatar, profile.memberBadges())
        save(key, identity)
        retryAfter.remove(key)
        failures = 0
        failedAt = null
    }

    private fun save(key: String, identity: MemberIdentity) {
        mutableIdentities.value = mutableIdentities.value + (key to identity)
        cache?.put(key, identity, limits().memberCacheTtlMs)
        expires.remove(key)
        expires[key] = now() + identity.displayLifetime(limits().memberCacheTtlMs)
        trim()
    }

    private fun trim() {
        while (expires.size > 256) {
            val oldest = expires.keys.first()
            expires.remove(oldest)
            retryAfter.remove(oldest)
            mutableIdentities.value = mutableIdentities.value - oldest
        }
    }

    companion object { fun key(username: String): String = username.trim().lowercase(Locale.ROOT) }
}
