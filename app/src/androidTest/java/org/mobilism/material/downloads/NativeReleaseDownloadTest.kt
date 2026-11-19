package org.mobilism.material.downloads

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.*
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferencesStore
import org.mobilism.material.ui.ReleaseScreen
import org.mobilism.material.ui.ReleaseState
import org.mobilism.material.ui.DownloadSettingsScreen
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(AndroidJUnit4::class)
class NativeReleaseDownloadTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun selectedFolderReceivesRawApkAndFlatZipWithOneRequestPerTap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val folderName = "MaterialTest-" + UUID.randomUUID()
        device.executeShellCommand("mkdir -p /sdcard/Documents/$folderName")
        val tree = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Documents/$folderName")
        val context = compose.activity
        val preferencesContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("fixture-$name", mode)
        }
        val store = DownloadPreferencesStore(preferencesContext)
        val args = InstrumentationRegistry.getArguments()
        val reduceMotion = args.getString("downloadMotion") != "full"
        val repository = DebridRepository(context, OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("<supported-filehosts/>".toResponseBody()).build()
        }.build())
        fun capture(name: String) {
            if (args.getString("downloadScreenshots") == "true") {
                compose.waitForIdle()
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                java.io.File(context.cacheDir, "download-$name.png").outputStream().use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
        }
        preferencesContext.getSharedPreferences("release-host-cooldowns", Context.MODE_PRIVATE).edit().clear().commit()
        store.useInternal(tree.toString())
        store.useBrowser()
        store.extractZips(true)
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client = releaseFileClient(preferencesContext).newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                if (workerClassName == ReleaseDownloadWorker::class.java.name) ReleaseDownloadWorker(appContext, workerParameters, client) else null
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setWorkerFactory(factory).build(),
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS)
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val apk = zip("AndroidManifest.xml" to "fixture".toByteArray(), "classes.dex" to ByteArray(400_000))
            val archive = zip("nested/Reader.apk" to apk, "notes.txt" to "notes".toByteArray())
            val direct = server.url("/release.apk").toString()
            val wrapped = server.url("/release.zip").toString()
            val cancel = server.url("/cancel.apk").toString()
            val unavailable = server.url("/unavailable.apk").toString()
            val landingPage = server.url("/landing.apk").toString()
            val html = "<b>Download Instructions:</b><br>" + listOf(direct, wrapped, cancel, unavailable, landingPage)
                .joinToString("<br>") { "<a href='$it'>File</a>" }
            val release = Release(81, "Reader v1", "Fixture", null, 0, 0, 0, contentHtml = html)
            var settings by mutableStateOf(true)
            compose.setContent { MobilismTheme(reduceMotion = reduceMotion) {
                val prefs by store.state.collectAsState()
                if (settings) DownloadSettingsScreen(prefs, store, repository, onBack = {})
                else ReleaseScreen(ReleaseState(release, busy = false), DebridState(), prefs, onBack = {}, onRetry = {},
                    onMoreComments = {}, watched = false, downloaded = false, onWatch = {}, onDownloaded = {},
                    onLike = {}, onActionMessageShown = {})
            } }
            try {
                compose.onNodeWithText("Internal downloader").assertHeightIsAtLeast(48.dp).performClick()
                assertNotNull(device.wait(Until.findObject(By.text(folderName)), 10_000))
                repeat(4) {
                    if (device.currentPackageName?.endsWith(".documentsui") == true) {
                        device.pressBack()
                        device.waitForIdle()
                    }
                }
                assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000))
                compose.onNodeWithText("Internal downloader").assertIsOff()
                compose.onNodeWithText("Internal downloader").performClick()
                assertNotNull("Picker opened in the fixture folder", device.wait(Until.findObject(By.text(folderName)), 10_000))
                device.waitForIdle()
                button(device, "Use this folder")
                val allow = By.text(Pattern.compile("Allow", Pattern.CASE_INSENSITIVE))
                try {
                    compose.waitUntil(15_000) {
                        device.findObject(allow)?.let { it.click(); device.waitForIdle() }
                        store.state.value.internalDownloads
                    }
                } catch (failure: Throwable) {
                    val snapshot = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
                    val labels = Regex("text=\"([^\"]+)\"").findAll(snapshot).map { it.groupValues[1] }.joinToString()
                    throw AssertionError("Folder selection ended on $labels", failure)
                }
                assertEquals(tree.toString(), DownloadPreferencesStore(preferencesContext).state.value.downloadFolder)
                capture("settings")
                compose.runOnIdle { settings = false }
                compose.onNodeWithTag("open-downloads").performClick()
                fun tap(url: String) {
                    compose.onNode(hasTestTag("download:$url") and hasAnyAncestor(hasTestTag("release-downloads-sheet")))
                        .performScrollTo().performClick()
                }
                server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream")
                    .setHeader("Content-Disposition", "attachment; filename=\"Reader.apk\"").setBody(Buffer().write(apk)))
                tap(direct)
                compose.waitUntil(20_000) { compose.onAllNodesWithText("Saved to your folder").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Reader.apk").assertIsDisplayed()
                compose.onNodeWithText("Open file").assertHeightIsAtLeast(48.dp)
                capture("saved")
                device.pressBack()
                server.enqueue(MockResponse().setBody(Buffer().write(archive)))
                tap(wrapped)
                compose.waitUntil(20_000) { compose.onAllNodesWithText("Extracted 2 files").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Close").performClick()
                val resolver = context.contentResolver
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val names = mutableListOf<String>()
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null, null, null)!!.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        names += name
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(1))
                        val expected = if (name == "notes.txt") "notes".toByteArray() else apk
                        assertArrayEquals(name, expected, resolver.openInputStream(uri)!!.use { it.readBytes() })
                    }
                }
                assertEquals(3, names.size)
                assertTrue("Raw APK, collision-safe second APK, and notes: $names", "Reader.apk" in names && names.count { it.endsWith(".apk") } == 2)
                server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(1_024_000))).throttleBody(4096, 100, TimeUnit.MILLISECONDS))
                tap(cancel)
                compose.waitUntil(15_000) { server.requestCount == 3 }
                val advancing = SemanticsMatcher("Transfer has made progress") {
                    val fraction = it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current ?: 0f
                    fraction > 0f && fraction < 1f
                }
                compose.waitUntil(10_000) { compose.onAllNodes(advancing).fetchSemanticsNodes().isNotEmpty() }
                capture("active")
                runBlocking {
                    assertFalse(ReleaseDownloads.enqueue(context, cancel, "Duplicate", tree.toString(), false) {
                        error("Duplicate resolved again")
                    })
                }
                compose.onNodeWithText("Cancel download").performClick()
                compose.waitUntil(10_000) { compose.onAllNodesWithText("Cancelled").fetchSemanticsNodes().isNotEmpty() }
                assertEquals(3, server.requestCount)
                compose.onNodeWithText("Close").performClick()
                server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("<html>Sign in</html>"))
                tap(landingPage)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("host returned a webpage", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Close").performClick()
                server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
                tap(unavailable)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("HTTP 429", substring = true).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText("Close").performClick()
                tap(unavailable)
                compose.waitUntil(10_000) { compose.onAllNodesWithText("asked you to wait", substring = true).fetchSemanticsNodes().isNotEmpty() }
                assertEquals("Retry-After prevented a second host request", 5, server.requestCount)
                resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use {
                    assertEquals("No cancelled or failed file", 3, it.count)
                }
                val request = server.takeRequest(1, TimeUnit.SECONDS)!!
                assertEquals("GET", request.method)
                assertTrue(request.getHeader("User-Agent").orEmpty().contains("MobilismMaterial/"))
                assertNull(request.getHeader("Authorization"))
                assertNull(request.getHeader("Cookie"))
            } finally {
                if (context.contentResolver.persistedUriPermissions.any { it.uri == tree }) {
                    val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    DocumentsContract.deleteDocument(context.contentResolver, root)
                } else device.executeShellCommand("rmdir /sdcard/Documents/$folderName")
            }
        }
    }

    private fun button(device: UiDevice, label: String) {
        val button = device.wait(Until.findObject(By.text(Pattern.compile(Pattern.quote(label), Pattern.CASE_INSENSITIVE))), 10_000)
        assertNotNull("Folder picker: $label", button)
        button.click()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            for ((name, body) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body)
                zip.closeEntry()
            }
        }
        bytes.toByteArray()
    }
}
