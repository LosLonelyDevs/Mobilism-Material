package org.mobilism.material.ui

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class DownloadRequestTrackerTest {
    @Test fun concurrentPresentationsCannotStartTheSameLookupTwice() {
        val requests = DownloadRequestTracker()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val accepted = pool.invokeAll(List(20) { Callable { requests.begin("https://example.org/file") } })
                .count { it.get() }
            assertEquals(1, accepted)
            assertEquals(setOf("https://example.org/file"), requests.pending.value)
            requests.finish("https://example.org/file")
            assertTrue(requests.pending.value.isEmpty())
            assertTrue(requests.begin("https://example.org/file"))
        } finally { pool.shutdownNow() }
    }

    @Test fun completingOneLookupKeepsOtherLinksBusy() {
        val requests = DownloadRequestTracker()
        requests.begin("first")
        requests.begin("second")
        requests.finish("first")
        assertEquals(setOf("second"), requests.pending.value)
        assertFalse(requests.begin("second"))
    }
}
