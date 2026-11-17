package org.mobilism.material.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.ApiRequestLimits
import org.mobilism.material.data.AppNotice
import org.mobilism.material.data.RemoteAppConfig
import org.mobilism.material.data.NoticeAudience

class RemoteControlStoreTest {
    @Test fun targetedForceUpdateWaitsForIdentityAndClearsOnSignOutOrAccountSwitch() = runBlocking {
        val target = notice(mode = "required").copy(audience = NoticeAudience.Group(true, listOf(
            NoticeAudience.Users(setOf(NoticeAudience.usernameHash("alice"))), NoticeAudience.Versions(setOf("0.1.20")))))
        val store = RemoteControlStore({ RemoteAppConfig(notices = listOf(target)) }, installedCode = 21, installedVersion = "0.1.20")
        store.refresh(); assertNull(store.notice.value)
        store.accountChanged("alice", known = false); assertNull(store.notice.value)
        store.accountChanged("alice"); assertEquals(target, store.notice.value)
        store.accountChanged("bob"); assertNull(store.notice.value)
        store.accountChanged("alice"); assertEquals(target, store.notice.value)
        store.accountChanged(null); assertNull(store.notice.value)
        val otherVersion = RemoteControlStore({ RemoteAppConfig(notices = listOf(target)) }, installedCode = 22, installedVersion = "0.1.21")
        otherVersion.accountChanged("alice"); otherVersion.refresh(); assertNull(otherVersion.notice.value)
    }

    @Test fun dismissalOfASelectedNoticeDoesNotDismissItForAnotherAccount() = runBlocking {
        val target = notice().copy(audience = NoticeAudience.Users(setOf("alice", "bob").map(NoticeAudience::usernameHash).toSet()))
        val store = RemoteControlStore({ RemoteAppConfig(notices = listOf(target)) })
        store.accountChanged("alice"); store.refresh(); store.dismiss(target); assertNull(store.notice.value)
        store.accountChanged("bob"); assertEquals(target, store.notice.value)
        store.accountChanged("alice"); assertNull(store.notice.value)
    }

    private fun notice(id: String = "test", mode: String = "notice", frequency: String = "once") =
        AppNotice(id, "Title", "Message", mode, frequency, true, null, null, 1, null,
            if (mode == "required") 30 else null, null, null)

    @Test fun startupFailuresAndExpiredValuesAlwaysUseHardcodedDefaults() = runBlocking {
        var time = 1000L
        var fail = false
        val remote = ApiRequestLimits.Defaults.copy(searchSpacingMs = 10000)
        val store = RemoteControlStore({ if (fail) error("offline") else RemoteAppConfig(remote, listOf(notice())) },
            installedCode = 21, now = { time })
        assertEquals(ApiRequestLimits.Defaults, store.limits)
        store.refresh()
        assertEquals(remote, store.limits)
        time += RemoteControlStore.MAX_AGE_MS
        assertEquals(ApiRequestLimits.Defaults, store.limits)
        store.tick(); assertNull(store.notice.value)
        store.refresh(); assertEquals(remote, store.limits)
        time += RemoteControlStore.REFRESH_MS; fail = true; store.refresh()
        assertEquals(ApiRequestLimits.Defaults, store.limits); assertNull(store.notice.value)
    }

    @Test fun requiredUpdatesTakePriorityIgnoreDismissalAndCanBeRevoked() = runBlocking {
        var time = 1000L
        var config = RemoteAppConfig(notices = listOf(notice(), notice("required", "required")))
        val store = RemoteControlStore({ config }, installedCode = 21, now = { time })
        store.refresh(); assertEquals("required", store.notice.value?.id)
        store.dismiss(store.notice.value!!); assertEquals("required", store.notice.value?.id)
        config = RemoteAppConfig(); time += 15_000; store.refresh(force = true)
        assertNull(store.notice.value)
        val current = RemoteControlStore({ RemoteAppConfig(notices = listOf(notice(mode = "required"))) }, installedCode = 30)
        current.refresh(); assertNull(current.notice.value)
    }

    @Test fun dismissalPersistsOnceButOtherFrequenciesRespectTheirLifecycle() = runBlocking {
        var saved = emptySet<String>()
        val config = RemoteAppConfig(notices = listOf(notice()))
        val first = RemoteControlStore({ config }, saveDismissed = { saved = it })
        first.refresh(); first.dismiss(first.notice.value!!); first.foreground(); assertNull(first.notice.value)
        val restarted = RemoteControlStore({ config }, readDismissed = { saved })
        restarted.refresh(); assertNull(restarted.notice.value)
        for (frequency in listOf("launch", "always")) {
            val store = RemoteControlStore({ RemoteAppConfig(notices = listOf(notice(frequency = frequency))) })
            store.refresh(); store.dismiss(store.notice.value!!); store.tick(); assertNull(store.notice.value)
            store.foreground(); assertEquals(frequency == "always", store.notice.value != null)
        }
    }

    @Test fun scheduleChangesWhileOpenWithoutAnotherFetch() = runBlocking {
        var wall = 1000L
        val store = RemoteControlStore({ RemoteAppConfig(notices = listOf(notice().copy(startsAt = 2000, endsAt = 3000))) }, wallTime = { wall })
        store.refresh(); assertNull(store.notice.value)
        wall = 2000; store.tick(); assertNotNull(store.notice.value)
        wall = 3000; store.tick(); assertNull(store.notice.value)
    }

    @Test fun failedRequestsAreThrottledAndUpdatedTextShowsAgain() = runBlocking {
        var time = 1000L; var calls = 0; var message = "one"
        val store = RemoteControlStore({ calls++; RemoteAppConfig(notices = listOf(notice().copy(message = message))) }, now = { time })
        store.refresh(); store.dismiss(store.notice.value!!)
        repeat(10) { store.refresh() }; assertEquals(1, calls)
        message = "two"; time += RemoteControlStore.REFRESH_MS; store.refresh()
        assertEquals("two", store.notice.value?.message)
    }
}
