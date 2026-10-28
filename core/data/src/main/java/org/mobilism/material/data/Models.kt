package org.mobilism.material.data

enum class ContentSection(val label: String, val apiPath: String) {
    Apps("Apps", "app"), Games("Games", "game"), Books("Books", "book"),
}

enum class FeedOrder(val label: String, val apiValue: String) {
    Latest("Latest", "date"), Top("Top", "views"),
}

enum class SearchSort(val label: String) {
    Newest("Newest posts"), Oldest("Oldest posts"), Activity("Latest activity"),
}

data class Release(
    val id: Long,
    val title: String,
    val author: String,
    val thumbnail: String?,
    val publishedAt: Long,
    val replies: Long,
    val views: Long,
    val contentHtml: String = "",
    val images: List<String> = emptyList(),
    val downloadLinks: List<String> = emptyList(),
    val browserUrl: String? = null,
    val section: ContentSection? = null,
    val likeId: Long? = null,
    val liked: Boolean? = null,
    val categoryId: Long? = null,
)

data class ReleasePage(val items: List<Release>, val canLoadMore: Boolean, val lastPage: Int? = null,
    val nextActivityAtMost: Long? = null)

data class CommentQuote(val author: String, val html: String)
data class ReleaseComment(
    val id: Long, val author: String, val avatar: String?, val publishedAt: Long,
    val contentHtml: String, val images: List<String> = emptyList(), val quotes: List<CommentQuote> = emptyList(),
)
data class CommentPage(val items: List<ReleaseComment>, val canLoadMore: Boolean)

data class ReleaseCategory(val id: Long, val name: String, val group: String? = null)

/** Additional filter dimensions can be added without changing feed navigation. */
data class FeedFilters(
    val categoryIds: Set<Long> = emptySet(),
    val postedFrom: java.time.LocalDate? = null,
    val postedThrough: java.time.LocalDate? = null,
    val searchSort: SearchSort = SearchSort.Newest,
    val customCategoryIds: Set<String> = emptySet(),
) {
    val hasDates: Boolean get() = postedFrom != null || postedThrough != null
    val isActive: Boolean get() = categoryIds.isNotEmpty() || customCategoryIds.isNotEmpty() || hasDates
    val validDates: Boolean get() = postedFrom == null || postedThrough == null || postedFrom <= postedThrough

    fun matches(release: Release, zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
        customCategories: CustomCategoryState = CustomCategoryState()): Boolean {
        if ((categoryIds.isNotEmpty() || customCategoryIds.isNotEmpty()) &&
            release.categoryId !in categoryIds && !customCategories.matches(release, customCategoryIds)) return false
        if (!hasDates) return true
        if (!validDates || release.publishedAt <= 0) return false
        val date = java.time.Instant.ofEpochSecond(release.publishedAt).atZone(zone).toLocalDate()
        return (postedFrom == null || date >= postedFrom) && (postedThrough == null || date <= postedThrough)
    }
}

enum class SearchScope(val label: String, val apiId: String, val section: ContentSection?) {
    All("All releases", "all", null), Apps("Apps", "app", ContentSection.Apps),
    Games("Games", "game", ContentSection.Games), Books("Books", "book", ContentSection.Books),
}

sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data object Guest : AuthState
    data class SignedIn(val username: String) : AuthState
}

internal data class Session(val username: String, val accessToken: String, val refreshToken: String?, val userId: Long? = null) {
    override fun toString(): String = "Session([redacted])"
}

enum class FailureKind { Permission, Dns, Connection, Challenge, Credentials, Session, Server, InvalidResponse }

class ApiFailure(val kind: FailureKind, override val message: String, val httpStatus: Int? = null) : Exception(message)

interface MobilismRepository {
    val auth: kotlinx.coroutines.flow.StateFlow<AuthState>
    suspend fun restoreSession()
    suspend fun signIn(username: String, password: String)
    suspend fun continueAsGuest()
    suspend fun signOut()
    suspend fun register(username: String, email: String, password: String)
    suspend fun restorePassword(email: String)
    suspend fun categories(section: ContentSection): List<ReleaseCategory>
    suspend fun releases(section: ContentSection, order: FeedOrder, page: Int, filters: FeedFilters = FeedFilters()): ReleasePage
    suspend fun release(id: Long): Release
    suspend fun toggleLike(release: Release): Boolean
    suspend fun comments(id: Long, page: Int): CommentPage
    suspend fun postComment(id: Long, text: String, replyTo: Long? = null): Long
    suspend fun search(query: String, scope: SearchScope, page: Int, filters: FeedFilters = FeedFilters()): ReleasePage
}
