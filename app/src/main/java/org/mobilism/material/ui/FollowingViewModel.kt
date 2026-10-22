package org.mobilism.material.ui

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mobilism.material.MobilismApplication

class FollowingViewModel(private val app: MobilismApplication) : AndroidViewModel(app) {
    val installed = app.following.installed
    val check = app.following.state
    val preferences = app.followPreferences
    private val mutableError = MutableStateFlow<ScreenError?>(null)
    val error = mutableError.asStateFlow()

    fun refresh() { viewModelScope.launch {
        try { app.following.refreshInstalled() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { mutableError.value = error.forScreen() }
    } }
    fun checkNow() {
        if (check.value.busy) return
        viewModelScope.launch {
            mutableError.value = null
            try { app.following.check() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutableError.value = error.forScreen() }
        }
    }
    fun link(id: Long, packageName: String?) { app.library.linkPackage(id, packageName); refresh() }
}
