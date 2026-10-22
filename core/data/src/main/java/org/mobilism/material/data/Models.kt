package org.mobilism.material.data

enum class ContentSection(val label: String, val apiPath: String) {
    Apps("Apps", "app"), Games("Games", "game"), Books("Books", "book"),
}

enum class FeedOrder(val label: String, val apiValue: String) {
    Latest("Latest", "date"), Top("Top", "views"),
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
)

data class ReleasePage(val items: List<Release>, val canLoadMore: Boolean)

sealed interface AuthState {
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data object Guest : AuthState
    data class SignedIn(val username: String) : AuthState
}

internal data class Session(val username: String, val accessToken: String, val refreshToken: String?) {
    override fun toString(): String = "Session([redacted])"
}

enum class FailureKind { Permission, Dns, Connection, Challenge, Credentials, Session, Server, InvalidResponse }

class ApiFailure(val kind: FailureKind, override val message: String) : Exception(message)

interface MobilismRepository {
    val auth: kotlinx.coroutines.flow.StateFlow<AuthState>
    suspend fun restoreSession()
    suspend fun signIn(username: String, password: String)
    suspend fun continueAsGuest()
    suspend fun signOut()
    suspend fun register(username: String, email: String, password: String)
    suspend fun restorePassword(email: String)
    suspend fun releases(section: ContentSection, order: FeedOrder, page: Int): ReleasePage
    suspend fun release(id: Long): Release
}
