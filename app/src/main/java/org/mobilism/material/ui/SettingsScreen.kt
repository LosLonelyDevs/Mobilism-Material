package org.mobilism.material.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import org.mobilism.material.designsystem.AppSurface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridAccount
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.DebridProvider
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: DebridState, repository: DebridRepository, onAppearance: () -> Unit, onDownloads: () -> Unit, onBack: () -> Unit,
    hideAdult: Boolean = false, onHideAdult: (Boolean) -> Unit = {}, onAbout: () -> Unit = {},
    developer: org.mobilism.material.settings.DeveloperPreferences = org.mobilism.material.settings.DeveloperPreferences(),
    onDeveloperOptions: () -> Unit = {}) {
    val theme = LocalAppTheme.current
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Settings", HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        })
    }) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ExpressiveCard(onAppearance, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, theme.personalization, 0.12f),
                )) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Palette, null, tint = theme.personalization, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppHeading("Appearance", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                            Text("Theme, font, and motion", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = theme.personalization)
                    }
                }
            }
            item {
                ExpressiveCard(onDownloads, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerHigh, theme.reading, 0.12f),
                )) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Download, null, tint = theme.reading, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppHeading("Download preferences", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                            Text("CPU architecture and favorite hosts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = theme.reading)
                    }
                }
            }
            item {
                ContentVisibilitySetting(hideAdult, onHideAdult)
            }
            if (developer.debridEnabled) {
                item {
                    AppHeading("Premium", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Green buttons resolve supported links through a verified Premium account. Choose Mobilism Premium, MegaDebrid, or Real-Debrid.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(Alphabetical.sorted(state.accounts) { it.provider.label }, key = { it.provider }) { account ->
                    DebridAccountCard(account, repository, state)
                }
            }
            if (developer.enabled) item(key = "developer") {
                ExpressiveCard(onDeveloperOptions, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
                        AppHeading("Developer options", HeadingLevel.Section, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge)
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item(key = "about") {
                ExpressiveCard(onAbout, Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppHeading("About Mobilism Material", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                            Text("Version, release notes, and source code", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("v${org.mobilism.material.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContentVisibilitySetting(hideAdult: Boolean, onHideAdult: (Boolean) -> Unit) {
    AppSurface(shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().toggleable(hideAdult, role = androidx.compose.ui.semantics.Role.Switch,
            onValueChange = onHideAdult).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AppHeading("Hide 18+ content", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                Text("Hide adult categories and releases in browsing and search.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(hideAdult, onCheckedChange = null)
        }
    }
}

@Composable
private fun DebridAccountCard(account: DebridAccount, repository: DebridRepository, debrid: DebridState) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val theme = LocalAppTheme.current
    var key by remember(account.provider) { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ScreenError?>(null) }
    val busy = working || account.busy
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        keyboard?.hide()
        focus.clearFocus()
        scope.launch {
            working = true
            error = null
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: org.mobilism.material.data.DebridException) { error = ScreenError(failure.message.orEmpty()) }
            catch (_: Exception) { error = ScreenError("The account could not be updated. Please try again.") }
            finally { working = false }
        }
    }
    AppSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AppHeading(account.provider.label, HeadingLevel.Subsection, style = MaterialTheme.typography.titleLarge)
                }
                if (account.configured) Icon(
                    if (account.validated) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    if (account.validated) "Account verified" else "Account needs verification",
                    tint = if (account.validated) theme.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp).size(24.dp),
                )
            }
            if (!account.configured) {
              if (account.provider != DebridProvider.RealDebrid) {
                val mobilism = account.provider == DebridProvider.Mobilism
                val service = if (mobilism) "Mobilism" else account.provider.label
                Text(if (mobilism) "Use your Mobilism username and Premium password. Your forum password may be different."
                    else "Sign in with your Premium account", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = username, onValueChange = { if (it.length <= 256) username = it },
                    label = { Text("$service username") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = password, onValueChange = { if (it.length <= 4096) password = it },
                    label = { Text(if (mobilism) "Mobilism Premium password" else "$service password") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                    shape = MaterialTheme.shapes.large,
                )
                ExpressiveButton(
                    onClick = { perform {
                        if (mobilism) repository.connectMobilism(username, password)
                        else repository.connectMegaDebrid(username, password)
                        username = ""
                        password = ""
                    } },
                    enabled = username.isNotBlank() && password.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in to ${account.provider.label}") }
              } else {
              Text("No key saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
              OutlinedTextField(
                value = key, onValueChange = { if (it.length <= 4096) key = it },
                label = { Text(account.provider.label + " API key") },
                placeholder = { Text("Enter your API key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
              ExpressiveButton(
                onClick = { perform { repository.saveKey(account.provider, key); key = "" } },
                enabled = key.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
              ) { Text("Verify and save") }
              }
            }
            if (account.configured) Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { perform { repository.refresh(account.provider) } },
                    modifier = Modifier.size(48.dp), enabled = !busy,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(Icons.Outlined.Refresh, "Verify account and refresh hosts")
                }
                DebridHostMenu(account, debrid, Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { perform { repository.removeKey(account.provider); key = "" } },
                    modifier = Modifier.size(48.dp), enabled = !busy,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, "Remove account")
                }
            }
            if (busy) LoadingState("Checking account and hosts…", Modifier.fillMaxWidth())
            error?.let { ErrorPanel(it) }
            account.error?.let { ErrorPanel(ScreenError(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DebridHostMenu(account: DebridAccount, debrid: DebridState, modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    var expanded by remember(account.provider) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "host menu chevron",
    )
    Box(modifier, contentAlignment = Alignment.CenterEnd) {
        ExpressiveButton(
            { expanded = !expanded },
            Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            val count = account.hostNames.size
            Text("$count Mobilism ${if (count == 1) "host" else "hosts"}")
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp).size(20.dp).rotate(rotation))
        }
        DropdownMenu(
            expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp, max = 320.dp).heightIn(max = 360.dp),
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                "Supported Mobilism hosts", Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            if (account.hostNames.isEmpty()) Text(
                if (account.busy) "Loading hosts…" else "No matching hosts. Try Refresh hosts.",
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            account.hostNames.forEach { name ->
                val domains = account.hosts.filter { it.name.equals(name, ignoreCase = true) }.map { it.domain }
                RoundedMenuItem(
                    label = name, onClick = { expanded = false }, accent = theme.success,
                    icon = Icons.Outlined.CheckCircle,
                    supportLabel = debrid.accounts.filter { other ->
                        other.configured && other.hosts.any { it.domain in domains }
                    }.map { it.provider.label }.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                )
            }
        }
    }
}
