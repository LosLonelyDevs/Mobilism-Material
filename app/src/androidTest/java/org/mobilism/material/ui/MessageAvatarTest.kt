package org.mobilism.material.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
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
        try {
            compose.setContent { MobilismTheme(reduceMotion = true) {
                androidx.compose.foundation.layout.Column {
                    MessageAvatar("Reader", file.absolutePath, Modifier.size(32.dp))
                    MessageAvatar("Author", null, Modifier.size(32.dp))
                }
            } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("R").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithContentDescription("Reader profile picture").assertIsDisplayed()
            compose.onNodeWithText("A").assertIsDisplayed()
        } finally { file.delete() }
    }
}
