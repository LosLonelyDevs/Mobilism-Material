package org.mobilism.material

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.runner.AndroidJUnitRunner
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.mobilism.material.data.AppUserAgent
import org.mobilism.material.data.VerificationSite

class LiveVerificationProbeRunner : AndroidJUnitRunner()

@SdkSuppress(minSdkVersion = 29)
class LiveVerificationProbeTest {
    @Test fun publishedForumRecoveryFollowsPublicRedirect() = checkRecovery(VerificationSite.Forum, manual = true)

    @Test fun silentForumRecoveryFollowsPublicRedirect() = checkRecovery(VerificationSite.Forum, manual = false)

    @Test fun silentApiRecoveryCompletes() = checkRecovery(VerificationSite.App, manual = false)

    private fun checkRecovery(site: VerificationSite, manual: Boolean) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveVerificationProbe") == "true")
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            delay(2_000)
            val application = instrumentation.targetContext.applicationContext as MobilismApplication
            val recovery = async {
                if (manual) application.connectionRecovery.verifyManually(site)
                else application.connectionRecovery.recover(site)
            }
            var neededInteraction = false
            repeat(30) {
                if (!manual && application.connectionRecovery.attempt.value?.interactive == true) neededInteraction = true
                if (!recovery.isCompleted) delay(1_000)
            }
            var browserState = "none"
            val pageState = CompletableDeferred<String>()
            instrumentation.runOnMainSync {
                val browsers = WindowInspector.getGlobalWindowViews().asSequence()
                    .flatMap(::descendants).filterIsInstance<WebView>().toList()
                browserState = browsers.joinToString { browser ->
                    "url=${browser.url?.substringBefore('?')}, title=${browser.title}, attached=${browser.isAttachedToWindow}, " +
                        "shown=${browser.isShown}, size=${browser.width}x${browser.height}"
                }
                val browser = browsers.firstOrNull()
                if (browser == null) pageState.complete("none") else browser.evaluateJavascript(
                    "JSON.stringify({title:document.title,ready:document.readyState,length:document.body?.innerText?.length," +
                        "challenge:!!document.querySelector('[id*=cf-chl],iframe[src*=challenges]')})") {
                    pageState.complete(it.take(500))
                }
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("verificationBrowser", browserState)
                    putString("requestIdentity", AppUserAgent.value(application))
                })
            }
            instrumentation.sendStatus(0, Bundle().apply { putString("verificationPage", withTimeoutOrNull(2_000) { pageState.await() }) })
            val result = if (recovery.isCompleted) recovery.await() else null
            recovery.cancel()
            assertEquals("$site recovery did not finish; $browserState", true, result)
            if (!manual) assertEquals("Silent verification opened a dialog", false, neededInteraction)
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
