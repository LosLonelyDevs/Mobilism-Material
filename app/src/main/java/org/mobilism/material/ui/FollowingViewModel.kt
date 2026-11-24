package org.mobilism.material.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.mobilism.material.MobilismApplication
import org.mobilism.material.following.FollowCheckState

data class FollowingError(val detail: ScreenError, val fromManualCheck: Boolean)

class FollowingViewModel(private val app: MobilismApplication) : AndroidViewModel(app) {
    val installed = app.following.installed
    private val manualProgress = MutableStateFlow(FollowCheckState())
    val check = manualProgress.asStateFlow()
    val preferences = app.followPreferences
    val limits = app.remoteControl.limitsState
    private val mutableError = MutableStateFlow<FollowingError?>(null)
    val error = mutableError.asStateFlow()

    fun refresh() { viewModelScope.launch {
        manualProgress.update { if (it.busy) it else FollowCheckState() }
        try {
            app.following.refreshInstalled()
            mutableError.update { it?.takeIf(FollowingError::fromManualCheck) }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableError.value = FollowingError(error.forScreen(), fromManualCheck = false) }
    } }
    fun checkNow() {
        if (check.value.busy || !limits.value.followPullToRefreshEnabled || limits.value.followCacheBatchSize == 0) return
        viewModelScope.launch {
            mutableError.value = null
            try { app.following.check(onProgress = { manualProgress.value = it }) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = FollowingError(error.forScreen(), fromManualCheck = true) }
        }
    }
    fun link(id: Long, packageName: String?) { app.library.linkPackage(id, packageName); refresh() }
}
