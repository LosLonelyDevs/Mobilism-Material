package org.mobilism.material.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobilism.material.members.MemberIdentity
import org.mobilism.material.members.MemberIdentityStore

internal val LocalMemberIdentities = staticCompositionLocalOf<MemberIdentityStore?> { null }

@Composable
internal fun memberIdentity(username: String): MemberIdentity? {
    val store = LocalMemberIdentities.current ?: return null
    val identities by store.identities.collectAsStateWithLifecycle()
    LaunchedEffect(store, username) { store.request(username) }
    return identities[MemberIdentityStore.key(username)]
}
