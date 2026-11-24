package org.mobilism.material

import android.view.accessibility.AccessibilityEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.DownloadLink
import org.mobilism.material.ui.DownloadLinkRow
import org.mobilism.material.ui.DownloadRequestTracker
import org.mobilism.material.ui.openReleaseTopic
import org.mobilism.material.ui.premiumForScreen

@RunWith(AndroidJUnit4::class)
class DebridDownloadsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val snackbar = SnackbarHostState()
    private val original = "https://files.example.org/file"
    private val direct = "https://downloads.example.org/file.apk"
    private val verified = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid,
        configured = true, validated = true, hostsVerified = true,
        hosts = listOf(SupportedHost("files.example.org", "Example host")))), enabled = true)

    @Test fun notificationReleaseUpdatesTheOpenHostButtonWhenPremiumFinishesRestoring() {
        val accounts = MutableStateFlow(DebridState())
        val auth = AuthState.SignedIn("Reader")
        val check = compose.onNodeWithTag("premium-download-check", useUnmergedTree = true)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            val nav = rememberNavController()
            NavHost(nav, startDestination = "following") {
                composable("following") { LaunchedEffect(Unit) { nav.openReleaseTopic(42) } }
                composable("release/{id}") {
                    val debrid = premiumForScreen(accounts, auth, enabled = true)
                    DownloadLinkRow(DownloadLink(original), debrid, onOpened = {}, snackbar = snackbar)
                }
            }
        } }
        compose.onNodeWithTag("download:$original").assertIsDisplayed()
        check.assertDoesNotExist()
        compose.runOnIdle { accounts.value = verified.copy(accounts = verified.accounts.map { it.copy(forumOwner = "Reader") }) }
        check.assertIsDisplayed()
        compose.runOnIdle { accounts.value = verified.copy(accounts = verified.accounts.map { it.copy(forumOwner = "Other") }) }
        check.assertDoesNotExist()
    }

    @Test fun sharedLookupDisablesBothPresentationsAndDismissalAllowsRetry() {
        val requests = DownloadRequestTracker()
        var firstVisible by mutableStateOf(true)
        var resolved = 0
        val result = CompletableDeferred<String>()
        val opened = mutableListOf<String>()
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Column {
                if (firstVisible) DownloadLinkRow(DownloadLink(original), verified, onOpened = {},
                    resolveLink = { resolved++; result.await() }, openLink = { opened += it; true },
                    requests = requests, snackbar = snackbar)
                DownloadLinkRow(DownloadLink(original), verified, onOpened = {},
                    resolveLink = { resolved++; direct }, openLink = { opened += it; true },
                    requests = requests, snackbar = snackbar)
            }
        } }
        compose.onAllNodesWithText("Example host")[0].performClick()
        compose.onAllNodesWithText("Resolving Example host").assertCountEquals(2)
        compose.onAllNodesWithText("Resolving Example host")[1].assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, resolved); firstVisible = false }
        compose.waitUntil { requests.pending.value.isEmpty() }
        compose.runOnIdle { result.complete(direct) }
        compose.runOnIdle { assertTrue(opened.isEmpty()) }
        compose.onNodeWithText("Example host").performClick()
        compose.runOnIdle { assertEquals(2, resolved); assertEquals(listOf(direct), opened) }
    }

    @Test fun opensResolvedLinkOnceAndCopyNeverResolves() {
        val result = CompletableDeferred<String>()
        val opened = mutableListOf<String>()
        var resolved = 0
        var downloads = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), verified, onOpened = { downloads++ },
                resolveLink = { assertEquals(original, it); resolved++; result.await() },
                openLink = { opened += it; true }, snackbar = snackbar)
        } }
        compose.onNodeWithText("Real-Debrid", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("premium-download-check", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy Example host link").performClick()
        compose.runOnIdle { assertEquals(0, resolved); assertEquals(0, downloads) }
        compose.onNodeWithText("Example host").performClick()
        compose.onNodeWithText("Resolving Example host").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, resolved); assertTrue(opened.isEmpty()); result.complete(direct) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(direct), opened); assertEquals(1, downloads) }
    }

    @Test fun missingFileLeavesMirrorsVisibleAndOnlyOpensHostByExplicitChoice() {
        val opened = mutableListOf<String>()
        compose.setContent { MobilismTheme(reduceMotion = true) {
            Column {
                DownloadLinkRow(DownloadLink(original), verified, onOpened = {},
                    resolveLink = { throw DebridException("Unavailable", fileUnavailable = true) },
                    openLink = { opened += it; true }, snackbar = snackbar)
                SnackbarHost(snackbar)
            }
        } }
        compose.onNodeWithText("Example host").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.download_file_unavailable)).assertIsDisplayed()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Example host").assertIsEnabled()
        compose.runOnIdle {
            assertTrue(opened.isEmpty())
            assertEquals(SnackbarDuration.Long, snackbar.currentSnackbarData?.visuals?.duration)
        }
        compose.onNodeWithText(compose.activity.getString(R.string.download_open_original)).performClick()
        compose.runOnIdle { assertEquals(listOf(original), opened) }
    }

    @Test fun failedResolutionOpensOriginalHostOnce() {
        val opened = mutableListOf<String>()
        var downloads = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), verified, onOpened = { downloads++ },
                resolveLink = { throw DebridException("The host is temporarily unavailable.") },
                openLink = { opened += it; true }, snackbar = snackbar)
        } }
        val message = compose.activity.getString(R.string.download_premium_fallback)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeAndWaitForEvent(
            { compose.onNodeWithText("Example host").performClick() },
            { event -> event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && event.text.any { it.toString() == message } },
            5_000,
        )
        compose.runOnIdle { assertEquals(listOf(original), opened); assertEquals(1, downloads) }
    }

    @Test fun unsupportedApkOpensOriginalEvenWithDownloaderEnabled() {
        val opened = mutableListOf<String>()
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(direct), verified, onOpened = {},
                resolveLink = { error("Unexpected resolution") }, openLink = { opened += it; true },
                downloadFile = { _, _ -> error("Unexpected native download") }, snackbar = snackbar)
        } }
        compose.onNodeWithTag("premium-download-check", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("download:$direct").performClick()
        compose.runOnIdle { assertEquals(listOf(direct), opened) }
    }

    @Test fun premiumInternalDownloadShowsOnlyTheDownloadAndCopyActions() {
        val saved = mutableListOf<String>()
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), verified, onOpened = {},
                resolveLink = { direct }, openLink = { error("Unexpected browser launch") },
                downloadFile = { _, resolve -> saved += resolve() }, snackbar = snackbar)
        } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose.onNodeWithContentDescription("Copy Example host link").assertIsDisplayed()
        compose.onNodeWithText("Example host").performClick()
        compose.runOnIdle { assertEquals(listOf(direct), saved) }
    }

    @Test fun unverifiedAccountsUseOrdinaryHostLinks() {
        var resolved = false
        val opened = mutableListOf<String>()
        val unverified = verified.copy(accounts = verified.accounts.map { it.copy(validated = false) })
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), unverified, onOpened = {},
                resolveLink = { resolved = true; direct }, openLink = { opened += it; true }, snackbar = snackbar)
        } }
        compose.onNodeWithTag("premium-download-check", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Example host").performClick()
        compose.runOnIdle { assertFalse(resolved); assertEquals(listOf(original), opened) }
    }
}
