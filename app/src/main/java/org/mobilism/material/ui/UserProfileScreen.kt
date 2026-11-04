package org.mobilism.material.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.mobilism.material.data.*
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState

internal val LocalProfileHandler = staticCompositionLocalOf<((ProfileReference) -> Unit)?> { null }

@Composable
internal fun UsernameLink(name: String, modifier: Modifier = Modifier, label: String = name,
    style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = 1, fontWeight: FontWeight? = null, onOpen: () -> Unit = {}) {
    val open = LocalProfileHandler.current
    Box(modifier.minimumInteractiveComponentSize().clip(MaterialTheme.shapes.small)
        .clickable(enabled = open != null && name.isNotBlank(), role = Role.Button, onClickLabel = "View $name profile") {
            onOpen()
            open?.invoke(ProfileReference(username = name))
        }, contentAlignment = Alignment.CenterStart) {
        Text(label, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, fontWeight = fontWeight)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UserProfileScreen(reference: ProfileReference, load: suspend (ProfileReference) -> UserProfile, onBack: () -> Unit) {
    val context = LocalContext.current
    var profile by remember(reference) { mutableStateOf<UserProfile?>(null) }
    var error by remember(reference) { mutableStateOf<ScreenError?>(null) }
    var attempt by remember(reference) { mutableIntStateOf(0) }
    var verify by remember(reference) { mutableStateOf(false) }
    var canVerify by remember(reference) { mutableStateOf(false) }
    LaunchedEffect(reference, attempt) {
        error = null
        try { profile = load(reference) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = failure.forScreen()
            canVerify = failure is ApiFailure && (failure.kind == FailureKind.Challenge || failure.httpStatus == 403)
        }
    }
    if (verify) ForumAccessVerification(reference.url, onRead = { html ->
        runCatching { ForumProfiles.parse(html, reference) }.getOrNull()?.let { profile = it; error = null; true } ?: false
    }, onDismiss = { verify = false })
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Profile", HeadingLevel.Page, style = MaterialTheme.typography.titleLarge) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
            actions = { AppIconButton(Icons.Outlined.OpenInBrowser, "Open profile in browser", { openForumBrowser(context, reference.url) }) })
    }) { insets ->
        Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Crossfade(profile, Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "profile content") { member ->
                if (member != null) UserProfileContent(member)
                else Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    error?.let { ErrorPanel(it, onRetry = { attempt++ }) } ?: LoadingState("Loading profile…")
                    if (error != null && canVerify) ExpressiveButton({ verify = true }) { Text("Verify forum access") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserProfileContent(profile: UserProfile, onOpenContact: ((String) -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val displayed = profile.roles.map { if (it == AccountRole.Member) profile.rank.ifBlank { "Member" } else it.label }
    val extraBadges = (listOf(profile.rank) + profile.groups).filter { badge ->
        badge.isNotBlank() && !badge.equals("Registered users", true) && !badge.equals("Member", true) &&
            displayed.none { badge.equals(it, true) || badge.equals(it + "s", true) }
    }.distinctBy { it.lowercase(java.util.Locale.ROOT) }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item("identity") {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MessageAvatar(profile.username, profile.avatar, Modifier.size(80.dp), initialStyle = MaterialTheme.typography.headlineMedium)
                    Text(profile.username, style = MaterialTheme.typography.headlineSmall, color = colors.primary, textAlign = TextAlign.Center)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.roles.forEach { role -> ProfileRoleBadge(role,
                            if (role == AccountRole.Member) profile.rank.ifBlank { "Member" } else role.label) }
                    }
                    if (profile.posts != null || profile.balance != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            profile.posts?.let { ProfileStatistic("Posts", it, Modifier.weight(1f)) }
                            profile.balance?.let { ProfileStatistic("WRZ$", it, Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (profile.contacts.isNotEmpty()) item("contacts") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.contacts.forEach { contact ->
                    ExpressiveButton({ onOpenContact?.invoke(contact.url) ?: run { openWeb(context, contact.url); Unit } }, compact = true,
                        colors = ButtonDefaults.filledTonalButtonColors()) {
                        Icon(when (contact.kind) {
                            ProfileContactKind.Telegram -> Icons.AutoMirrored.Outlined.Send
                            ProfileContactKind.WhatsApp -> Icons.Outlined.ChatBubbleOutline
                            ProfileContactKind.Website -> Icons.AutoMirrored.Outlined.OpenInNew
                        }, null, Modifier.size(18.dp))
                        Text(if (contact.kind == ProfileContactKind.Website) contact.label else "Join ${contact.label}",
                            Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (extraBadges.isNotEmpty()) item("badges") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppHeading("Badges", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    extraBadges.forEach { ProfileRoleBadge(AccountRole.Member, it) }
                }
            }
        }
        if (profile.about.isNotBlank()) item("about") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppHeading("About", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                SelectionContainer { Text(profile.about, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        if (profile.joined != null || profile.lastVisited != null) item("community") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppHeading("Community", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium, color = colors.primary)
                profile.joined?.let { ProfileDetail("Joined", it) }
                profile.lastVisited?.let { ProfileDetail("Last active", it) }
            }
        }
        if (profile.details.isNotEmpty()) item("details-title") {
            AppHeading("Details", HeadingLevel.Section, style = MaterialTheme.typography.titleMedium, color = colors.primary)
        }
        items(profile.details.entries.toList(), key = { it.key }) { ProfileDetail(it.key, it.value) }
    }
}

@Composable
private fun ProfileStatistic(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ProfileRoleBadge(role: AccountRole, label: String) {
    val colors = MaterialTheme.colorScheme
    val (background, foreground) = when (role) {
        AccountRole.Administrator -> colors.tertiaryContainer to colors.onTertiaryContainer
        AccountRole.Developer -> colors.primaryContainer to colors.onPrimaryContainer
        AccountRole.Moderator -> colors.secondaryContainer to colors.onSecondaryContainer
        AccountRole.Member -> colors.surfaceContainerHigh to colors.onSurface
    }
    Surface(shape = MaterialTheme.shapes.large, color = background, contentColor = foreground) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(when (role) {
                AccountRole.Administrator -> Icons.Outlined.Shield
                AccountRole.Moderator -> Icons.Outlined.VerifiedUser
                AccountRole.Developer -> Icons.Outlined.Code
                AccountRole.Member -> if (label.endsWith("Releaser", true)) Icons.Outlined.WorkspacePremium else Icons.Outlined.Person
            }, null, Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
