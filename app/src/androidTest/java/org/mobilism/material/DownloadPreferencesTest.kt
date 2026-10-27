package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.designsystem.SolarizedDark
import org.mobilism.material.settings.DownloadPreferencesStore
import org.mobilism.material.ui.DownloadSettingsScreen

@RunWith(AndroidJUnit4::class)
class DownloadPreferencesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun isolatedContext() = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("download-test-$name", mode)
    }.also { it.getSharedPreferences("material-downloads", 0).edit().clear().commit() }

    @Test fun encounteredHostsSurviveCatalogRefreshAndPreferenceReload() {
        val context = isolatedContext()
        val store = DownloadPreferencesStore(context)
        store.catalog(listOf(SupportedHost("send.now", "Send.now")))
        store.discover(listOf("https://github.com/owner/app/releases/download/v1/app.apk",
            "https://www.github.com/owner/app/releases/download/v2/app.apk", "https://zipyshare.com/file/123",
            "https://forum.mobilism.org/viewtopic.php?t=123"))
        store.hosts(setOf("github.com"))
        store.catalog(listOf(SupportedHost("send.now", "Send.now"), SupportedHost("rapidgator.net", "Rapidgator")))
        val restored = DownloadPreferencesStore(context).state.value
        assertEquals(listOf("GitHub", "Rapidgator", "Send.now", "zipyshare.com"), restored.hosts.map { it.name })
        assertEquals(setOf("github.com"), restored.preferredHosts)
        assertEquals(1, restored.hosts.count { it.domain == "github.com" })
    }

    @Test fun discoveredHostCanBeSelectedAfterSettingsRefresh() {
        val store = DownloadPreferencesStore(isolatedContext())
        store.discover(listOf("https://github.com/owner/app/releases/download/v1/app.apk"))
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("""<supported-filehosts><host url="send.now"/><host url="rapidgator.net"/></supported-filehosts>""".toResponseBody()).build()
        }.build()
        val repository = DebridRepository(compose.activity, client)
        compose.setContent { MobilismTheme(theme = SolarizedDark, reduceMotion = true) {
            val state by store.state.collectAsState()
            DownloadSettingsScreen(state, store, repository, {})
        } }
        compose.waitUntil(10_000) { store.state.value.hosts.any { it.domain == "send.now" } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Preferred file hosts"))
        compose.onNodeWithText("GitHub").performScrollTo().performClick()
        compose.onNodeWithText("GitHub").assertIsOn()
        assertEquals(setOf("github.com"), store.state.value.preferredHosts)
        val directory = File(compose.activity.getExternalFilesDir(null), "download-polish")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "host-preferences.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("GitHub").performClick()
        compose.onNodeWithText("GitHub").assertIsOff()
        assertTrue(store.state.value.preferredHosts.isEmpty())
    }
}
