package org.mobilism.material.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.text.Spanned
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.designsystem.MobilismTheme

class ForumLinkActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun holdingNamedLinkCopiesDestinationWithoutOpeningItAndTapStillNavigates() {
        var opened: Long? = null
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseBody("<p><a href='/viewtopic.php?t=42'>Read this topic</a></p>", "https://forum.mobilism.org/",
                onRelease = { opened = it })
        } }
        val target = textView()
        touch(target, MotionEvent.ACTION_DOWN)
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
        touch(target, MotionEvent.ACTION_UP)
        compose.onNodeWithText("Link options").assertIsDisplayed()
        compose.onNodeWithText("Open in browser").assertIsDisplayed()
        assertNull(opened)
        compose.onNodeWithText("Copy to clipboard").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Link options").fetchSemanticsNodes().isEmpty() }
        compose.runOnUiThread {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("https://forum.mobilism.org/viewtopic.php?t=42", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        touch(target, MotionEvent.ACTION_DOWN)
        touch(target, MotionEvent.ACTION_UP)
        compose.runOnIdle { assertEquals(42L, opened) }
    }

    @Test fun cancelledLinkGestureDoesNotOpenMenuOrNavigate() {
        var opened = false
        compose.setContent { MobilismTheme(reduceMotion = true) {
            ReleaseBody("https://forum.mobilism.org/viewtopic.php?t=42", "https://forum.mobilism.org/", onRelease = { opened = true })
        } }
        val target = textView()
        touch(target, MotionEvent.ACTION_DOWN)
        touch(target, MotionEvent.ACTION_CANCEL)
        SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 100)
        compose.onNodeWithText("Link options").assertDoesNotExist()
        assertFalse(opened)
    }

    private fun textView(): TextView {
        compose.waitForIdle()
        fun find(view: View): TextView? = if (view is TextView && view.text is Spanned) view
            else if (view is ViewGroup) (0 until view.childCount).firstNotNullOfOrNull { find(view.getChildAt(it)) } else null
        return checkNotNull(find(compose.activity.window.decorView))
    }

    private fun touch(view: TextView, action: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val text = view.text as Spanned
            val span = text.getSpans(0, text.length, URLSpan::class.java).first()
            val offset = text.getSpanStart(span) + 1
            val line = view.layout.getLineForOffset(offset)
            val x = view.layout.getPrimaryHorizontal(offset) + view.totalPaddingLeft
            val y = (view.layout.getLineTop(line) + view.layout.getLineBottom(line)) / 2f + view.totalPaddingTop
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}
