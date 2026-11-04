package org.mobilism.material.updates

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mobilism.material.data.AppRelease

class AppUpdateCheckerTest {
    @Test fun cachedUpdatePromptsSurviveRestartUntilDismissedAndNewVersionsPromptAgain() = runBlocking {
        val fixture = Fixture()
        val checker = fixture.checker()
        checker.check(automatic = true)
        assertEquals("0.1.18", checker.state.value.prompt?.version)
        val restarted = fixture.checker()
        restarted.check(automatic = true)
        assertEquals(1, fixture.reads)
        assertNotNull(restarted.state.value.prompt)
        restarted.dismissPrompt()
        assertNull(fixture.checker().state.value.prompt)
        fixture.version = "0.1.19"
        fixture.time += AppUpdateChecker.AUTOMATIC_INTERVAL_MS
        val next = fixture.checker()
        next.check(automatic = true)
        assertEquals("0.1.19", next.state.value.prompt?.version)
        next.setEnabled(false)
        assertNull(next.state.value.prompt)
        assertNull(fixture.checker().state.value.prompt)
    }

    @Test fun disablingDuringLookupPreventsTheModalAsWellAsNotifications() = runBlocking {
        val answer = CompletableDeferred<AppRelease>()
        val settings = Saved()
        val checker = AppUpdateChecker(settings, { answer.await() }, { true }, installed = "0.1.17")
        val job = async(start = CoroutineStart.UNDISPATCHED) { checker.check(automatic = true) }
        checker.setEnabled(false)
        answer.complete(AppRelease("0.1.18", "https://example.org", null))
        job.await()
        assertNull(checker.state.value.prompt)
        assertNull(AppUpdateChecker(settings, { error("no lookup") }, { true }, installed = "0.1.17").state.value.prompt)
    }

    @Test fun installingTheNotifiedVersionClearsItsAlertWithoutAnotherRequest() = runBlocking {
        val fixture = Fixture()
        fixture.settings.notifiedVersion = "0.1.17"
        fixture.settings.lastAutomaticAttempt = fixture.time
        fixture.checker().check(automatic = true)
        assertEquals(1, fixture.cancellations)
        assertEquals(0, fixture.reads)
    }

    @Test fun coldStartsAreLimitedAcrossCheckerInstancesAndManualChecksBypassTheLimit() = runBlocking {
        val fixture = Fixture()
        fixture.checker().check(automatic = true)
        repeat(4) { fixture.checker().check(automatic = true) }
        assertEquals(1, fixture.reads)
        fixture.time += AppUpdateChecker.AUTOMATIC_INTERVAL_MS - 1
        fixture.checker().check(automatic = true)
        assertEquals(1, fixture.reads)
        fixture.time++
        fixture.checker().check(automatic = true)
        assertEquals(2, fixture.reads)
        repeat(3) { fixture.checker().check() }
        assertEquals(5, fixture.reads)
    }

    @Test fun disabledNotificationsStopAutomaticChecksButManualChecksStillWork() = runBlocking {
        val fixture = Fixture()
        val checker = fixture.checker()
        assertTrue(checker.state.value.enabled)
        checker.setEnabled(false)
        fixture.checker().check(automatic = true)
        assertEquals(0, fixture.reads)
        checker.check()
        assertNotNull(checker.state.value.available)
        checker.notifyAvailable()
        assertEquals(1, fixture.reads)
        assertEquals(0, fixture.notifications)
        assertEquals(1, fixture.cancellations)
    }

    @Test fun notifiesOncePerNewVersionAndRetriesWhenPermissionBecomesAvailable() = runBlocking {
        val fixture = Fixture()
        fixture.allowed = false
        val checker = fixture.checker()
        checker.check(automatic = true)
        assertNull(fixture.settings.notifiedVersion)
        fixture.allowed = true
        checker.notifyAvailable()
        checker.notifyAvailable()
        assertEquals(1, fixture.notifications)
        fixture.time += AppUpdateChecker.AUTOMATIC_INTERVAL_MS
        fixture.checker().check(automatic = true)
        assertEquals(1, fixture.notifications)
        fixture.version = "0.1.19"
        fixture.time += AppUpdateChecker.AUTOMATIC_INTERVAL_MS
        fixture.checker().check(automatic = true)
        assertEquals(2, fixture.notifications)
        fixture.version = "0.1.18"
        fixture.time += AppUpdateChecker.AUTOMATIC_INTERVAL_MS
        fixture.checker().check(automatic = true)
        assertEquals("Moving GitHub's Latest label back must not repeat an alert", 2, fixture.notifications)
    }

    @Test fun failedChecksCountTowardAutomaticLimitAndManualRetryClearsTheError() = runBlocking {
        val fixture = Fixture()
        fixture.fail = true
        val checker = fixture.checker()
        checker.check(automatic = true)
        assertNotNull(checker.state.value.error)
        assertFalse(checker.state.value.checking)
        fixture.checker().check(automatic = true)
        assertEquals(1, fixture.reads)
        fixture.fail = false
        checker.check()
        assertNull(checker.state.value.error)
        assertNotNull(checker.state.value.available)
    }

    @Test fun currentOrOlderReleaseDoesNotNotify() = runBlocking {
        val fixture = Fixture()
        for (version in listOf("0.1.17", "0.1.16")) {
            fixture.version = version
            val checker = fixture.checker()
            checker.check()
            assertTrue(checker.state.value.checked)
            assertNull(checker.state.value.available)
        }
        assertEquals(0, fixture.notifications)
    }

    @Test fun clockRollbackDoesNotCauseExtraAutomaticRequests() = runBlocking {
        val fixture = Fixture()
        fixture.checker().check(automatic = true)
        fixture.time -= 60_000
        fixture.checker().check(automatic = true)
        fixture.time++
        fixture.checker().check(automatic = true)
        assertEquals(1, fixture.reads)
    }

    @Test fun concurrentChecksShareTheRequestAndDisablingDuringItPreventsNotification() = runBlocking {
        val release = CompletableDeferred<AppRelease>()
        val settings = Saved()
        var reads = 0
        var notifications = 0
        val checker = AppUpdateChecker(settings, { reads++; release.await() }, { notifications++; true },
            installed = "0.1.17")
        val pending = async(start = CoroutineStart.UNDISPATCHED) { checker.check(automatic = true) }
        checker.check()
        checker.setEnabled(false)
        release.complete(AppRelease("0.1.18", "https://example.org", null))
        pending.await()
        assertEquals(1, reads)
        assertEquals(0, notifications)
        assertFalse(checker.state.value.checking)
    }

    private class Saved : AppUpdateSettings {
        override var dismissedVersion: String? = null
        override var cachedRelease: AppRelease? = null
        override var enabled = true
        override var lastAutomaticAttempt = 0L
        override var notifiedVersion: String? = null
        override var permissionRequested = false
    }

    private class Fixture {
        val settings = Saved()
        var time = 1_000_000L
        var version = "0.1.18"
        var reads = 0
        var notifications = 0
        var cancellations = 0
        var allowed = true
        var fail = false
        fun checker() = AppUpdateChecker(settings, {
            reads++
            if (fail) throw IOException("offline")
            AppRelease(version, "https://example.org", null)
        }, { if (allowed) { notifications++; true } else false }, { cancellations++ }, { time }, "0.1.17")
    }
}
