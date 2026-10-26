package org.mobilism.material

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*

@RunWith(AndroidJUnit4::class)
class LiveConnectionTest {
    @Test fun readsReportedThreadAndDecodesTheReportedComment() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveConnection") == "true")
        val events = mutableListOf<DiagnosticEvent>()
        val repository = DefaultMobilismRepository(InstrumentationRegistry.getInstrumentation().targetContext,
            diagnostics = DiagnosticLogger { synchronized(events) { events += it } })
        try {
            val comments = repository.comments(6437968, 1)
            val smile = comments.items.single { it.id == 12726994L }
            assertTrue(smile.contentHtml.contains("😊"))
            assertFalse(smile.contentHtml.contains("[smile]"))
            assertFalse(smile.contentHtml.contains("\\ud83d"))
        } catch (failure: ApiFailure) {
            fail("Live API: ${failure.kind}, HTTP ${failure.httpStatus}; diagnostics: $events")
        }
    }
}
