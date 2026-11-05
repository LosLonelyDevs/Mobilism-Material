package org.mobilism.material.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.members.MemberIdentityStore

class UsernameAvatarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun profile(reference: ProfileReference, avatar: String?) = UserProfile(
        reference, reference.username!!, avatar, setOf(AccountRole.Member), "", emptyList(),
        "", emptyMap(), null, null, null, null)

    private fun avatarFile(): File {
        val file = File.createTempFile("username-avatar-", ".png", compose.activity.cacheDir)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test fun releaseAuthorLoadsAvatarAndReleaserBadgeBeforeAnyProfileVisit() {
        val file = avatarFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val ready = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val identities = MemberIdentityStore(scope, { reference ->
            calls.incrementAndGet()
            ready.await()
            profile(reference, file.absolutePath).copy(rank = "Major Android Releaser")
        })
        var opened: ProfileReference? = null
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalMemberIdentities provides identities,
                    LocalProfileHandler provides { opened = it }) {
                    Row(Modifier.width(320.dp)) { UsernameLink("Author", Modifier.weight(1f)) }
                }
            } }
            compose.onNodeWithText("Author").assertIsDisplayed()
            compose.onNodeWithContentDescription("Author profile picture", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("major-android-releaser-badge-Author", useUnmergedTree = true).assertDoesNotExist()
            compose.runOnIdle { assertNull(opened); ready.complete(Unit) }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Author profile picture", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithText("A", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithContentDescription("Author profile picture", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("major-android-releaser-badge-Author", useUnmergedTree = true).assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, calls.get()); assertNull(opened) }
            compose.onNodeWithText("Author (Major Android Releaser)").performClick()
            compose.runOnIdle { assertEquals("Author", opened?.username) }
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun commentsFetchMissingAvatarsWithoutShowingDuplicatePictures() {
        val file = avatarFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val calls = AtomicInteger()
        val identities = MemberIdentityStore(scope, { reference ->
            calls.incrementAndGet()
            profile(reference, file.absolutePath)
        })
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalMemberIdentities provides identities) {
                    ReleaseCommentCard(ReleaseComment(1, "Author", null, 0, "<p>Comment</p>"),
                        "https://forum.mobilism.org/viewtopic.php?t=1", onImage = { _, _ -> })
                }
            } }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithContentDescription("Author profile picture", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodesWithContentDescription("Author profile picture", useUnmergedTree = true).assertCountEquals(1)
            compose.runOnIdle { assertEquals(1, calls.get()) }
        } finally { scope.cancel(); file.delete() }
    }

    @Test fun missingAvatarKeepsUsernameVisible() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val identities = MemberIdentityStore(scope, { reference -> profile(reference, null) })
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalMemberIdentities provides identities) {
                    Column { UsernameLink("Author") }
                }
            } }
            compose.waitUntil { identities.identities.value.containsKey("author") }
            compose.onNodeWithText("Author").assertIsDisplayed()
            compose.onNodeWithContentDescription("Author profile picture", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("major-android-releaser-badge-Author", useUnmergedTree = true).assertDoesNotExist()
        } finally { scope.cancel() }
    }
}
