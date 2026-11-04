package org.mobilism.material.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Both download presentations share pending requests; each row owns cancellation. */
class DownloadRequestTracker {
    private val mutablePending = MutableStateFlow(emptySet<String>())
    val pending = mutablePending.asStateFlow()

    @Synchronized fun begin(url: String): Boolean {
        if (url in mutablePending.value) return false
        mutablePending.value += url
        return true
    }

    @Synchronized fun finish(url: String) {
        mutablePending.value -= url
    }
}
