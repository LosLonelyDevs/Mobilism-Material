package org.mobilism.material

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.DownloadLink
import org.mobilism.material.ui.DownloadLinkRow

@RunWith(AndroidJUnit4::class)
class DebridDownloadsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val original = "https://files.example.org/file"
    private val direct = "https://downloads.example.org/file.apk"
    private val verified = DebridState(listOf(DebridAccount(DebridProvider.RealDebrid,
        configured = true, validated = true, hostsVerified = true,
        hosts = listOf(SupportedHost("files.example.org", "Example host")))), enabled = true)

    @Test fun opensResolvedLinkOnceAndCopyNeverResolves() {
        val result = CompletableDeferred<String>()
        val opened = mutableListOf<String>()
        var resolved = 0
        var downloads = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), verified, onOpened = { downloads++ },
                resolveLink = { assertEquals(original, it); resolved++; result.await() },
                openLink = { opened += it; true }, onCopied = {})
        } }
        compose.onNodeWithText("Real-Debrid", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy Example host link").performClick()
        compose.runOnIdle { assertEquals(0, resolved); assertEquals(0, downloads) }
        compose.onNodeWithText("Example host").performClick()
        compose.onNodeWithText("Resolving Example host").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, resolved); assertTrue(opened.isEmpty()); result.complete(direct) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf(direct), opened); assertEquals(1, downloads) }
    }

    @Test fun failureWaitsForExplicitOriginalHostChoice() {
        val opened = mutableListOf<String>()
        var downloads = 0
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), verified, onOpened = { downloads++ },
                resolveLink = { throw DebridException("The host is temporarily unavailable.") },
                openLink = { opened += it; true }, onCopied = {})
        } }
        compose.onNodeWithText("Example host").performClick()
        compose.onNodeWithText("The host is temporarily unavailable.").assertIsDisplayed()
        compose.runOnIdle { assertTrue(opened.isEmpty()); assertEquals(0, downloads) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertTrue(opened.isEmpty()) }
        compose.onNodeWithText("Example host").performClick()
        compose.onNodeWithText("Open original host").performClick()
        compose.runOnIdle { assertEquals(listOf(original), opened); assertEquals(1, downloads) }
    }

    @Test fun unverifiedAccountsUseOrdinaryHostLinks() {
        var resolved = false
        val opened = mutableListOf<String>()
        val unverified = verified.copy(accounts = verified.accounts.map { it.copy(validated = false) })
        compose.setContent { MobilismTheme(reduceMotion = true) {
            DownloadLinkRow(DownloadLink(original), unverified, onOpened = {},
                resolveLink = { resolved = true; direct }, openLink = { opened += it; true }, onCopied = {})
        } }
        compose.onNodeWithText("Example host").performClick()
        compose.runOnIdle { assertFalse(resolved); assertEquals(listOf(original), opened) }
    }
}
