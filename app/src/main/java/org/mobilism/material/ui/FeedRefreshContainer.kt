package org.mobilism.material.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import org.mobilism.material.designsystem.LocalReducedMotion

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedRefreshContainer(refreshing: Boolean, busy: Boolean, onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit) {
    val pull = rememberPullToRefreshState()
    val refresh = { if (!busy) onRefresh() }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = refresh, state = pull,
        modifier = Modifier.fillMaxSize().semantics {
            customActions = listOf(CustomAccessibilityAction("Refresh") {
                if (busy) false else { onRefresh(); true }
            })
        }, indicator = {
            val modifier = Modifier.align(Alignment.TopCenter).then(if (refreshing) Modifier
                .testTag("feed-refresh-indicator").semantics {
                    contentDescription = "Refreshing releases"
                    liveRegion = LiveRegionMode.Polite
                } else Modifier)
            if (LocalReducedMotion.current) PullToRefreshDefaults.IndicatorBox(pull, refreshing, modifier) {
                Icon(Icons.Outlined.Refresh, null, tint = MaterialTheme.colorScheme.primary)
            } else PullToRefreshDefaults.LoadingIndicator(pull, refreshing, modifier)
        }, content = content)
}
