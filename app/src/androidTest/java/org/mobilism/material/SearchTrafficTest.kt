package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.following.*

@RunWith(AndroidJUnit4::class)
class SearchTrafficTest {
    private class Fixture(private val code: Int = 200) {
        private val base = InstrumentationRegistry.getInstrumentation().targetContext
        private val prefix = "search-traffic-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
        var calls = 0
        val repository = DefaultMobilismRepository(context, ContentPreferencesStore(context).also { it.hideAdult(false) },
            client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                assertTrue(checkNotNull(request.body).isOneShot())
                val login = request.url.encodedPath.endsWith("auth/login")
                if (!login) { assertTrue(request.url.encodedPath.endsWith("/search")); calls++ }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture")
                    .code(if (login) 200 else code)
                    .body((if (login) """{"access_token":"fixture-account"}""" else """{"releases":[]}""").toResponseBody()).build()
            }.build())
        val library = AppLibraryStore(context)
        fun checker() = FollowingChecker(library, repository, InstalledApps(context), FollowingPreferences(context), FollowingNotifications(context))
        fun followApps() {
            ('A'..'L').forEachIndexed { index, letter ->
                library.watch(Release(index + 1L, "Fixture $letter v1.0", "", null, 1, 0, 0, section = ContentSection.Apps), true)
            }
            assertEquals(12, library.state.value.count { it.watched })
        }
    }

    @Test fun duplicateQueriesUseCacheButPagesScopesAndAccountsStaySeparate() = runBlocking {
        val fixture = Fixture()
        repeat(10) { fixture.repository.search("  fixture  ", SearchScope.Apps, 1) }
        assertEquals(1, fixture.calls)
        fixture.repository.search("fixture", SearchScope.Books, 1)
        fixture.repository.search("fixture", SearchScope.Apps, 2)
        fixture.repository.signIn("Reader", "fixture-password")
        fixture.repository.search("fixture", SearchScope.Apps, 1)
        assertEquals(4, fixture.calls)
        fixture.repository.signOut()
        fixture.repository.search("fixture", SearchScope.Apps, 1)
        assertEquals(4, fixture.calls)
    }

    @Test fun followingChecksFiveAppsOnceAndPersistsItsBatchCooldown() = runBlocking {
        val fixture = Fixture()
        fixture.followApps()
        val result = fixture.checker().check()
        assertEquals(5, result.done)
        assertEquals(0, result.failed)
        assertEquals(5, fixture.calls)
        assertEquals(5, fixture.library.state.value.count { it.checkedAt != null })
        fixture.checker().check()
        assertEquals(5, fixture.calls)
    }

    @Test fun followingStopsTheWholeBatchOnItsFirstServerFailure() = runBlocking {
        val fixture = Fixture(503)
        fixture.followApps()
        val result = fixture.checker().check()
        assertEquals(1, result.failed)
        assertEquals(1, fixture.calls)
        assertTrue(fixture.library.state.value.all { it.checkedAt == null })
        fixture.checker().check()
        assertEquals(1, fixture.calls)
    }
}
