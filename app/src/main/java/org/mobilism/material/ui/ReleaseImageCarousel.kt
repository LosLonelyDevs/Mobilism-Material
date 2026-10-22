package org.mobilism.material.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReleaseImageCarousel(images: List<String>, onImage: (Int) -> Unit) {
    if (images.isEmpty()) return
    val state = rememberCarouselState { images.size }
    val motion = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
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
}
