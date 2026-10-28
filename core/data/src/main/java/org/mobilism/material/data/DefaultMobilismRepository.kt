package org.mobilism.material.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultMobilismRepository(context: Context, private val contentPreferences: ContentPreferencesStore = ContentPreferencesStore(context),
    diagnostics: DiagnosticLogger = DiagnosticLogger.None, client: okhttp3.OkHttpClient = okhttp3.OkHttpClient(),
    private val customCategories: CustomCategoryStore? = null,
    private val customCategoriesEnabled: () -> Boolean = { false },
) : MobilismRepository, MessagesRepository, DiscussionsRepository, HomeRepository {
    private val api = MobilismApi(context.applicationContext, diagnostics, client)
    private val store = SessionStore(context.applicationContext)
    private val reactions = context.getSharedPreferences("release-reactions", Context.MODE_PRIVATE)
    private val authMutex = Mutex()
    private val categoryMutex = Mutex()
    private val categoryCache = mutableMapOf<Boolean, Map<ContentSection, List<ReleaseCategory>>>()
    private val pagerMutex = Mutex()
    private data class Query(val section: ContentSection, val order: FeedOrder, val categories: Set<Long>, val hideAdult: Boolean,
        val customIds: Set<String> = emptySet(), val customItems: List<CategorizedItem> = emptyList())
    private var pagerQuery: Query? = null
    private var pager: CategoryFeedPager? = null
    private var pagerPage = 0
    private val pagerResults = mutableMapOf<Int, ReleasePage>()
    private val mutableAuth = MutableStateFlow<AuthState>(AuthState.Restoring)
    override val auth = mutableAuth.asStateFlow()
    @Volatile private var session: Session? = null

    override suspend fun restoreSession() = authMutex.withLock {
        try {
            session = store.read()
            mutableAuth.value = session?.let { AuthState.SignedIn(it.username) }
                ?: if (store.isGuest()) AuthState.Guest else AuthState.SignedOut
        } catch (error: Exception) {
            mutableAuth.value = AuthState.SignedOut
            throw error
        }
    }

    override suspend fun signIn(username: String, password: String) = authMutex.withLock {
        require(username.isNotBlank() && password.isNotEmpty())
        val result = api.signIn(username.trim(), password)
        store.write(result)
        session = result
        mutableAuth.value = AuthState.SignedIn(result.username)
    }

    override suspend fun continueAsGuest() = authMutex.withLock {
        store.clear(guest = true)
        session = null
        mutableAuth.value = AuthState.Guest
    }

    override suspend fun signOut() = authMutex.withLock {
        store.clear()
        session = null
        mutableAuth.value = AuthState.SignedOut
    }

    override suspend fun categories(section: ContentSection): List<ReleaseCategory> = withSession { token ->
        catalog(contentPreferences.hideAdult.value, token).getValue(section)
    }

    private suspend fun catalog(hideAdult: Boolean, token: String?) = categoryMutex.withLock {
        categoryCache[hideAdult] ?: api.categories(token, hideAdult).also { categoryCache[hideAdult] = it }
    }

    private suspend fun excludedCategories(hideAdult: Boolean): Set<Long> {
        if (!hideAdult) return emptySet()
        return withSession { token ->
            val all = catalog(false, token).values.flatten().map { it.id }.toSet()
            val visible = catalog(true, token).values.flatten().map { it.id }.toSet()
            // Use the server's category policy; names such as Young Adult are not 18+ flags.
            all - visible
        }
    }

    override suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, filters: FeedFilters): ReleasePage {
        val effective = if (customCategoriesEnabled()) filters else filters.copy(customCategoryIds = emptySet())
        val local = customCategories?.state?.value ?: CustomCategoryState()
        val hideAdult = contentPreferences.hideAdult.value
        val excluded = excludedCategories(hideAdult)
        val categories = effective.categoryIds - excluded
        if (effective.customCategoryIds.isNotEmpty()) {
            if (categories.isEmpty() && local.items.none { item -> item.categoryIds.any(effective.customCategoryIds::contains) &&
                item.release.section == section }) return ReleasePage(emptyList(), false)
            return filteredReleasePages(page, effective.copy(categoryIds = emptySet(), customCategoryIds = emptySet()),
                newestFirst = order == FeedOrder.Latest, excludedCategories = excluded) { sourcePage ->
                categoryReleases(section, order, sourcePage, categories, hideAdult, effective.customCategoryIds, local)
            }
        }
        if (effective.categoryIds.isNotEmpty() && categories.isEmpty()) return ReleasePage(emptyList(), false)
        return filteredReleasePages(page, effective.copy(categoryIds = emptySet()),
            newestFirst = order == FeedOrder.Latest, excludedCategories = excluded) { sourcePage ->
            categoryReleases(section, order, sourcePage, categories, hideAdult)
        }
    }

    private suspend fun categoryReleases(section: ContentSection, order: FeedOrder, page: Int, categories: Set<Long>, hideAdult: Boolean,
        customIds: Set<String> = emptySet(), local: CustomCategoryState = CustomCategoryState()): ReleasePage =
        withSession { token ->
            if (categories.isEmpty() && customIds.isEmpty()) api.releases(section, order, page, token, hideAdult = hideAdult)
            else pagerMutex.withLock {
                val query = Query(section, order, categories.toSet(), hideAdult, customIds.toSet(),
                    local.items.filter { item -> item.categoryIds.any(customIds::contains) })
                if (page == 1 || pagerQuery != query) {
                    pagerQuery = query
                    pagerPage = 0
                    pagerResults.clear()
                    pager = CategoryFeedPager(query.categories, order,
                        localMatches = if (customIds.isNotEmpty()) ({ release -> local.matches(release, customIds) }) else null) { category, sourcePage ->
                        api.releases(section, order, sourcePage, token, category.takeIf { it > 0 }, hideAdult).let { result ->
                            if (category == 0L) result else result.copy(items = result.items.map { it.copy(categoryId = it.categoryId ?: category) })
                        }
                    }
                }
                // A date scan can fail after consuming several category pages. Retrying must replay them.
                pagerResults[page] ?: run {
                    check(page == pagerPage + 1) { "Feed pages must be loaded in sequence" }
                    checkNotNull(pager).nextPage().also {
                        pagerPage = page
                        pagerResults[page] = it
                        pagerResults.keys.removeAll { cached -> cached < page - 5 }
                    }
                }
            }
        }

    override suspend fun release(id: Long): Release = withSession { token ->
        val account = session
        val release = api.release(id, token)
        val key = account?.takeIf { it.accessToken == token }?.let { reactionKey(it.username, id) }
        if (release.liked != null && key != null) reactions.edit().putBoolean(key, release.liked).apply()
        release.copy(liked = release.liked ?: key?.takeIf(reactions::contains)?.let { reactions.getBoolean(it, false) })
    }

    override suspend fun toggleLike(release: Release): Boolean = withSession { token ->
        val account = session?.takeIf { it.accessToken == token }
            ?: throw ApiFailure(FailureKind.Credentials, "Sign in to give this release a thumbs up.")
        api.toggleLike(release, account.accessToken).also {
            reactions.edit().putBoolean(reactionKey(account.username, release.id), it).apply()
        }
    }

    private fun reactionKey(username: String, id: Long): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(username.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) } + ":$id"

    override suspend fun comments(id: Long, page: Int): CommentPage = withSession { token -> api.comments(id, page, token) }

    override suspend fun postComment(id: Long, text: String, replyTo: Long?): Long = withSession { token ->
        val account = session?.takeIf { it.accessToken == token }
            ?: throw ApiFailure(FailureKind.Credentials, "Sign in to post a comment.")
        api.postComment(id, text, account.accessToken, replyTo)
    }

    override suspend fun discussionCategories(area: DiscussionArea) = withSession { api.discussionCategories(area, it) }

    override suspend fun discussionLastActivity(category: DiscussionCategory): Long? = withSession { token ->
        api.discussionTopics(category, 1, "", token).items.maxOfOrNull { it.activityAt }?.takeIf { it > 0 }
    }

    override suspend fun homeReleases(section: ContentSection, order: FeedOrder) =
        releases(section, order, 1, FeedFilters()).items.take(12)

    override suspend fun homeCategories(section: ContentSection) = categories(section)

    override suspend fun homeReviews(section: ContentSection): HomeTopics? = withSession { token ->
        val category = api.discussionCategories(section.discussionArea(), token)
            .firstOrNull { Regex("\\breviews?\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }
            ?: return@withSession null
        HomeTopics(category, api.discussionTopics(category, 1, "", token, includePinned = false).items.take(3))
    }

    override suspend fun homeDiscussions(section: ContentSection): HomeTopics = withSession { token ->
        val category = DiscussionCategory(12, "Recent discussions", section.discussionArea())
        HomeTopics(category, api.discussionTopics(category, 1, "", token, includePinned = false).items.take(3))
    }

    override suspend fun discussionTopics(category: DiscussionCategory, page: Int, query: String) =
        withSession { api.discussionTopics(category, page, query, it) }

    override suspend fun discussionComments(topic: DiscussionTopic, page: Int) = withSession { api.discussionComments(topic, page, it) }

    override suspend fun postDiscussionComment(topic: DiscussionTopic, text: String, replyTo: Long?) = withSession { token ->
        val account = session?.takeIf { it.accessToken == token }
            ?: throw ApiFailure(FailureKind.Credentials, "Sign in to join this discussion.")
        api.postDiscussionComment(topic, text, replyTo, account.accessToken)
    }

    override suspend fun messages(folder: MessageFolder, page: Int): MessagePage = withMessageSession { account ->
        val userId = account.userId ?: throw ApiFailure(FailureKind.Credentials,
            "Sign in again to load your messages. Your saved login needs to be updated.")
        api.messages(folder, userId, page, account.accessToken)
    }

    override suspend fun conversation(id: Long): List<PrivateMessage> = withMessageSession { api.conversation(id, it.accessToken) }

    override suspend fun markMessageRead(id: Long) = withMessageSession { api.markMessageRead(id, it.accessToken) }

    override suspend fun replyToMessage(message: PrivateMessage, text: String) = withMessageSession {
        api.replyToMessage(message, text, it)
    }

    private suspend fun <T> withMessageSession(block: suspend (Session) -> T): T = withSession { token ->
        val account = session?.takeIf { it.accessToken == token }
            ?: throw ApiFailure(FailureKind.Credentials, "Sign in to read and reply to messages.")
        block(account)
    }

    override suspend fun search(query: String, scope: SearchScope, page: Int, filters: FeedFilters): ReleasePage {
        val effective = if (customCategoriesEnabled()) filters else filters.copy(customCategoryIds = emptySet())
        val local = customCategories?.state?.value ?: CustomCategoryState()
        if (effective.customCategoryIds.isNotEmpty() && effective.categoryIds.isEmpty() && local.items.none { item ->
            item.categoryIds.any(effective.customCategoryIds::contains) && (scope.section == null || item.release.section == scope.section)
        }) return ReleasePage(emptyList(), false)
        val hideAdult = contentPreferences.hideAdult.value
        return filteredReleasePages(page, effective, excludedCategories = excludedCategories(hideAdult),
            customCategories = local) { sourcePage ->
            withSession { token -> api.search(query, scope, sourcePage, token, hideAdult) }
        }
    }

    override suspend fun register(username: String, email: String, password: String) = api.register(username, email, password)

    override suspend fun restorePassword(email: String) = api.restorePassword(email)

    private suspend fun <T> withSession(block: suspend (String?) -> T): T {
        val token = session?.accessToken
        try {
            return block(token)
        } catch (error: ApiFailure) {
            if (error.kind == FailureKind.Session) authMutex.withLock {
                // A failed old request must not erase a newly established session.
                if (session?.accessToken == token) {
                    store.clear()
                    session = null
                    mutableAuth.value = AuthState.SignedOut
                }
            }
            throw error
        }
    }
}
