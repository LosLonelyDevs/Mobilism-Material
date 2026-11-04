package org.mobilism.material.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
internal fun MessageAvatar(name: String, url: String?, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    var loaded by remember(url) { mutableStateOf(false) }
    Surface(modifier, shape = CircleShape, color = color, contentColor = contentColor) {
        Box(contentAlignment = Alignment.Center) {
            if (!loaded) Text(name.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelMedium)
            if (url != null) AsyncImage(url, "$name profile picture", Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop, onSuccess = { loaded = true }, onError = { loaded = false })
        }
    }
}
