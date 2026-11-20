package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*
import org.mobilism.material.settings.AppearanceStore
import org.mobilism.material.settings.LandingPage
import org.mobilism.material.settings.LandingPageStore
import org.mobilism.material.ui.MobilismViewModel

@RunWith(AndroidJUnit4::class)
class FeedTrafficTest {
    private class Fixture {
        private val base = InstrumentationRegistry.getInstrumentation().targetContext
        private val prefix = "feed-traffic-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        val repository = DefaultMobilismRepository(context, client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("""{"response":[]}""".toResponseBody()).build()
        }.build())

        suspend fun model(source: MobilismRepository = repository): MobilismViewModel {
            repository.continueAsGuest()
            return withContext(Dispatchers.Main) {
                MobilismViewModel(context, source, AppearanceStore(context), DebridRepository(context), AppLibraryStore(context))
            }
        }
    }

    @Test fun onlyTheVisibleFeedLoadsAndBrowsingDoesNotChangeTheSavedLandingPage() = runBlocking {
        val fixture = Fixture()
        val landing = LandingPage(ContentSection.Books, FeedOrder.Top)
        LandingPageStore(fixture.context).select(landing)
        val model = fixture.model()
        try {
            withContext(Dispatchers.Main) { model.loadFeed(refresh = true) }
            assertTrue(fixture.requests.isEmpty())
            assertEquals(ContentSection.Books, model.feed.value.section)
            assertEquals(FeedOrder.Top, model.feed.value.order)
            withContext(Dispatchers.Main) { model.setFeedVisible(true) }
            withTimeout(10_000) { model.feed.first { it.page == 1 && !it.busy } }
            assertEquals(1, fixture.requests.size)
            assertTrue(fixture.requests.single().url.encodedPath.contains("release/${ContentSection.Books.apiPath}/"))
            withContext(Dispatchers.Main) { model.setFeedVisible(false); model.setFeedVisible(true) }
            assertEquals(1, fixture.requests.size)
            withContext(Dispatchers.Main) { model.loadFeed(refresh = true) }
            withTimeout(10_000) { model.feed.first { !it.busy } }
            assertEquals(2, fixture.requests.size)
            withContext(Dispatchers.Main) { model.setFeedVisible(false); model.selectSection(ContentSection.Games) }
            assertEquals(2, fixture.requests.size)
            withContext(Dispatchers.Main) { model.setFeedVisible(true) }
            withTimeout(10_000) { model.feed.first { it.page == 1 && !it.busy } }
            assertEquals(3, fixture.requests.size)
            assertEquals(landing, LandingPageStore(fixture.context).state.value)
        } finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel() } }
    }

    @Test fun leavingTheFeedCancelsItsRequestAndTheNextSectionCanStillLoad() = runBlocking {
        val fixture = Fixture()
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val calls = mutableListOf<ContentSection>()
        val repository = object : MobilismRepository by fixture.repository {
            override suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, filters: FeedFilters, fresh: Boolean): ReleasePage {
                calls += section
                if (section == ContentSection.Apps) {
                    started.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                return ReleasePage(emptyList(), false)
            }
        }
        val model = fixture.model(repository)
        try {
            withContext(Dispatchers.Main) { model.setFeedVisible(true) }
            withTimeout(10_000) { started.await() }
            withContext(Dispatchers.Main) { model.setFeedVisible(false) }
            withTimeout(10_000) { cancelled.await() }
            assertFalse(model.feed.value.busy)
            assertNull(model.feed.value.error)
            withContext(Dispatchers.Main) { model.selectSection(ContentSection.Books); model.setFeedVisible(true) }
            withTimeout(10_000) { model.feed.first { it.page == 1 && !it.busy } }
            assertEquals(listOf(ContentSection.Apps, ContentSection.Books), calls)
            assertEquals(ContentSection.Books, model.feed.value.section)
        } finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel() } }
    }
}
