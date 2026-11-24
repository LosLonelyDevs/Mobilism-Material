package org.mobilism.material.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONException
import org.json.JSONObject

internal class MobilismApi(private val context: Context, private val diagnostics: DiagnosticLogger = DiagnosticLogger.None,
    client: OkHttpClient = AppHttp.client, private val challengeRecovery: ChallengeRecovery = ChallengeRecovery.None,
    private val requestLimits: () -> ApiRequestLimits = { ApiRequestLimits.Defaults },
    requestTraffic: RequestTrafficSink = RequestTrafficSink.None,
    private val onLatestApps: (LatestAppsPage) -> Unit = {}) {
    private val client = AppHttp.builder(client)
        .apply {
            interceptors().add(0, DiagnosticInterceptor(diagnostics))
            interceptors().add(1, BrowserCookiesInterceptor())
        }
        .addNetworkInterceptor(RequestTrafficInterceptor(requestTraffic))
        .addNetworkInterceptor { chain ->
            val once = chain.request().tag(MessageDeletion::class.java)
            if (once != null && !once.started.compareAndSet(false, true)) throw IOException("Message action already attempted")
            chain.proceed(chain.request())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
    // Connection recovery can resend a request. Keep writes on the non-retrying client.
    private val readClient = this.client.newBuilder().retryOnConnectionFailure(true).build()
    private val feedPages = FeedPageStore(context.getSharedPreferences("release-feed-pages", Context.MODE_PRIVATE))
    private val feedLocks = Array(8) { kotlinx.coroutines.sync.Mutex() }
    private val latestWindow = LatestFeedWindow(context.getSharedPreferences("latest-apps-window", Context.MODE_PRIVATE))
    private val searches = SearchRequestPolicy(limits = requestLimits, diagnostics = diagnostics,
        pausedMessage = { context.getString(R.string.search_paused) })
    private val reads = ReadRequestPolicy(limits = requestLimits, diagnostics = diagnostics,
        busyMessage = { context.getString(R.string.read_paused) })
    private val forumReads = ReadRequestPolicy(limits = requestLimits, busyMessage = { context.getString(R.string.read_paused) })
    fun commentGateway(account: () -> Session?, changed: (Long) -> Unit): ForumCommentGateway =
        NativeForumComments(context, this.client, forumReads, account, changed)
    private val likePostIds = context.getSharedPreferences("release-post-ids", Context.MODE_PRIVATE)
    private val forumPostIds = ConcurrentHashMap<Long, Long>()
    private val forumCommentStarts = ConcurrentHashMap<Pair<Long, Int>, Int>()

    suspend fun signIn(username: String, password: String): Session {
        val result = request("auth/login", form = mapOf("username" to username, "password" to password))
        val token = result.optString("access_token")
        if (token.isBlank() || token == "null") throw ApiFailure(
            FailureKind.InvalidResponse, context.getString(R.string.login_response_missing),
        )
        return Session(username, token, result.optString("refresh_token").takeUnless { it.isBlank() || it == "null" },
            result.optLong("id").takeIf { it > 0 })
    }

    suspend fun categories(token: String?, hideAdult: Boolean = false): Map<ContentSection, List<ReleaseCategory>> {
        val response = request("release/category", token, hideAdult = hideAdult)
        return ContentSection.entries.associateWith { CategoryJson.parse(response, it) }
    }

    fun feedPolicy(order: FeedOrder): ReleaseFeedPolicy {
        val limits = requestLimits()
        val latest = order == FeedOrder.Latest
        return ReleaseFeedPolicy(if (latest) limits.releaseLatestCacheTtlMs else limits.releaseTopCacheTtlMs,
            feedPages.generation(order, limits.cacheResets[order.name] ?: 0), latest || limits.customTopLifetime,
            if (latest) limits.customLatestLifetime else limits.customTopLifetime)
    }

    suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, token: String?, categoryId: Long? = null,
        hideAdult: Boolean = false): ReleasePage {
        require(page > 0)
        require(categoryId == null || categoryId > 0)
        val category = categoryId?.toString() ?: "all"
        val path = "release/${section.apiPath}/category/$category/page/$page/limit/$PAGE_SIZE/orderby/${order.apiValue}/desc/0"
        fun parse(array: org.json.JSONArray): ReleasePage = ReleaseJson.page(array).let { result ->
            result.copy(items = result.items.map { it.copy(section = it.section ?: section) })
        }
        suspend fun fetch(key: String?): ReleasePage {
            val readAt = System.currentTimeMillis() / 1000
            val array = request(path, token, emptyRecordsAsArray = true, hideAdult = hideAdult).getJSONArray("response")
            val result = parse(array)
            if (key != null) feedPages.save(key, array.toString())
            if (section == ContentSection.Apps && order == FeedOrder.Latest) {
                val first = page == 1 && categoryId == null
                val baseline = if (first) runCatching {
                    latestWindow.baseline(requestKey("latest:$token:$hideAdult"), result.items, readAt)
                }.getOrNull() else null
                onLatestApps(LatestAppsPage(result.items, first, hideAdult, readAt, baseline))
            }
            return result
        }
        val firstPage = kotlin.coroutines.coroutineContext[FirstFeedPage] ?: return fetch(null)
        val policy = feedPolicy(order)
        val oldKey = requestKey(listOf(path, token, hideAdult).toString())
        val key = if (policy.generation == 0) oldKey else "$oldKey:${policy.generation}"
        val ttl = policy.ttlMs
        return withContext(Dispatchers.IO) {
            feedLocks[Math.floorMod(key.hashCode(), feedLocks.size)].withLock {
                if (!firstPage.fresh) {
                    val cached = feedPages.read(key, ttl)?.let { saved ->
                        runCatching { parse(org.json.JSONArray(saved.json)) }.getOrNull()?.also { firstPage.include(saved.savedAt) }
                    }
                    diagnostics.cacheLookup(AppCache.ReleaseFeed, if (cached != null) CacheOutcome.DiskHit else CacheOutcome.Miss)
                    if (cached != null) return@withLock cached
                }
                fetch(key)
            }
        }
    }

    suspend fun register(username: String, email: String, password: String) {
        request("auth/register", form = mapOf(
            "username" to username.trim(), "email" to email.trim(),
            "password" to password, "repeatPassword" to password,
        ))
    }

    suspend fun restorePassword(email: String) {
        request("auth/restore", form = mapOf("email" to email.trim()))
    }

    suspend fun openTopic(id: Long, token: String?, directForum: Long? = null): TopicContent {
        require(id > 0)
        if (directForum == null) {
            try {
                val release = ReleaseJson.parse(request("release/$id", token).getJSONObject("response"))
                forumPostIds.remove(id)
                return TopicContent.Catalog(release)
            } catch (failure: ApiFailure) {
                if (failure.httpStatus != 400 || failure.message != "No records found.") throw failure
            }
        }
        val content = ForumReleaseParser.linkedTopic(forumTopic(id, forumId = directForum?.takeIf { it > 0 }), id)
        if (content is TopicContent.Catalog) forumPostIds[id] = requireNotNull(content.release.likeId)
        return content
    }

    suspend fun linkedComment(postId: Long, topicId: Long?): LinkedCommentPage {
        require(postId > 0 && (topicId == null || topicId > 0))
        val url = "https://forum.mobilism.org/viewtopic.php".toHttpUrl().newBuilder()
            .apply { topicId?.let { addQueryParameter("t", it.toString()) } }
            .addQueryParameter("p", postId.toString()).build()
        val html = forumReads.request(url.toString()) { readForumPage(url, client = client) }
        return ForumReleaseParser.linkedComment(html, postId, topicId)
    }

    suspend fun toggleLike(release: Release, token: String): Boolean {
        require(release.id > 0)
        // Post IDs are public and stable. Reuse a verified ID when the forum blocks later reads.
        val postId = release.likeId?.takeIf { it > 0 }
            ?: likePostIds.getLong(release.id.toString(), 0).takeIf { it > 0 }
            ?: ReleaseLikes.postId(forumTopic(release.id))
            ?: throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.original_post_missing))
        likePostIds.edit().putLong(release.id.toString(), postId).apply()
        val response = request("post/like", token, mapOf("id" to postId.toString())).getJSONObject("response")
        return ReleaseLikes.liked(response)
            ?: throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.like_unconfirmed))
    }

    // This is a forum guide, not a release in the app catalog.
    suspend fun downloadHelp(): DiscussionTopic = DownloadHelp.parse(directForumPage(DownloadHelp.URL.toHttpUrl()))

    suspend fun comments(id: Long, page: Int, token: String?): CommentPage {
        require(id > 0 && page > 0)
        forumPostIds[id]?.let { originalPostId ->
            val start = if (page == 1) 0 else forumCommentStarts[id to page] ?: return CommentPage(emptyList(), false)
            val result = ForumReleaseParser.comments(forumTopic(id, start), id, originalPostId, start)
            if (page == 1) forumCommentStarts.keys.removeAll { it.first == id }
            result.nextStart?.let { forumCommentStarts[id to page + 1] = it }
            return result.page
        }
        return CommentJson.page(request("release/$id/comments/page/$page/limit/$PAGE_SIZE/orderby/date/desc", token,
            emptyRecordsAsArray = true).getJSONArray("response"))
    }

    suspend fun postComment(id: Long, text: String, token: String, replyTo: Long? = null): Long {
        require(id > 0 && token.isNotBlank())
        require(text.isNotBlank() && text.length <= ReleaseComments.MAX_LENGTH)
        require(replyTo == null || replyTo > 0)
        return ReleaseComments.confirmedId(request("release/comment", token,
            form = buildMap {
                put("id", id.toString()); put("comment", text.trim())
                replyTo?.let { put("msg_id", it.toString()) }
            }))
    }

    suspend fun discussionCategories(area: DiscussionArea, token: String?, fresh: Boolean = false): List<DiscussionCategory> =
        requestPage(if (area == DiscussionArea.Community) "other/category" else "discussion/categories", token,
            cacheMillis = requestLimits().discussionCategoriesCacheTtlMs, fresh = fresh, categoryGroup = area.categoryKey) {
            DiscussionJson.categories(it, area)
        }

    suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String, token: String?, includePinned: Boolean = true,
        fresh: Boolean = false): DiscussionPage {
        require(page > 0)
        if (query.isNotBlank()) return requestPage("search", token, form = mapOf(
            "search" to query.trim(), "count" to page.toString(), "limits" to PAGE_SIZE.toString(),
            "isrelease" to "0", "isdiscussion" to if (category.kind == DiscussionKind.Discussion) "1" else "0",
            "isother" to if (category.kind == DiscussionKind.Other) "1" else "0",
            "id" to if (category.id == 12L) "all" else category.id.toString(),
        )) { DiscussionJson.page(it, category.kind, search = true) }
        return requestPage("${category.kind.path}/category/${category.apiId}/page/$page/limit/$PAGE_SIZE/orderby/topic_last_post_time/desc/${if (includePinned) 1 else 0}",
            token, emptyRecordsAsArray = true,
            cacheMillis = if (page == 1) requestLimits().discussionFirstPageCacheTtlMs else 0, fresh = fresh) { DiscussionJson.page(it, category.kind) }
    }

    suspend fun discussionComments(topic: DiscussionTopic, page: Int, token: String?): CommentPage {
        require(topic.id > 0 && page > 0)
        return CommentJson.page(request("${topic.kind.path}/${topic.id}/comments/page/$page/limit/$PAGE_SIZE/orderby/date/desc",
            token, emptyRecordsAsArray = true).getJSONArray("response"))
    }

    suspend fun announcement(id: Long, forumId: Long, token: String?): LinkedAnnouncement {
        require(id > 0 && forumId > 0)
        val category = DiscussionCategory(forumId, "Announcements", DiscussionArea.Community)
        val listed = discussionTopics(category, 1, "", token).items.firstOrNull { it.id == id }
        if (listed != null) return LinkedAnnouncement(listed)
        // Older announcements can fall off the first page. Read the linked topic directly.
        return ForumReleaseParser.announcement(forumTopic(id, forumId = forumId), id)
    }

    suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long?, token: String): Long {
        require(topic.id > 0 && token.isNotBlank() && text.isNotBlank() && text.length <= ReleaseComments.MAX_LENGTH)
        require(replyTo == null || replyTo > 0)
        return ReleaseComments.confirmedId(request("${topic.kind.path}/comment", token, form = buildMap {
            put("id", topic.id.toString()); put("comment", text.trim())
            replyTo?.let { put("msg_id", it.toString()) }
        }))
    }

    suspend fun messages(folder: MessageFolder, userId: Long, page: Int, token: String): MessagePage {
        require(userId > 0 && page > 0)
        val items = PrivateMessages.parse(request(
            "message/${folder.apiPath}/$userId/page/$page/limit/$PAGE_SIZE/orderby/times/desc", token,
            emptyRecordsAsArray = true))
        return MessagePage(items, items.isNotEmpty())
    }

    suspend fun conversation(id: Long, token: String): List<PrivateMessage> {
        require(id > 0)
        return PrivateMessages.parse(request("message/$id/orderby/times/asc", token))
    }

    suspend fun markMessageRead(id: Long, token: String) {
        require(id > 0)
        PrivateMessages.confirm(request("message/$id/read", token, retryRead = false, emptySuccess = true))
    }

    suspend fun replyToMessage(message: PrivateMessage, text: String, account: Session) {
        val recipient = message.recipient(account.username)
        require(message.messageId > 0 && recipient.isNotBlank())
        require(text.isNotBlank() && text.length <= PrivateMessages.MAX_LENGTH)
        PrivateMessages.confirm(request("message/reply/send", account.accessToken, form = mapOf(
            "to" to recipient, "from" to account.username, "subject" to message.subject,
            "content" to text.trim(), "reply" to message.messageId.toString(),
        ), emptySuccess = true))
    }

    suspend fun sendMessage(recipient: String, subject: String, body: String, account: Session) {
        require(recipient.isNotBlank() && subject.isNotBlank() && body.isNotBlank() && body.length <= PrivateMessages.MAX_LENGTH)
        reads.checkAllowed()
        PrivateMessages.confirm(request("message/send", account.accessToken, form = mapOf(
            "to" to recipient.trim(), "from" to account.username, "subject" to subject.trim(), "content" to body.trim(),
        ), retryRead = false, emptySuccess = true))
    }

    private class MessageDeletion { val started = java.util.concurrent.atomic.AtomicBoolean() }

    suspend fun deleteMessage(messageId: Long, token: String) {
        require(messageId > 0)
        reads.checkAllowed()
        PrivateMessages.confirm(request("message/delete/$messageId", token, retryRead = false, emptySuccess = true))
    }

    suspend fun search(query: String, scope: SearchScope, page: Int, token: String?, hideAdult: Boolean = false): ReleasePage {
        require(query.isNotBlank() && page > 0)
        return requestPage("search", token, form = mapOf(
            "search" to query.trim(), "count" to page.toString(), "limits" to PAGE_SIZE.toString(),
            "isrelease" to "1", "isdiscussion" to "0", "isother" to "0", "id" to scope.apiId,
        ), hideAdult = hideAdult, parse = SearchJson::page).let { result ->
            result.copy(items = result.items.map { it.copy(section = it.section ?: scope.section) })
        }
    }

    suspend fun profile(reference: ProfileReference): UserProfile =
        ForumProfiles.parse(directForumPage(reference.url.toHttpUrl()), reference)

    // These screens can display the requested page directly when the forum requires verification.
    private suspend fun directForumPage(url: okhttp3.HttpUrl): String =
        forumReads.request(url.toString()) { readForumPage(url) }

    private suspend fun forumTopic(id: Long, commentsStart: Int? = null, forumId: Long? = null): String {
        val url = "https://forum.mobilism.org/viewtopic.php".toHttpUrl().newBuilder()
            .apply { forumId?.let { addQueryParameter("f", it.toString()) } }
            .addQueryParameter("t", id.toString()).apply {
                if (commentsStart != null) {
                    addQueryParameter("sd", "d")
                    addQueryParameter("start", commentsStart.toString())
                }
            }.build()
        return forumPage(url)
    }

    private suspend fun forumPage(url: okhttp3.HttpUrl): String = forumReads.request(url.toString()) {
        readForumPage(url) { challenge ->
            if (!challengeRecovery.recover(VerificationSite.Forum)) throw challenge
            forumReads.checkAllowed()
            readForumPage(url)
        }
    }

    private suspend fun readForumPage(url: okhttp3.HttpUrl, client: OkHttpClient = readClient,
        onChallenge: suspend (ApiFailure) -> String = { throw it }): String = withContext(Dispatchers.IO) {
        var userAgentChanged = false
        try {
            // Forum reads never receive the API session token.
            val request = Request.Builder().url(url)
            client.newCall(request.build()).await().use { response ->
                userAgentChanged = AppUserAgent.response(context, response.code)
                val source = response.body?.source() ?: throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.forum_empty))
                source.request(MAX_RESPONSE_BYTES + 1)
                if (source.buffer.size > MAX_RESPONSE_BYTES) throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.forum_oversize))
                val html = source.readUtf8()
                val challenged = NetworkFailures.response(response.code, response.header("cf-mitigated") == "challenge", html,
                    VerificationSite.Forum)?.kind == FailureKind.Challenge
                forumReads.response(response.code, response.header("Retry-After"), challenged)
                if (!response.isSuccessful || challenged) {
                    NetworkFailures.response(response.code, response.header("cf-mitigated") == "challenge", html,
                        VerificationSite.Forum)?.let { throw it }
                    throw ApiFailure(FailureKind.Server, context.getString(R.string.forum_http_status, response.code), response.code)
                }
                html
            }
        } catch (failure: ApiFailure) {
            if (!userAgentChanged && failure.kind == FailureKind.Challenge) return@withContext onChallenge(failure)
            throw failure
        } catch (failure: IOException) {
            throw NetworkFailures.forScreen(failure)
        }
    }

    private fun requestKey(identity: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private suspend fun request(path: String, token: String? = null, form: Map<String, String>? = null,
        emptyRecordsAsArray: Boolean = false, hideAdult: Boolean = false, retryRead: Boolean = true,
        emptySuccess: Boolean = false): JSONObject {
        if (form != null || !retryRead)
            return performRequest(path, token, form, emptyRecordsAsArray, hideAdult, retryRead, emptySuccess)
        return JSONObject(reads.request(requestKey(listOf(path, token, hideAdult).toString())) {
            performRequest(path, token, emptyRecordsAsArray = emptyRecordsAsArray, hideAdult = hideAdult, emptySuccess = emptySuccess).toString()
        })
    }

    private suspend fun <T> requestPage(path: String, token: String?, form: Map<String, String>? = null,
        emptyRecordsAsArray: Boolean = false, hideAdult: Boolean = false, cacheMillis: Long = 0, fresh: Boolean = false,
        categoryGroup: String? = null, parse: (JSONObject) -> T): T {
        val key = requestKey(listOf(path, token, hideAdult, form?.toSortedMap()).toString())
        val decode = { body: String ->
            try { parse(JSONObject(body)) }
            catch (_: JSONException) { throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.api_unreadable)) }
            catch (_: IllegalArgumentException) { throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.api_unreadable)) }
        }
        if (form == null) return reads.request(key, cacheMillis, fresh, decode, pageKey = categoryGroup?.let { "$key:$it" } ?: key) {
            performRequest(path, token, emptyRecordsAsArray = emptyRecordsAsArray, hideAdult = hideAdult).toString()
        }
        var readAt = 0L
        var fullPage = false
        val page = searches.request(key, decode) {
            reads.checkAllowed()
            readAt = System.currentTimeMillis() / 1000
            val result = performRequest(path, token, form, hideAdult = hideAdult, retryRead = false)
            fullPage = (result.optJSONArray("releases")?.length() ?: 0) >= PAGE_SIZE
            result.toString()
        }
        if (readAt > 0) kotlin.coroutines.coroutineContext[FollowSearchObservation]?.observed(readAt, hideAdult, fullPage)
        return page
    }

    private suspend fun performRequest(path: String, token: String? = null, form: Map<String, String>? = null,
        emptyRecordsAsArray: Boolean = false, hideAdult: Boolean = false, retryRead: Boolean = true,
        emptySuccess: Boolean = false, recovered: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val operation = when {
                path.startsWith("auth/") -> DiagnosticOperation.Login
                path == "search" -> DiagnosticOperation.Search
                "comment" in path -> DiagnosticOperation.Comments
                path.startsWith("message/") -> DiagnosticOperation.Messages
                path.endsWith("category") -> DiagnosticOperation.Categories
                path.startsWith("post/") -> DiagnosticOperation.Reaction
                else -> DiagnosticOperation.Releases
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    failure = DiagnosticFailure.Permission))
                throw ApiFailure(FailureKind.Permission, context.getString(R.string.network_disabled))
            }
            val url = BASE_URL.resolve(path)!!.newBuilder().apply {
                if (hideAdult) addQueryParameter("no_adult", "1")
            }.build()
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            // This legacy GET changes the mailbox. Skip caches and prevent HTTP follow-up replays.
            if (path.startsWith("message/delete/")) builder.header("Cache-Control", "no-cache, no-store")
                .tag(MessageDeletion::class.java, MessageDeletion())
            // This client is used only for the API origin, never images or download URLs.
            token?.let { builder.header("X-Auth-Token", it) }
            form?.let { fields ->
                val body = FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()
                // Searches are expensive too. HTTP follow-ups must not replay any POST.
                builder.post(object : RequestBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
                    override fun isOneShot() = true
                })
            }
            var userAgentChanged = false
            try {
                val readOnly = retryRead && (form == null || path == "search")
                val request = builder.build()
                val transport = if (readOnly) readClient else client
                var result = transport.newCall(request).await()
                if (readOnly && result.code in setOf(502, 503, 504, 520, 521, 522, 523, 524) &&
                    result.header("cf-mitigated") != "challenge" && result.header("Retry-After") == null) {
                    diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Retrying,
                        httpStatus = result.code, failure = DiagnosticFailure.Server))
                    result.close()
                    delay(requestLimits().readRetryDelayMs)
                    result = transport.newCall(request).await()
                }
                result.use { response ->
                    userAgentChanged = AppUserAgent.response(context, response.code)
                    if (path == "search" && !response.isSuccessful) searches.retryAfter(response.header("Retry-After"))
                    val body = response.body ?: throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.api_empty))
                    val source = body.source()
                    source.request(MAX_RESPONSE_BYTES + 1)
                    if (source.buffer.size > MAX_RESPONSE_BYTES) throw ApiFailure(
                        FailureKind.InvalidResponse, context.getString(R.string.api_oversize),
                    )
                    val text = source.readUtf8()
                    val challenged = NetworkFailures.response(response.code, response.header("cf-mitigated") == "challenge", text)
                        ?.kind == FailureKind.Challenge
                    if (readOnly || path == "search" || path == "message/send" || path.startsWith("message/delete/"))
                        reads.response(response.code, response.header("Retry-After"), challenged)
                    NetworkFailures.response(response.code, response.header("cf-mitigated") == "challenge", text)?.let { throw it }
                    val json = try {
                        if (emptySuccess && response.isSuccessful && (text.isBlank() || text.trim() == "null")) JSONObject()
                        else JSONObject(text)
                    } catch (_: JSONException) {
                        throw ApiFailure(FailureKind.InvalidResponse, context.getString(R.string.api_unreadable))
                    }
                    // Comments use HTTP 400 for an empty page, including the end of pagination.
                    if (emptyRecordsAsArray && response.code == 400 && json.optJSONObject("error")?.optString("text") == "No records found.") {
                        return@withContext JSONObject().put("response", org.json.JSONArray())
                    }
                    if (!response.isSuccessful || json.optString("status") == "error") {
                        val kind = when {
                            form != null && path == "auth/login" && response.code in listOf(400, 401, 403) -> FailureKind.Credentials
                            response.code == 401 && token != null -> FailureKind.Session
                            else -> FailureKind.Server
                        }
                        val fallback = when (kind) {
                            FailureKind.Credentials -> context.getString(R.string.login_rejected)
                            FailureKind.Session -> context.getString(R.string.session_expired)
                            else -> if (response.code == 429) context.getString(R.string.rate_limited)
                                else context.getString(R.string.http_status, response.code)
                        }
                        val message = json.optJSONObject("error")?.optString("text")
                            ?.takeIf { it.isNotBlank() && !it.contains('<') }?.take(240) ?: fallback
                        throw ApiFailure(kind, message, response.code)
                    }
                    json
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ApiFailure) {
                if (!recovered && !userAgentChanged && retryRead && (form == null || path == "search") &&
                    failure.kind == FailureKind.Challenge && challengeRecovery.recover(VerificationSite.App)) {
                    return@withContext performRequest(path, token, form, emptyRecordsAsArray, hideAdult, retryRead, emptySuccess,
                        recovered = true)
                }
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    httpStatus = failure.httpStatus, failure = when (failure.kind) {
                        FailureKind.Challenge -> DiagnosticFailure.Challenge
                        FailureKind.Credentials -> DiagnosticFailure.Credentials
                        FailureKind.Session -> DiagnosticFailure.SessionExpired
                        FailureKind.InvalidResponse -> DiagnosticFailure.InvalidResponse
                        else -> DiagnosticFailure.Server
                    }))
                throw failure
            } catch (_: SecurityException) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    failure = DiagnosticFailure.Permission))
                throw ApiFailure(FailureKind.Permission, context.getString(R.string.network_blocked))
            } catch (failure: IOException) {
                diagnostics.record(DiagnosticEvent(DiagnosticSource.Forum, operation, DiagnosticPhase.Failed,
                    failure = NetworkFailures.category(failure)))
                throw NetworkFailures.forScreen(failure)
            }
        }

    private companion object {
        val BASE_URL = "https://app.mobilism.org/api-new/".toHttpUrl()
        const val PAGE_SIZE = 20
        const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    }
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}
