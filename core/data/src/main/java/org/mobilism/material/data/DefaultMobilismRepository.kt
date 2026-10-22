package org.mobilism.material.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultMobilismRepository(context: Context) : MobilismRepository {
    private val api = MobilismApi(context.applicationContext)
    private val store = SessionStore(context.applicationContext)
    private val authMutex = Mutex()
    private val categoryMutex = Mutex()
    private var categoryCache: Map<ContentSection, List<ReleaseCategory>>? = null
    private val pagerMutex = Mutex()
    private data class Query(val section: ContentSection, val order: FeedOrder, val categories: Set<Long>)
    private var pagerQuery: Query? = null
    private var pager: CategoryFeedPager? = null
    private var pagerPage = 0
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
        categoryMutex.withLock {
            val catalog = categoryCache ?: api.categories(token).also { categoryCache = it }
            catalog.getValue(section)
        }
    }

    override suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, filters: FeedFilters): ReleasePage =
        withSession { token ->
            if (filters.categoryIds.isEmpty()) api.releases(section, order, page, token)
            else pagerMutex.withLock {
                val query = Query(section, order, filters.categoryIds.toSet())
                if (page == 1 || pagerQuery != query) {
                    pagerQuery = query
                    pagerPage = 0
                    pager = CategoryFeedPager(query.categories, order) { category, sourcePage ->
                        api.releases(section, order, sourcePage, token, category)
                    }
                }
                check(page == pagerPage + 1) { "Feed pages must be loaded in sequence" }
                checkNotNull(pager).nextPage().also { pagerPage = page }
            }
        }

    override suspend fun release(id: Long): Release = withSession { token -> api.release(id, token) }

    override suspend fun comments(id: Long, page: Int): CommentPage = withSession { token -> api.comments(id, page, token) }

    override suspend fun search(query: String, scope: SearchScope, page: Int): ReleasePage =
        withSession { token -> api.search(query, scope, page, token) }

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
