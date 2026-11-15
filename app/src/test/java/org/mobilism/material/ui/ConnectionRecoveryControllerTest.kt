package org.mobilism.material.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mobilism.material.data.VerificationSite

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionRecoveryControllerTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    @Test fun simultaneousReadsShareOneCheckAndResumeTogether() = runTest(dispatcher) {
        val controller = ConnectionRecoveryController { testScheduler.currentTime }
        controller.setForeground(true)
        val first = async { controller.recover(VerificationSite.App) }
        val second = async { controller.recover(VerificationSite.App) }
        runCurrent()
        val attempt = controller.attempt.value!!
        assertFalse(attempt.interactive)
        controller.finish(attempt.id, true)
        assertTrue(first.await())
        assertTrue(second.await())
        assertNull(controller.attempt.value)
        assertEquals(attempt.id, controller.completed.value[VerificationSite.App])
    }

    @Test fun proactiveChecksStayQuietButAnAwaitingReadCanShowInteraction() = runTest(dispatcher) {
        val controller = ConnectionRecoveryController { testScheduler.currentTime }
        controller.setForeground(true)
        controller.prepare(VerificationSite.App)
        controller.showInteraction(controller.attempt.value!!.id)
        assertNull(controller.attempt.value)
        assertFalse(controller.canPrepare(VerificationSite.App))
        val read = async { controller.recover(VerificationSite.App) }
        runCurrent()
        assertTrue(controller.attempt.value!!.interactive)
        controller.finish(controller.attempt.value!!.id, true)
        assertTrue(read.await())
    }

    @Test fun cancellationReleasesOnlyItsReaderAndBackgroundingReleasesEverything() = runTest(dispatcher) {
        val controller = ConnectionRecoveryController()
        assertFalse(controller.recover(VerificationSite.App))
        controller.setForeground(true)
        val first = async { controller.recover(VerificationSite.App) }
        val second = async { controller.recover(VerificationSite.App) }
        val forum = async { controller.recover(VerificationSite.Forum) }
        runCurrent()
        first.cancel()
        runCurrent()
        assertNotNull(controller.attempt.value)
        controller.setForeground(false)
        assertFalse(second.await())
        assertFalse(forum.await())
        assertNull(controller.attempt.value)
        assertFalse(controller.canPrepare(VerificationSite.App))
    }

    @Test fun finalCancelledReaderRemovesTheBrowser() = runTest(dispatcher) {
        val controller = ConnectionRecoveryController()
        controller.setForeground(true)
        val read = async { controller.recover(VerificationSite.App) }
        runCurrent()
        read.cancel()
        runCurrent()
        assertNull(controller.attempt.value)
    }

    @Test fun failedCheckBacksOffAndManualRetryCanStillProceed() = runTest(dispatcher) {
        val controller = ConnectionRecoveryController { testScheduler.currentTime }
        controller.setForeground(true)
        val read = async { controller.recover(VerificationSite.App) }
        runCurrent()
        controller.finish(controller.attempt.value!!.id, false)
        assertFalse(read.await())
        assertFalse(controller.recover(VerificationSite.App))
        assertNull(controller.attempt.value)
        val manual = async { controller.verifyManually(VerificationSite.App) }
        runCurrent()
        assertTrue(controller.attempt.value!!.interactive)
        controller.finish(controller.attempt.value!!.id, false)
        assertFalse(manual.await())
        advanceTimeBy(30_001)
        val later = async { controller.recover(VerificationSite.App) }
        runCurrent()
        assertFalse(controller.attempt.value!!.interactive)
        controller.finish(controller.attempt.value!!.id, true)
        assertTrue(later.await())
    }

    @Test fun originChecksStaySeparateAndQueuedManualChecksRemainVisible() = runTest(dispatcher) {
        val controller = ConnectionRecoveryController()
        controller.setForeground(true)
        val app = async { controller.recover(VerificationSite.App) }
        val forum = async { controller.verifyManually(VerificationSite.Forum) }
        runCurrent()
        val originalId = controller.attempt.value!!.id
        controller.finish(originalId, true)
        assertTrue(app.await())
        assertEquals(VerificationSite.Forum, controller.attempt.value!!.site)
        assertTrue(controller.attempt.value!!.interactive)
        controller.finish(originalId, true)
        assertFalse(forum.isCompleted)
        controller.finish(controller.attempt.value!!.id, false)
        assertFalse(forum.await())
    }
}
