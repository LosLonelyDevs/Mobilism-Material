package org.mobilism.material.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import coil3.asDrawable
import kotlinx.coroutines.runBlocking
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import java.io.File

class MessageAvatarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun loadedProfileImageReplacesInitialAndMissingImageKeepsFallback() {
        val file = File(compose.activity.cacheDir, "avatar-fixture.png")
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val identities = org.mobilism.material.members.MemberIdentityStore(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main), { error("No profile") })
        identities.remember(UserProfile(ProfileReference(username = "Verified"), "Verified", file.absolutePath,
            setOf(AccountRole.Member), "", emptyList(), "", emptyMap(), null, null, null, null))
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                CompositionLocalProvider(LocalMemberIdentities provides identities) {
                androidx.compose.foundation.layout.Column {
                    MessageAvatar("Reader", file.absolutePath, Modifier.size(32.dp))
                    MessageAvatar("Author", null, Modifier.size(32.dp))
                    MessageAvatar("Verified", null, Modifier.size(32.dp))
                }
                }
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("R").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Reader profile picture").assertIsDisplayed()
            compose.onNodeWithText("A").assertIsDisplayed()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("V").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Verified profile picture").assertIsDisplayed()
        } finally { file.delete() }
    }

    @Test fun defaultImageLoaderDecodesGifAsAnimation() = runBlocking {
        val bytes = java.io.ByteArrayOutputStream()
        fun write(vararg values: Int) { values.forEach(bytes::write) }
        bytes.write("GIF89a".toByteArray(Charsets.US_ASCII))
        write(1, 0, 1, 0, 0x80, 0, 0, 0, 0, 0, 255, 255, 255)
        for (pixel in 0..1) {
            write(0x21, 0xf9, 4, 0, 10, 0, 0, 0)
            write(0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0)
            write(2, 2, if (pixel == 0) 0x44 else 0x4c, 1, 0)
        }
        write(0x3b)
        val loader = coil3.ImageLoader.Builder(compose.activity).build()
        try {
            val result = loader.execute(coil3.request.ImageRequest.Builder(compose.activity).data(bytes.toByteArray()).build())
            assertTrue(result.toString(), result is coil3.request.SuccessResult)
            val drawable = (result as coil3.request.SuccessResult).image.asDrawable(compose.activity.resources)
            assertTrue("GIF must retain its frames", drawable is android.graphics.drawable.Animatable)
        } finally { loader.shutdown() }
    }
}
