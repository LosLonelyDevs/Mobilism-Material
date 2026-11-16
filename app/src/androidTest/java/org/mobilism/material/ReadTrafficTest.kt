package org.mobilism.material

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.data.*

@RunWith(AndroidJUnit4::class)
class ReadTrafficTest {
    private class Fixture {
        private val base = InstrumentationRegistry.getInstrumentation().targetContext
        private val prefix = "read-traffic-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        var status = 200
        var retryAfter: String? = null
        var onRead: () -> Unit = {}
        var login = 0
        val repository = DefaultMobilismRepository(context, client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val signingIn = request.url.encodedPath.endsWith("auth/login")
            if (!signingIn) onRead()
            val body = when {
                signingIn -> """{"access_token":"fixture-${++login}","id":1}"""
                status != 200 -> """{"error":{"text":"Busy"}}"""
                request.url.encodedPath.endsWith("discussion/categories") -> """{"response":{"app|game":[],"books":[]}}"""
                else -> """{"response":[]}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).message("Fixture")
                .code(if (signingIn) 200 else status).apply { retryAfter?.let { header("Retry-After", it) } }
                .body(body.toResponseBody()).build()
        }.build())
    }

    @Test fun activityAndTopicListShareTheFirstPageButManualRefreshStillFetches() = runBlocking {
        val fixture = Fixture()
        val category = DiscussionCategory(42, "Apps", DiscussionArea.Apps)
        fixture.repository.discussionLastActivity(category)
        fixture.repository.discussionTopics(category, 1)
        fixture.repository.discussionLastActivity(category)
        assertEquals(1, fixture.requests.size)
        fixture.repository.refreshDiscussionTopics(category)
        assertEquals(2, fixture.requests.size)
        fixture.repository.discussionTopics(category, 2)
        assertEquals(3, fixture.requests.size)
    }

    @Test fun categoryCatalogIsSharedAcrossScreensAndManualRefreshBypassesCache() = runBlocking {
        val fixture = Fixture()
        fixture.repository.discussionCategories(DiscussionArea.Apps)
        fixture.repository.discussionCategories(DiscussionArea.Games)
        fixture.repository.homeReviews(ContentSection.Apps)
        assertEquals(1, fixture.requests.size)
        fixture.repository.refreshDiscussionCategories(DiscussionArea.Apps)
        assertEquals(2, fixture.requests.size)
    }

    @Test fun cachedTopicPagesNeverCrossAccountBoundaries() = runBlocking {
        val fixture = Fixture()
        val category = DiscussionCategory(42, "Apps", DiscussionArea.Apps)
        fixture.repository.discussionTopics(category, 1)
        fixture.repository.signIn("First", "fixture")
        fixture.repository.discussionTopics(category, 1)
        fixture.repository.signIn("Second", "fixture")
        fixture.repository.discussionTopics(category, 1)
        val reads = fixture.requests.filter { it.method == "GET" }
        assertEquals(listOf(null, "fixture-1", "fixture-2"), reads.map { it.header("X-Auth-Token") })
    }

    @Test fun overlappingMailboxReadsShareOneRequestAndLaterReadsRemainFresh() = runBlocking {
        val fixture = Fixture()
        fixture.repository.signIn("Reader", "fixture")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fixture.onRead = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
        val first = async { fixture.repository.messages(MessageFolder.Inbox, 1) }
        delay(100)
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        val second = async { fixture.repository.messages(MessageFolder.Inbox, 1) }
        delay(100)
        assertEquals(2, fixture.requests.size)
        release.countDown()
        first.await(); second.await()
        fixture.onRead = {}
        fixture.repository.messages(MessageFolder.Inbox, 1)
        assertEquals(3, fixture.requests.size)
    }

    @Test fun retryAfterStopsOtherReadsAndSearchWithoutReplayingServerFailures() = runBlocking {
        for (status in listOf(429, 503)) {
            val fixture = Fixture().apply { this.status = status; retryAfter = "120" }
            assertTrue(runCatching { fixture.repository.comments(42, 1) }.isFailure)
            assertTrue(runCatching { fixture.repository.comments(43, 1) }.isFailure)
            assertTrue(runCatching { fixture.repository.search("fixture", SearchScope.All, 1) }.isFailure)
            assertEquals(1, fixture.requests.size)
        }
    }

    @Test fun forumCooldownDoesNotBlockTheSeparateApiOrigin() = runBlocking {
        val fixture = Fixture().apply { status = 429; retryAfter = "120" }
        assertTrue(runCatching { fixture.repository.profile(ProfileReference(username = "Reader")) }.isFailure)
        assertTrue(runCatching { fixture.repository.downloadHelp() }.isFailure)
        fixture.status = 200
        fixture.repository.comments(42, 1)
        assertEquals(2, fixture.requests.size)
    }
}
