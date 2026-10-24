package org.mobilism.material

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.settings.DiagnosticLogExporter
import org.mobilism.material.settings.DiagnosticLogStore

@RunWith(AndroidJUnit4::class)
class DiagnosticExportTest {
    @Test fun sharesOnlyAnExplicitLogExportThroughContentUri() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "test-diagnostics")
        directory.deleteRecursively()
        try {
            val logs = DiagnosticLogStore(directory, { true })
            assertNull(DiagnosticLogExporter.export(context, logs))
            logs.record(DiagnosticEvent(DiagnosticSource.MegaDebrid, DiagnosticOperation.Resolve,
                DiagnosticPhase.Failed, httpStatus = 200, failure = DiagnosticFailure.Unrestricting))
            val uri = checkNotNull(DiagnosticLogExporter.export(context, logs))
            assertEquals("content", uri.scheme)
            assertEquals(context.packageName + ".diagnostics", uri.authority)
            val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            assertTrue(text.contains("Mobilism Material ${BuildConfig.VERSION_NAME}"))
            assertTrue(text.contains("Unrestricting"))
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, context.packageName + ".diagnostics", File(context.filesDir, "private-file"))
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
