package org.mobilism.material

import android.graphics.Bitmap
import android.text.Spanned
import android.text.style.ClickableSpan
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.settings.DownloadPreferences
import org.mobilism.material.ui.*

@RunWith(AndroidJUnit4::class)
class ReleaseContentUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun tappingTruncatedRelativeTopicLinkUsesFullHrefAndReturnsToSource() {
        val label = "https://forum.mobilism.org/viewtopic.ph ... example"
        val html = "<a href='/viewtopic.php?f=429&amp;t=6286022&amp;sid=example'>$label</a>"
        var target by mutableStateOf<Long?>(null)
        compose.setContent { MobilismTheme(reduceMotion = true) {
            if (target == null) TestRelease(html) { target = it }
            else Column {
                Text("Linked release $target")
                Button(onClick = { target = null }) { Text("Back to source") }
            }
        } }
        onView(allOf(isAssignableFrom(TextView::class.java), withText(containsString(label)))).perform(
            GeneralClickAction(Tap.SINGLE, { view ->
                val text = view as TextView
                val spanned = text.text as Spanned
                val span = spanned.getSpans(0, spanned.length, ClickableSpan::class.java).single()
                val offset = spanned.getSpanStart(span) + 2
                val line = text.layout.getLineForOffset(offset)
                val position = IntArray(2).also(text::getLocationOnScreen)
                floatArrayOf(position[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(offset) - text.scrollX,
                    position[1] + text.totalPaddingTop + (text.layout.getLineTop(line) + text.layout.getLineBottom(line)) / 2f - text.scrollY)
            }, Press.FINGER),
        )
        compose.onNodeWithText("Linked release 6286022").assertIsDisplayed()
        compose.onNodeWithText("Back to source").performClick()
        onView(withText(containsString(label))).check(matches(isDisplayed()))
        assertNull(target)
    }

    @Test fun bareReleaseUrlNavigatesInsideApp() {
        var target: Long? = null
        val url = "https://forum.mobilism.org/viewtopic.php?t=12345"
        compose.setContent { MobilismTheme(reduceMotion = true) { TestRelease("<p>See $url.</p>") { target = it } } }
        onView(allOf(isAssignableFrom(TextView::class.java), withText(containsString(url)))).perform(
            GeneralClickAction(Tap.SINGLE, { view ->
                val text = view as TextView
                val spanned = text.text as Spanned
                val span = spanned.getSpans(0, spanned.length, ClickableSpan::class.java).single()
                val offset = spanned.getSpanStart(span) + 2
                val line = text.layout.getLineForOffset(offset)
                val position = IntArray(2).also(text::getLocationOnScreen)
                floatArrayOf(position[0] + text.totalPaddingLeft + text.layout.getPrimaryHorizontal(offset) - text.scrollX,
                    position[1] + text.totalPaddingTop + (text.layout.getLineTop(line) + text.layout.getLineBottom(line)) / 2f - text.scrollY)
            }, Press.FINGER))
        compose.runOnIdle { assertEquals(12345L, target) }
    }

    @Test fun variantHeadingsAndNotesAreVisibleWithTheirMirrors() {
        val html = "<p>Two packages for different setups.</p><b>Download Instructions:</b><br>" +
            "<b>arm64-v8a:</b><br><a href='https://files.example.org/standard'>File</a><br>" +
            "<b>with GmsCore support:</b><br><b>arm64-v8a:</b><br>" +
            "<p>Keep your existing configuration before installing.</p>" +
            "<a href='https://files.example.org/gms'>File</a><br>Mirror:<br>" +
            "<a href='https://backup.example.org/gms'>Mirror</a>"
        compose.setContent { MobilismTheme(reduceMotion = true) { TestRelease(html) {} } }
        compose.onNodeWithText("arm64-v8a", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("with GmsCore support / arm64-v8a").performScrollTo().assertIsDisplayed()
        onView(withText(containsString("Keep your existing configuration"))).check(matches(isDisplayed()))
        capture("download-variants")
    }

    @Composable private fun TestRelease(html: String, onRelease: (Long) -> Unit) {
        val release = Release(6439280, "Example v2.0.3", "Uploader", null, 0, 0, 0, contentHtml = html)
        ReleaseScreen(ReleaseState(release, busy = false), DebridState(), DownloadPreferences(),
            onBack = {}, onRetry = {}, onMoreComments = {}, watched = false, downloaded = false,
            onWatch = {}, onDownloaded = {}, onLike = {}, onActionMessageShown = {}, onRelease = onRelease)
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = instrumentation.uiAutomation.takeScreenshot()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "release-content-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}
