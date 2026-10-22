package org.mobilism.material.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridAccount
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.ExpressiveCard
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: DebridState, repository: DebridRepository, onAppearance: () -> Unit, onDownloads: () -> Unit, onBack: () -> Unit) {
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
                AppHeading("Debrid accounts", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Green download buttons mark Mobilism file hosts supported by a provider with a saved key. Links open in your browser.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.accounts, key = { it.provider }) { account ->
                DebridAccountCard(account, repository, state)
            }
        }
    }
}

@Composable
private fun DebridAccountCard(account: DebridAccount, repository: DebridRepository, debrid: DebridState) {
    val scope = rememberCoroutineScope()
    var key by remember(account.provider) { mutableStateOf("") }
    var editing by remember(account.provider) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ScreenError?>(null) }
    val busy = working || account.busy
    fun perform(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            working = true
            error = null
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = ScreenError("The key could not be updated. Please try again.") }
            finally { working = false }
        }
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppHeading(account.provider.label, HeadingLevel.Subsection, style = MaterialTheme.typography.titleLarge)
            if (account.configured) DebridAccountStatus(account, debrid)
            else Text("No key saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!account.configured || editing) {
              OutlinedTextField(
                value = key, onValueChange = { if (it.length <= 4096) key = it },
                label = { Text(account.provider.label + " API key") },
                placeholder = { Text(if (account.configured) "Enter a replacement key" else "Enter your key or token") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
              ExpressiveButton(
                onClick = { perform { repository.saveKey(account.provider, key); key = ""; editing = false } },
                enabled = key.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
              ) { Text("Save " + account.provider.label + " key") }
              if (account.configured) AppTextButton("Cancel", { key = ""; editing = false }, enabled = !busy)
            } else ExpressiveButton({ editing = true }, Modifier.fillMaxWidth(), enabled = !busy) {
                Icon(Icons.Outlined.Key, null, Modifier.padding(end = 8.dp).size(20.dp))
                Text("Change key")
            }
            if (account.configured) FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                ExpressiveButton(
                    { perform { repository.refresh(account.provider) } },
                    Modifier.weight(1f).widthIn(min = 144.dp), enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
                ) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.padding(end = 8.dp).size(20.dp))
                    Text("Refresh hosts")
                }
                ExpressiveButton(
                    { perform { repository.removeKey(account.provider); key = ""; editing = false } },
                    Modifier.weight(1f).widthIn(min = 144.dp), enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.padding(end = 8.dp).size(20.dp))
                    Text("Remove key")
                }
            }
            if (busy) LoadingState("Loading hosts…", Modifier.fillMaxWidth())
            error?.let { ErrorPanel(it) }
            account.error?.let { ErrorPanel(ScreenError(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DebridAccountStatus(account: DebridAccount, debrid: DebridState) {
    val theme = LocalAppTheme.current
    var expanded by remember(account.provider) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "host menu chevron",
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = theme.success, contentColor = theme.onSuccess, shape = MaterialTheme.shapes.extraLarge) {
            Row(
                Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp))
                Text("Key saved", style = MaterialTheme.typography.labelLarge)
            }
        }
        Box {
            ExpressiveButton(
                { expanded = !expanded },
                Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text("${account.hostNames.size} Mobilism hosts")
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
}
