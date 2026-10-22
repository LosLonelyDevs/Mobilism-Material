package org.mobilism.material.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.FailureKind
import org.mobilism.material.data.FeedOrder
import org.mobilism.material.data.MobilismRepository
import org.mobilism.material.data.Release
import org.mobilism.material.settings.AppearanceStore

data class ScreenError(val message: String, val showNetworkSettings: Boolean = false)

internal fun Exception.forScreen(): ScreenError = when (this) {
    is ApiFailure -> ScreenError(message, kind == FailureKind.Permission || kind == FailureKind.Dns)
    else -> ScreenError("Something went wrong. Please try again.")
}

data class LoginState(val busy: Boolean = false, val error: ScreenError? = null)

data class FeedState(
    val section: ContentSection = ContentSection.Apps,
    val order: FeedOrder = FeedOrder.Latest,
    val items: List<Release> = emptyList(),
    val page: Int = 0,
    val busy: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: ScreenError? = null,
)

class MobilismViewModel(
    context: Context,
    val repository: MobilismRepository,
    val appearance: AppearanceStore,
    val debrid: DebridRepository,
) : ViewModel() {
    private val preferences = context.getSharedPreferences("material-navigation", Context.MODE_PRIVATE)
    val preferredSection: ContentSection?
        get() = ContentSection.entries.firstOrNull { it.name == preferences.getString("section", null) }
    val auth = repository.auth
    private val mutableLogin = MutableStateFlow(LoginState())
    val login = mutableLogin.asStateFlow()
    private val mutableFeed = MutableStateFlow(FeedState(section = preferredSection ?: ContentSection.Apps))
    val feed = mutableFeed.asStateFlow()
    private var feedJob: Job? = null
    private var loginJob: Job? = null

    init {
        viewModelScope.launch {
            try { debrid.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Settings exposes retry and key replacement. */ }
        }
        viewModelScope.launch {
            try { appearance.load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the bundled theme if local preferences are unreadable. */ }
        }
        viewModelScope.launch {
            auth.collect { state ->
                if (state == AuthState.SignedOut) {
                    feedJob?.cancel()
                    mutableFeed.value = FeedState(section = preferredSection ?: ContentSection.Apps)
                }
            }
        }
        viewModelScope.launch {
            try { repository.restoreSession() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableLogin.value = LoginState(error = error.forScreen()) }
        }
    }

    fun signIn(username: String, password: String) = authAction {
        if (username.isBlank() || password.isEmpty()) {
            mutableLogin.value = LoginState(error = ScreenError("Enter your username and password."))
        } else repository.signIn(username, password)
    }

    fun continueAsGuest() = authAction { repository.continueAsGuest() }

    private fun authAction(block: suspend () -> Unit) {
        if (loginJob?.isActive == true) return
        loginJob = viewModelScope.launch {
            mutableLogin.value = LoginState(busy = true)
            try {
                block()
                mutableLogin.update { it.copy(busy = false) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableLogin.value = LoginState(error = error.forScreen()) }
        }
    }

    fun signOut() {
        feedJob?.cancel()
        authAction {
            repository.signOut()
            mutableFeed.value = FeedState(section = mutableFeed.value.section)
        }
    }

    fun selectSection(section: ContentSection) {
        preferences.edit().putString("section", section.name).apply()
        feedJob?.cancel()
        mutableFeed.value = FeedState(section = section, order = mutableFeed.value.order)
        loadFeed(refresh = true)
    }

    fun selectOrder(order: FeedOrder) {
        if (mutableFeed.value.order == order) return
        feedJob?.cancel()
        mutableFeed.value = FeedState(section = mutableFeed.value.section, order = order)
        loadFeed(refresh = true)
    }

    fun loadFeed(refresh: Boolean = false) {
        if (auth.value == AuthState.SignedOut || auth.value == AuthState.Restoring) return
        if (!refresh && (feedJob?.isActive == true || !mutableFeed.value.canLoadMore)) return
        feedJob?.cancel()
        val snapshot = mutableFeed.value
        val page = if (refresh) 1 else snapshot.page + 1
        feedJob = viewModelScope.launch {
            mutableFeed.update { it.copy(busy = true, error = null) }
            try {
                val result = repository.releases(snapshot.section, snapshot.order, page)
                val existing = if (refresh) emptyList() else snapshot.items
                val combined = (existing + result.items).distinctBy { it.id }
                mutableFeed.update { it.copy(
                    items = combined, page = page, busy = false,
                    canLoadMore = result.canLoadMore && (refresh || combined.size > existing.size),
                ) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableFeed.update { it.copy(busy = false, error = error.forScreen()) }
            }
        }
    }
}

data class ReleaseState(val release: Release? = null, val busy: Boolean = true, val error: ScreenError? = null)

class ReleaseViewModel(private val id: Long, private val repository: MobilismRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ReleaseState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try { mutableState.value = ReleaseState(release = repository.release(id), busy = false) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableState.value = ReleaseState(busy = false, error = error.forScreen()) }
        }
    }
}
