package org.mobilism.material.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.mobilism.material.members.MemberIdentity
import org.mobilism.material.members.MemberIdentityStore

internal val LocalMemberIdentities = staticCompositionLocalOf<MemberIdentityStore?> { null }

@Composable
internal fun memberIdentity(username: String): MemberIdentity? {
    val store = LocalMemberIdentities.current ?: return null
    val owner = LocalLifecycleOwner.current
    val identities by store.identities.collectAsStateWithLifecycle()
    LaunchedEffect(store, username, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                store.request(username)
                delay(60_000)
            }
        }
    }
    return identities[MemberIdentityStore.key(username)]
}
