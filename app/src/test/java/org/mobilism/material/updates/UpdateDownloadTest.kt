package org.mobilism.material.updates

import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mobilism.material.data.ReleaseApk

class UpdateDownloadTest {
    @get:Rule val folder = TemporaryFolder()
    private val content = "verified apk contents".toByteArray()
    private val url = "https://github.com/LosLonelyDevs/Mobilism-Material/releases/download/v1.0.0/app.apk"
    private val hash = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val apk = ReleaseApk(url, content.size.toLong(), hash)

    @Test fun onlyCompleteVerifiedDownloadsBecomeInstallable() {
        val output = File(folder.root, "updates/update.apk")
        val progress = mutableListOf<Int>()
        writeUpdateApk(content.inputStream(), output, apk, progress::add)
        assertArrayEquals(content, output.readBytes())
        assertEquals(100, progress.last())
        assertFalse(File(output.parentFile, "update.apk.part").exists())
    }

    @Test fun sizeHashAndCancellationFailuresRemovePartialFiles() {
        for (target in listOf(apk.copy(size = apk.size + 1), apk.copy(size = apk.size - 1), apk.copy(sha256 = "0".repeat(64)))) {
            val output = File(folder.root, "update.apk")
            assertTrue(runCatching { writeUpdateApk(content.inputStream(), output, target) }.exceptionOrNull() is IOException)
            assertFalse(output.exists())
            assertFalse(File(folder.root, "update.apk.part").exists())
        }
        val output = File(folder.root, "update.apk")
        assertTrue(runCatching {
            writeUpdateApk(content.inputStream(), output, apk, checkCancelled = { throw CancellationException() })
        }.exceptionOrNull() is CancellationException)
        assertFalse(output.exists())
        assertFalse(File(folder.root, "update.apk.part").exists())
    }

    @Test fun redirectsCannotDowngradeTransportOrLeaveTheReleaseHosts() {
        val initial = URL(url)
        assertTrue(isUpdateDownloadUrl(initial, initial))
        assertTrue(isUpdateDownloadUrl(URL("https://release-assets.githubusercontent.com/asset?token=example"), initial))
        for (candidate in listOf("http://github.com/file", "https://example.org/file",
            "https://github.com/another/repository", "https://user@release-assets.githubusercontent.com/file",
            "https://release-assets.githubusercontent.com:8443/file")) {
            assertFalse(candidate, isUpdateDownloadUrl(URL(candidate), initial))
        }
    }
}
