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
    private class Fixture(private val code: Int = 200, private val searchBody: String = """{"releases":[]}""") {
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
                    .body((if (login) """{"access_token":"fixture-account"}""" else searchBody).toResponseBody()).build()
            }.build())
        val library = AppLibraryStore(context)
        val cacheEvents = mutableListOf<DiagnosticEvent>()
        fun checker(limits: ApiRequestLimits = ApiRequestLimits.Defaults, latest: suspend () -> List<Release> = { emptyList() },
            publish: suspend (LatestAppsPage) -> Unit = {}) =
            FollowingChecker(library, repository, InstalledApps(context), FollowingPreferences(context), FollowingNotifications(context),
                limits = { limits }, latestApps = { _, _ -> LatestAppsSnapshot(CacheOutcome.Available, latest()) },
                diagnostics = DiagnosticLogger { cacheEvents.add(it) }, publishMatches = publish, searchError = { "Fixture failure" })
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

    @Test fun anExistingFallbackFillsTheNextDevicesCacheWithoutAnotherSearch() = runBlocking {
        val fixture = Fixture(searchBody = """{"releases":[
            {"id":61,"subject":"Fixture A v2.0","date":1700000061,"name":"Example"},
            {"id":62,"subject":"Fixture A Reader v3.0","date":1700000062,"name":"Example"},
            {"id":63,"subject":"Fixture B v1.5","date":1700000063,"name":"Example"}]}""")
        val first = Release(1, "Fixture A v1.0", "", null, 1, 0, 0, section = ContentSection.Apps)
        val second = first.copy(id = 2, title = "Fixture B v1.0")
        val batch = mutableListOf<LatestAppsPage>()
        listOf(first, second).forEach { fixture.library.watch(it, true) }
        val result = fixture.checker(publish = { batch.add(it) }).check()
        assertEquals(2, result.done)
        assertEquals(2, fixture.calls)
        val contribution = batch.single()
        assertFalse(contribution.firstPage)
        assertFalse(contribution.adultFiltered)
        assertEquals(setOf(61L, 63L), contribution.releases.map { it.id }.toSet())
        val next = Fixture()
        next.library.watch(first, true)
        assertEquals(1, next.checker(latest = { contribution.releases }).check().done)
        assertEquals(0, next.calls)
        assertEquals(61L, next.library.state.value.single().update?.id)

        val cached = FollowSearchObservation()
        val page = kotlinx.coroutines.withContext(cached) { fixture.repository.search("Fixture A", SearchScope.Apps, 1) }
        assertNull(cached.contribution(first, page))
        assertEquals(2, fixture.calls)
    }

    @Test fun manualChecksUseOneWholeListLookupAndOnlyFiveSearchesOnCacheFailure() = runBlocking {
        val fixture = Fixture()
        fixture.followApps()
        val requests = mutableListOf<List<String>>()
        val checker = FollowingChecker(fixture.library, fixture.repository, InstalledApps(fixture.context),
            FollowingPreferences(fixture.context), FollowingNotifications(fixture.context),
            limits = { ApiRequestLimits.Defaults.copy(followCacheBatchSize = 2) }, latestApps = { names, background ->
                assertFalse(background)
                requests.add(names)
                throw java.io.IOException("Fixture outage")
            }, searchError = { "Fixture failure" })
        assertEquals(5, checker.check().done)
        checker.check()
        assertEquals(12, requests.single().size)
        assertEquals(5, fixture.calls)
    }

    @Test fun rotatingBackgroundBatchAndIndependentCacheClocksSurviveProcessRecreation() {
        val fixture = Fixture()
        val prefs = FollowingPreferences(fixture.context)
        val names = listOf("App A", "App B", "App C")
        val limits = ApiRequestLimits.Defaults
        assertEquals(names.take(2), prefs.backgroundBatch(names, 2))
        val restored = FollowingPreferences(fixture.context)
        assertEquals(listOf("App C", "App A"), restored.backgroundBatch(names, 2))
        assertTrue(prefs.beginCacheCheck(3000000, limits, background = true))
        assertTrue(restored.beginCacheCheck(3001000, limits, background = false))
        assertFalse(restored.beginCacheCheck(3301000, limits, background = true))
        assertTrue(restored.beginCacheCheck(3301000, limits, background = false))
        assertTrue(restored.beginCheck(3301000, limits))
        assertFalse(prefs.beginCheck(3302000, limits))
    }

    @Test fun oneSnapshotCanUpdateMoreThanFiveFollowedAppsWithoutMobilismReads() = runBlocking {
        val fixture = Fixture()
        fixture.followApps()
        val releases = fixture.library.state.value.map { entry ->
            entry.release.copy(id = entry.release.id + 100, title = entry.release.title.replace("v1.0", "v2.0"))
        }
        var reads = 0
        val result = fixture.checker(latest = { reads++; releases }).check()
        assertEquals(1, reads)
        assertEquals(CacheDiagnostic(AppCache.Following, CacheOutcome.Available, 12, 0, 12, false, 0),
            fixture.cacheEvents.single().cache)
        assertEquals(12, result.done)
        assertEquals(0, fixture.calls)
        assertTrue(fixture.library.state.value.all { it.update?.title?.endsWith("v2.0") == true })
    }

    @Test fun onlyReadingTheAppsLatestFeedContributesItsExistingPage() = runBlocking {
        val context = Fixture().context
        val pages = mutableListOf<LatestAppsPage>()
        var calls = 0
        val repository = DefaultMobilismRepository(context, onLatestApps = { pages.add(it) },
            client = OkHttpClient.Builder().addInterceptor { chain ->
                calls++
                val body = """{"response":[{"id":77,"name":"Uploader","subject":"Fixture v2.1","date":1700000000}]}"""
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(body.toResponseBody()).build()
            }.build())
        repository.releases(ContentSection.Apps, FeedOrder.Latest, 1)
        repository.releases(ContentSection.Apps, FeedOrder.Top, 1)
        repository.releases(ContentSection.Games, FeedOrder.Latest, 1)
        assertEquals(3, calls)
        assertTrue(pages.single().firstPage)
        assertEquals("Uploader", pages.single().releases.single().author)
        assertEquals(77L, pages.single().releases.single().id)
    }

    @Test fun cacheExceptionStillCompletesTheNormalSearchBatchAndKeepsItsCooldown() = runBlocking {
        val fixture = Fixture()
        fixture.followApps()
        val checker = fixture.checker(latest = { throw java.io.IOException("Vercel unavailable") })
        assertEquals(5, checker.check(background = true).done)
        assertEquals(CacheDiagnostic(AppCache.Following, CacheOutcome.Failed, 0, 0, 0, true, 5),
            fixture.cacheEvents.single().cache)
        assertEquals(5, fixture.calls)
        val prefs = fixture.context.getSharedPreferences("following-settings", Context.MODE_PRIVATE)
        prefs.edit().putLong("last-check-attempt", System.currentTimeMillis() - 20 * 60_000).commit()
        assertEquals(0, checker.check(background = true).done)
        assertEquals(0, fixture.cacheEvents.last().cache!!.fallbackSearches)
        assertEquals(5, fixture.calls)
        assertEquals(5, checker.check().done)
        assertEquals(10, fixture.calls)
    }

    @Test fun aCompatibleMatchUsesTheCacheAndOnlyMissesUseTheDirectBatch() = runBlocking {
        val fixture = Fixture()
        fixture.followApps()
        val first = fixture.library.state.value.first().release
        val newer = first.copy(id = 100, title = first.title.replace("v1.0", "v2.0"))
        val result = fixture.checker(latest = { listOf(newer) }).check()
        assertEquals(6, result.done)
        assertEquals(CacheDiagnostic(AppCache.Following, CacheOutcome.Available, 1, 11, 1, false, 5),
            fixture.cacheEvents.single().cache)
        assertEquals(5, fixture.calls)
        assertEquals(100L, fixture.library.state.value.single { it.release.id == first.id }.update?.id)
    }

    @Test fun followingRemoteBatchAndCooldownSurviveCheckerRecreationAndDefaultFallback() = runBlocking {
        val fixture = Fixture()
        fixture.followApps()
        val limits = ApiRequestLimits.Defaults.copy(followBatchSize = 2, followAttemptIntervalMs = 3_600_000)
        assertEquals(2, fixture.checker(limits).check().done)
        assertEquals(2, fixture.calls)
        assertEquals(12, fixture.library.state.value.count { it.watched })
        val preferences = fixture.context.getSharedPreferences("following-settings", Context.MODE_PRIVATE)
        preferences.edit().putLong("last-check-attempt", System.currentTimeMillis() - 30 * 60_000).commit()
        fixture.checker(limits).check()
        assertEquals(2, fixture.calls)
        assertEquals(5, fixture.checker().check().done)
        assertEquals(7, fixture.calls)
    }
}
