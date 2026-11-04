package org.mobilism.material

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.AppRelease
import org.mobilism.material.data.AppReleaseUpdates
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.AppUpdateSettings
import org.mobilism.material.ui.AppUpdateHost
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.mobilism.material.updates.*

@RunWith(AndroidJUnit4::class)
class AppUpdatesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun updateModalOpensTheSubmenuAndStartsOneDownloadThatSurvivesNavigation() {
        var downloads = 0
        val checker = AppUpdateChecker(Saved(), { newer }, { true })
        val updater = AppUpdateViewModel(File(compose.activity.cacheDir, "updater-ui-test.apk"), { _, _ -> }) { _, _, progress ->
            downloads++
            progress(42)
            CompletableDeferred<Unit>().await()
        }
        runBlocking { checker.check(automatic = true) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            AppUpdateHost(checker, updater, null, {}) { open -> Button(open) { Text("Open update settings") } }
        } }
        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("Updates").assertIsDisplayed()
        compose.waitUntil { downloads == 1 && updater.state.value.progress == 42 }
        compose.onNodeWithText("42% downloaded").performScrollTo().assertIsDisplayed()
        capture("app-updates-progress.png")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Update available").assertDoesNotExist()
        compose.onNodeWithText("Open update settings").performClick()
        compose.onNodeWithText("42% downloaded").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, downloads) }
        compose.onNodeWithContentDescription("Cancel download").performScrollTo().performClick()
        compose.waitUntil { updater.state.value.phase == UpdatePhase.Idle }
        compose.runOnIdle { assertFalse(updater.state.value.installRequested) }
    }

    @Test fun changelogDismissalPrecedesTheAvailableUpdateDialog() {
        val checker = AppUpdateChecker(Saved(), { newer }, { true })
        val updater = AppUpdateViewModel(File(compose.activity.cacheDir, "unused-ui-test.apk"), { _, _ -> })
        runBlocking { checker.check(automatic = true) }
        compose.setContent { MobilismTheme(reduceMotion = true) {
            var notes by remember { mutableStateOf(AppChangelog("0.1.18", listOf("First change.", "Second change.")) as AppChangelog?) }
            AppUpdateHost(checker, updater, notes, { notes = null }) { }
        } }
        compose.onNodeWithText("What's new").assertIsDisplayed()
        compose.onNodeWithText("Update available").assertDoesNotExist()
        capture("app-changelog-modal.png")
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("Update available").assertIsDisplayed()
        capture("app-update-modal.png")
        compose.onNodeWithText("Later").performClick()
        compose.runOnIdle { assertNull(checker.state.value.prompt) }
    }

    @Test fun installerRejectsSameVersionAndForeignPackagesAndSharesOnlyTheUpdateDirectory() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val currentApk = File(context.applicationInfo.sourceDir)
        val current = newer.copy(version = BuildConfig.VERSION_NAME)
        assertTrue(runCatching { validateUpdatePackage(context, currentApk, current) }.exceptionOrNull() is IllegalArgumentException)
        val testApk = File(instrumentation.context.applicationInfo.sourceDir)
        assertTrue(runCatching { validateUpdatePackage(context, testApk, newer) }.exceptionOrNull() is IllegalArgumentException)
        val update = File(context.cacheDir, "updates/test.apk").apply { parentFile?.mkdirs(); writeText("test") }
        try {
            val intent = updateInstallIntent(context, update)
            assertEquals("content", intent.data?.scheme)
            assertEquals("${context.packageName}.updates", intent.data?.authority)
            assertTrue(intent.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(runCatching { updateInstallIntent(context, File(context.cacheDir, "outside.apk")) }.isFailure)
        } finally { update.delete() }
    }

    @Test fun liveGithubReleaseCanBeCheckedManually() = kotlinx.coroutines.runBlocking<Unit> {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdates") == "true")
        val release = AppReleaseUpdates().latest()
        assertNotNull(org.mobilism.material.data.AppVersions.parts(release.version))
        assertTrue(release.pageUrl.startsWith(AppReleaseUpdates.REPOSITORY_URL + "/releases/tag/"))
        assertNotNull(release.apkUrl)
    }

    @Test fun manualCheckWorksWithAutomaticChecksOffAndStartsTheDownload() {
        var reads = 0
        val opened = mutableListOf<String>()
        val checker = AppUpdateChecker(Saved(), { reads++; newer }, { true })
        screen(checker) { opened += it }
        compose.onNodeWithText("Enable app updates").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        compose.waitUntil { reads == 1 && !checker.state.value.checking }
        compose.onNodeWithText("Version 99.0.0 is available.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(newer.apkUrl), opened) }
        capture()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        compose.waitUntil { reads == 2 && !checker.state.value.checking }
    }

    @Test fun errorCanBeRetriedAndCurrentReleaseShowsUpToDate() {
        var reads = 0
        val checker = AppUpdateChecker(Saved(), {
            reads++
            if (reads == 1) throw IOException("offline")
            newer.copy(version = BuildConfig.VERSION_NAME)
        }, { true })
        screen(checker)
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        compose.waitUntil { checker.state.value.error != null }
        compose.onNodeWithText("Couldn't check for updates. Try again.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Check for updates").performScrollTo().performClick()
        compose.waitUntil { reads == 2 && !checker.state.value.checking }
        compose.onNodeWithText("You're up to date (v${BuildConfig.VERSION_NAME}).").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }

    @Test fun toggleDefaultsOnAndPersistsAcrossInstances() {
        val prefix = "app-updates-test-${System.nanoTime()}-"
        val context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
        val checker = AppUpdateChecker(context)
        assertTrue(checker.state.value.enabled)
        checker.setEnabled(false)
        assertFalse(AppUpdateChecker(context).state.value.enabled)
        checker.setEnabled(true)
        assertTrue(AppUpdateChecker(context).state.value.enabled)
    }

    @Test fun dismissedChangelogStaysDismissedAcrossStoreInstances() {
        val prefix = "changelog-test-${System.nanoTime()}-"
        val context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences(prefix + name, mode)
        }
        val store = AppChangelogStore(context)
        assertEquals(BuildConfig.VERSION_NAME, store.pending()?.version)
        store.markSeen(BuildConfig.VERSION_NAME)
        assertNull(AppChangelogStore(context).pending())
    }

    @Test fun allowedNotificationUsesItsOwnChannelAndCanBeCancelled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val permissionWasGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!permissionWasGranted) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val notifications = AppUpdateNotifications(context)
        try {
            assertTrue(notifications.post(newer))
            val manager = context.getSystemService(NotificationManager::class.java)
            compose.waitUntil(5_000) { manager.activeNotifications.any { it.tag == AppUpdateNotifications.TAG } }
            val posted = manager.activeNotifications.single { it.tag == AppUpdateNotifications.TAG }
            assertEquals(AppUpdateNotifications.CHANNEL, posted.notification.channelId)
            assertEquals("Mobilism Material 99.0.0 is available", posted.notification.extras.getString("android.title"))
            assertNotNull(posted.notification.contentIntent)
            notifications.cancel()
            compose.waitUntil(5_000) { manager.activeNotifications.none { it.tag == AppUpdateNotifications.TAG } }
            assertTrue(manager.activeNotifications.none { it.tag == AppUpdateNotifications.TAG })
        } finally {
            notifications.cancel()
        }
    }

    private fun screen(checker: AppUpdateChecker, open: (String) -> Unit = {}) {
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Surface(Modifier.fillMaxSize()) {
                AppUpdateSettings(checker, { open(requireNotNull(it.apkUrl)) }, modifier = Modifier.safeDrawingPadding())
            }
        } }
    }

    private fun capture(name: String = "app-update-check.png") {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(500, 5_000)
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private class Saved : AppUpdateSettings {
        override var dismissedVersion: String? = null
        override var cachedRelease: AppRelease? = null
        override var enabled = true
        override var lastAutomaticAttempt = 0L
        override var notifiedVersion: String? = null
        override var permissionRequested = false
    }

    private val newer = AppRelease("99.0.0", "${AppReleaseUpdates.REPOSITORY_URL}/releases/tag/v99.0.0",
        "${AppReleaseUpdates.REPOSITORY_URL}/releases/download/v99.0.0/MobilismMaterial_v99.0.0-release.apk", 100)
}
