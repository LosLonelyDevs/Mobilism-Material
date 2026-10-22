package org.mobilism.material.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.carousel.CarouselDefaults
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.launch
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReleaseImageCarousel(images: List<String>, onImage: (Int) -> Unit) {
    if (images.isEmpty()) return
    val state = rememberCarouselState { images.size }
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 300.dp,
            modifier = Modifier.fillMaxWidth(),
            itemSpacing = 12.dp,
            flingBehavior = CarouselDefaults.singleAdvanceFlingBehavior(state, motion),
        ) { index ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = MaterialTheme.shapes.extraLarge
            Box(
                Modifier.height(248.dp)
                    .maskClip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    // The focus border follows the moving mask instead of the full image bounds.
                    .then(if (focused) Modifier.maskBorder(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
                    .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button,
                        onClickLabel = "View full size image") { onImage(index) },
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = images[index],
                    contentDescription = "Release image ${index + 1} of ${images.size}",
                    contentScale = if (images.size == 1) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingState("Loading image…") } },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Image unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                )
            }
        }
        if (images.size > 1) Row(
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.currentItem > 0) AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Previous release image", {
                scope.launch { state.animateScrollToItem(state.currentItem - 1, motion) }
            }) else Box(Modifier.size(48.dp))
            Text("${state.currentItem + 1} / ${images.size}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            if (state.currentItem < images.lastIndex) AppIconButton(Icons.AutoMirrored.Outlined.ArrowForward, "Next release image", {
                scope.launch { state.animateScrollToItem(state.currentItem + 1, motion) }
            }) else Box(Modifier.size(48.dp))
        }
    }
}
