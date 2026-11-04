package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.DataInputStream
import java.net.Socket
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.SettingsScreen

class LivePremiumRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun realPremiumAccountKeepsWorkingWhenCatalogIsForbidden() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("livePremiumRecovery") == "true")
        assumeTrue(Build.HARDWARE in listOf("ranchu", "goldfish"))
        val port = checkNotNull(arguments.getString("localCredentialsPort")?.toIntOrNull())
        val credentials = Socket("127.0.0.1", port).use { socket ->
            socket.soTimeout = 10_000
            val input = DataInputStream(socket.getInputStream())
            val size = input.readInt()
            require(size in 1..16_384)
            val bytes = ByteArray(size)
            input.readFully(bytes)
            JSONObject(bytes.toString(Charsets.UTF_8))
        }
        val prefix = "live-premium-${System.nanoTime()}-"
        val preferences = CopyOnWriteArraySet<String>()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences(prefix + name, mode).also { preferences += prefix + name }
        }
        val blockCatalog = AtomicBoolean(false)
        val hostRequests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "forum.mobilism.org" && request.url.encodedPath == "/filehosts.xml") {
                hostRequests.incrementAndGet()
                if (blockCatalog.get()) return@addInterceptor Response.Builder().request(request)
                    .protocol(Protocol.HTTP_1_1).code(403).message("Forbidden").body("Forbidden".toResponseBody()).build()
            }
            chain.proceed(request)
        }.build()
        val repository = DebridRepository(context, client)
        val forum = DefaultMobilismRepository(context, client = client)
        var displayed by mutableStateOf(repository)
        try {
            repository.connectMobilism(credentials.getString("username"), credentials.getString("premiumPassword"))
            compose.setContent { MobilismTheme(reduceMotion = true) {
                val state by displayed.state.collectAsState()
                SettingsScreen(state, displayed, onAppearance = {}, onDownloads = {}, onBack = {})
            } }
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Premium"))
            compose.onNodeWithText("Premium").performClick()
            assertUsable(repository)
            blockCatalog.set(true)
            val before = hostRequests.get()
            compose.onNodeWithContentDescription("Verify account and refresh hosts").performClick()
            compose.waitUntil(60_000) { hostRequests.get() > before && !repository.state.value.accounts.single { it.provider == DebridProvider.Mobilism }.busy }
            assertUsable(repository)

            val restored = DebridRepository(context, client)
            restored.load()
            compose.runOnUiThread { displayed = restored }
            assertUsable(restored)
            forum.signIn(credentials.getString("username"), credentials.getString("password"))
            val source = forum.release(6438871).downloadLinks.first { HostMatcher.matches(it, "rapidgator.net") }
            val direct = restored.resolve(source)
            assertNotEquals(source, direct)
            client.newCall(Request.Builder().url(direct).header("Range", "bytes=0-3").build()).execute().use { response ->
                assertTrue("Resolved download must be readable", response.isSuccessful)
                assertArrayEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46), checkNotNull(response.body).source().readByteArray(4))
                val account = restored.state.value.accounts.single { it.provider == DebridProvider.Mobilism }
                println("Live Premium: catalog=403; hosts=${account.hostNames.size}; verified=${account.validated}; refresh=passed; restart=passed; downloadHTTP=${response.code}; PDF header=verified")
            }
        } finally {
            repository.removeKey(DebridProvider.Mobilism)
            forum.signOut()
            preferences.forEach(context::deleteSharedPreferences)
        }
    }

    private fun assertUsable(repository: DebridRepository) {
        val account = repository.state.value.accounts.single { it.provider == DebridProvider.Mobilism }
        assertTrue(account.configured)
        assertTrue(account.validated)
        assertTrue(account.hostsVerified)
        assertNull(account.error)
        assertTrue(account.hostNames.isNotEmpty())
        val label = "${account.hostNames.size} Mobilism hosts"
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithContentDescription("Account verified").assertExists()
        compose.onNodeWithText("0 Mobilism hosts").assertDoesNotExist()
        compose.onNodeWithText("Mobilism Premium denied access. Check your account or try again later.").assertDoesNotExist()
        assertEquals(listOf(DebridProvider.Mobilism), repository.state.value.supporting("https://rapidgator.net/file/123").map { it.provider })
    }
}
