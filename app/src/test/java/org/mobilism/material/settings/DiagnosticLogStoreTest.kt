package org.mobilism.material.settings

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mobilism.material.data.*

class DiagnosticLogStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun loggingRequiresOptInAndStopsImmediatelyWhenDisabled() {
        var enabled = false
        val logs = DiagnosticLogStore(temporary.newFolder(), { enabled })
        val event = DiagnosticEvent(DiagnosticSource.Forum, DiagnosticOperation.Login, DiagnosticPhase.Completed,
            httpStatus = 200, elapsedMillis = 20)
        logs.record(event)
        assertEquals("", logs.snapshot())
        enabled = true
        logs.record(event)
        val captured = logs.snapshot()
        assertTrue(captured.contains("\"operation\":\"Login\""))
        enabled = false
        logs.record(event.copy(httpStatus = 401))
        assertEquals(captured, logs.snapshot())
    }

    @Test fun rotatesToTwoBoundedFilesAndKeepsNewestEvents() {
        val directory = temporary.newFolder()
        val logs = DiagnosticLogStore(directory, { true }, maxFileBytes = 512)
        repeat(100) { logs.record(DiagnosticEvent(DiagnosticSource.Forum, DiagnosticOperation.Search,
            DiagnosticPhase.Completed, requestId = it.toLong(), httpStatus = 200)) }
        val files = directory.listFiles().orEmpty()
        assertEquals(2, files.size)
        assertTrue(files.all { it.length() <= 512 })
        val lines = logs.snapshot().lines().filter(String::isNotBlank)
        assertTrue(lines.size < 100)
        assertTrue(lines.last().contains("\"requestId\":99"))
        assertTrue(lines.all { it.startsWith("{") && it.endsWith("}") })
    }
}
