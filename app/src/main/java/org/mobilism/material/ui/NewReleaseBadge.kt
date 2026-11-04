package org.mobilism.material.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mobilism.material.data.ContentSection
import org.mobilism.material.data.Release
import org.mobilism.material.designsystem.LocalReducedMotion

@Composable
internal fun NewReleaseBadge(id: Long, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val pulse = if (LocalReducedMotion.current) 0f else {
        val transition = rememberInfiniteTransition(label = "new release glow")
        val value by transition.animateFloat(0f, 1f,
            infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow intensity")
        value
    }
    Badge(containerColor = color,
        modifier = modifier.drawBehind {
            if (pulse > 0f) {
                val radius = size.minDimension * (0.8f + pulse * 0.7f)
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f * pulse), color.copy(alpha = 0f)),
                    center, radius), radius, center)
            }
        }.testTag("new-release-$id").semantics {
            contentDescription = "New since your last visit"
        })
}

@Composable
internal fun ObserveVisibleReleases(list: LazyListState, section: ContentSection, releases: List<Release>,
    enabled: Boolean = true, isVisible: () -> Boolean = { true },
    onViewed: (ContentSection, List<Release>) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnViewed by rememberUpdatedState(onViewed)
    val currentVisibility by rememberUpdatedState(isVisible)
    LaunchedEffect(lifecycle, list, section, releases, enabled) {
        if (!enabled || releases.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow {
                if (currentVisibility()) list.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long }.toSet()
                else emptySet()
            }.distinctUntilChanged().collect { ids ->
                val visible = releases.filter { it.id in ids }
                if (visible.isNotEmpty()) currentOnViewed(section, visible)
            }
        }
    }
}
