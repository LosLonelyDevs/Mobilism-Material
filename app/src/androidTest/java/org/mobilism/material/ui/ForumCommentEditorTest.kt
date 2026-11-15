package org.mobilism.material.ui

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.designsystem.MobilismTheme

@SdkSuppress(minSdkVersion = 29)
class ForumCommentEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val pageText = AtomicReference<String>()

    @Test fun editingKeepsTheForumSourceAndSendsOnlyWhenTheFormIsSubmitted() {
        val posts = showForm(ForumCommentAction.Edit, """
            <textarea name="message">[quote=Author]Original[/quote][b]My comment[/b]</textarea>
            <button type="submit" name="post">Submit</button>
        """)
        assertEquals(0, posts.get())
        assertEquals("\"[quote=Author]Original[/quote][b]My comment[/b]\"",
            evaluate("document.querySelector('textarea').value"))
        evaluate("document.querySelector('textarea').value = '[quote=Author]Original[/quote][b]Edited comment[/b]'")
        assertEquals(0, posts.get())
        evaluate("document.querySelector('button').click()")
        compose.waitUntil(10_000) { posts.get() == 1 }
        compose.waitUntil(10_000) { pageText.get()?.contains("Saved") == true }
        compose.onNodeWithText("Close").performClick()
        assertEquals(1, posts.get())
    }

    @Test fun deletionWaitsForForumConfirmationAndReopeningNeverResubmits() {
        val posts = showForm(ForumCommentAction.Delete, """
            <p>Are you sure you want to delete this post?</p>
            <button type="submit" name="confirm">Yes</button>
        """)
        assertEquals(0, posts.get())
        evaluate("document.querySelector('button').click()")
        compose.waitUntil(10_000) { posts.get() == 1 }
        compose.waitUntil(10_000) { pageText.get()?.contains("Saved") == true }
        compose.onNodeWithText("Open comment form").performClick()
        compose.waitUntil(10_000) { pageText.get()?.contains("Are you sure") == true }
        assertEquals(1, posts.get())
    }

    private fun showForm(action: ForumCommentAction, fields: String): AtomicInteger {
        val target = ForumCommentTarget(42, 123, action)
        val posts = AtomicInteger()
        compose.setContent { MobilismTheme(reduceMotion = true) { ForumCommentEditor(target, "Reader", {}) } }
        val html = """<html><body><form method="post" action="${target.url.replace("&", "&amp;")}">
            <input type="hidden" name="form_token" value="fixture-token">$fields</form></body></html>"""
        compose.runOnIdle {
            val view = browser()
            view.stopLoading()
            val client = view.webViewClient
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    client.shouldOverrideUrlLoading(view, request)

                override fun onPageFinished(view: WebView, url: String) {
                    client.onPageFinished(view, url)
                    view.evaluateJavascript("document.body.textContent") { pageText.set(it) }
                }

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val body = if (!target.accepts(request.url.toString())) "Blocked" else if (request.method == "POST") {
                        posts.incrementAndGet()
                        "<html><body>Saved</body></html>"
                    } else html
                    return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(body.toByteArray()))
                }
            }
            view.loadDataWithBaseURL(target.url, html, "text/html", "UTF-8", target.url)
        }
        compose.waitUntil(10_000) { evaluate("!!document.querySelector('form')") == "true" }
        return posts
    }

    private fun evaluate(script: String): String {
        var result: String? = null
        compose.runOnIdle { browser().evaluateJavascript(script) { result = it } }
        compose.waitUntil(10_000) { result != null }
        return result!!
    }

    private fun browser(): WebView = WindowInspector.getGlobalWindowViews()
        .asSequence().flatMap(::descendants).filterIsInstance<WebView>().single()

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
