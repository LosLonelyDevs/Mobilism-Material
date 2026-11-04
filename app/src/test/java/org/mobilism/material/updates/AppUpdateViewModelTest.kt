package org.mobilism.material.updates

import androidx.lifecycle.ViewModelStore
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mobilism.material.data.AppRelease

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    @get:Rule val folder = TemporaryFolder()
    private val store = ViewModelStore()
    private val bytes = "test update".toByteArray()
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val release = AppRelease("99.0.0", "https://example.org", "https://example.org/update.apk", bytes.size.toLong(), hash)

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun oneDownloadSurvivesRepeatedActionsAndInstallationRequestIsConsumedOnce() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var downloads = 0
        val output = File(folder.root, "update.apk")
        val model = AppUpdateViewModel(output, { _, _ -> }) { _, file, progress ->
            downloads++
            started.complete(Unit)
            finish.await()
            file.writeBytes(bytes)
            progress(100)
        }
        store.put("updater", model)
        model.downloadAndInstall(release)
        withTimeout(5_000) { started.await() }
        model.downloadAndInstall(release)
        assertEquals(1, downloads)
        finish.complete(Unit)
        withTimeout(5_000) { model.state.first { it.phase == UpdatePhase.Ready } }
        assertTrue(model.consumeInstallRequest())
        assertFalse(model.consumeInstallRequest())
        assertEquals(output, model.fileForInstall())
        model.downloadAndInstall(release)
        assertEquals(1, downloads)
        assertTrue(model.consumeInstallRequest())
    }

    @Test fun cancellationStopsTheDownloadWithoutOpeningTheInstaller() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val model = AppUpdateViewModel(File(folder.root, "update.apk"), { _, _ -> }) { _, _, _ ->
            started.complete(Unit)
            CompletableDeferred<Unit>().await()
        }
        store.put("updater", model)
        model.downloadAndInstall(release)
        withTimeout(5_000) { started.await() }
        model.cancel()
        withTimeout(5_000) { model.state.first { it.phase == UpdatePhase.Idle } }
        assertFalse(model.consumeInstallRequest())
        assertNull(model.fileForInstall())
    }

    @Test fun rejectsInvalidPackagesAndRechecksSavedFilesBeforeInstallation() = runBlocking {
        val invalidFile = File(folder.root, "invalid.apk")
        val invalid = AppUpdateViewModel(invalidFile, { _, _ -> error("Wrong signing key") }) { _, file, _ -> file.writeBytes(bytes) }
        store.put("invalid", invalid)
        invalid.downloadAndInstall(release)
        withTimeout(5_000) { invalid.state.first { it.phase == UpdatePhase.Idle && it.message == "Wrong signing key" } }
        assertFalse(invalid.consumeInstallRequest())
        assertFalse(invalidFile.exists())

        val output = File(folder.root, "valid.apk")
        val valid = AppUpdateViewModel(output, { _, _ -> }) { _, file, _ -> file.writeBytes(bytes) }
        store.put("valid", valid)
        valid.downloadAndInstall(release)
        withTimeout(5_000) { valid.state.first { it.phase == UpdatePhase.Ready } }
        valid.consumeInstallRequest()
        output.writeBytes(ByteArray(bytes.size))
        assertNull(valid.fileForInstall())
        assertEquals(UpdatePhase.Idle, valid.state.value.phase)
        assertFalse(output.exists())
    }
}
