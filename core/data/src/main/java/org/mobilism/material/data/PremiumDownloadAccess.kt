package org.mobilism.material.data

import android.content.Context

/** A queued transfer must still belong to the forum account that authorized it. */
class PremiumDownloadAccess(context: Context) {
    private val session = SessionStore(context)
    private val proof = context.getSharedPreferences("material-premium-download-access", Context.MODE_PRIVATE)

    internal fun verified(provider: DebridProvider, username: String, hosts: List<SupportedHost>) {
        proof.edit().putString(provider.name, username).putStringSet(provider.name + "-hosts", hosts.map { it.domain }.toSet()).apply()
    }

    internal fun revoke() = proof.edit().clear().apply()
    internal fun revoke(provider: DebridProvider) = proof.edit().remove(provider.name).remove(provider.name + "-hosts").apply()

    suspend fun permits(owner: String, host: String): Boolean {
        if (owner.isBlank() || !owner.equals(session.read()?.username, ignoreCase = true)) return false
        return DebridProvider.entries.any { provider ->
            owner.equals(proof.getString(provider.name, null), ignoreCase = true) &&
                proof.getStringSet(provider.name + "-hosts", emptySet()).orEmpty().any { HostMatcher.matches(host, it) }
        }
    }
}

fun DebridState.hasPremiumAccount(auth: AuthState): Boolean =
    enabled && auth is AuthState.SignedIn && auth.username.isNotBlank() && accounts.any {
        it.configured && it.validated && auth.username.equals(it.forumOwner, ignoreCase = true)
    }

fun DebridState.forForumUser(auth: AuthState): DebridState =
    copy(accounts = accounts.map {
        if (auth is AuthState.SignedIn && auth.username.equals(it.forumOwner, ignoreCase = true)) it
        else if (it.configured) DebridAccount(it.provider) else it
    })
