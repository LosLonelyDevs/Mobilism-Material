package org.mobilism.material.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import java.text.BreakIterator
import java.util.Locale
import org.mobilism.material.designsystem.LocalAppTheme
import org.mobilism.material.members.MemberIdentityStore
import org.mobilism.material.members.displayAvatar

@Composable
internal fun MessageAvatar(name: String, url: String?, modifier: Modifier = Modifier,
    initialStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium,
    lookupIdentity: Boolean = true, onProfile: (() -> Unit)? = profileClick(name)) {
    val store = LocalMemberIdentities.current
    val identity = memberIdentity(name, lookup = lookupIdentity)
    var rejected by remember(name, url, identity?.avatar) { mutableStateOf(emptySet<String>()) }
    val observed = url.displayAvatar()
    val source = listOfNotNull(identity?.avatar.displayAvatar(), observed).firstOrNull { it !in rejected }
    var loaded by remember(source) { mutableStateOf(false) }
    LaunchedEffect(store, name, observed) {
        if (observed != null) store?.rememberAvatar(name, observed)
    }
    val theme = LocalAppTheme.current
    val scheme = MaterialTheme.colorScheme
    val (color, contentColor) = when (Math.floorMod(MemberIdentityStore.key(name).hashCode(), 8)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        2 -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        3 -> scheme.errorContainer to scheme.onErrorContainer
        4 -> scheme.primary to scheme.onPrimary
        5 -> theme.success to theme.onSuccess
        6 -> theme.featured to theme.onFeatured
        else -> theme.reading to theme.onReading
    }
    val initial = remember(name) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) "?" else {
            val letters = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(trimmed) }
            trimmed.substring(0, letters.next()).uppercase(Locale.ROOT)
        }
    }
    ProfileTouchBounds {
        val target = if (onProfile != null) Modifier.clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "View $name profile", onClick = onProfile) else Modifier
        Surface(modifier.then(target), shape = CircleShape, color = color, contentColor = contentColor) {
            Box(contentAlignment = Alignment.Center) {
                if (!loaded) Text(initial, style = initialStyle, maxLines = 1)
                if (source != null) AsyncImage(source, "$name profile picture", Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop, onSuccess = { loaded = true },
                    onError = { loaded = false; rejected = rejected + source })
            }
        }
    }
}
