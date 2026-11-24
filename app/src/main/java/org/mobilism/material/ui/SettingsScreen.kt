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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import org.mobilism.material.R
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.data.DebridAccount
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.DebridProvider
import org.mobilism.material.data.DebridRepository
import org.mobilism.material.data.DebridState
import org.mobilism.material.data.HostOrder
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.LoadingState
import org.mobilism.material.designsystem.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: DebridState, repository: DebridRepository, onAppearance: () -> Unit, onDownloads: () -> Unit, onBack: () -> Unit,
    hideAdult: Boolean = false, onHideAdult: (Boolean) -> Unit = {}, onAbout: () -> Unit = {},
    developer: org.mobilism.material.settings.DeveloperPreferences = org.mobilism.material.settings.DeveloperPreferences(),
    onDeveloperOptions: () -> Unit = {}, diagnostics: org.mobilism.material.settings.DiagnosticLogStore? = null,
    preferredHosts: Set<String> = emptySet(),
    landingPage: org.mobilism.material.settings.LandingPage = org.mobilism.material.settings.LandingPage(),
    onLandingPage: (org.mobilism.material.settings.LandingPage) -> Unit = {},
    dailyUsageEnabled: Boolean = true, onDailyUsage: (Boolean) -> Unit = {}, onUpdates: () -> Unit = {},
    customCategoriesEnabled: Boolean = true, onCustomCategories: (Boolean) -> Unit = {},
    messageNotifications: (@Composable () -> Unit)? = null) {
    var premiumOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(developer.debridEnabled) { if (!developer.debridEnabled) premiumOpen = false }
    BackHandler(enabled = premiumOpen) { premiumOpen = false }
    if (premiumOpen && developer.debridEnabled) {
        PremiumSettingsScreen(state, repository, preferredHosts, onBack = { premiumOpen = false })
        return
    }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading(stringResource(R.string.settings_title), HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
        })
    }) { insets ->
        SettingsList(Modifier.padding(insets), state = listState) {
            item("preferences-section") { SettingsSectionLabel(stringResource(R.string.settings_preferences)) }
            item("appearance") { SettingsNavigationRow(stringResource(R.string.appearance_title), stringResource(R.string.settings_appearance_summary), Icons.Outlined.Palette, onAppearance) }
            messageNotifications?.let { setting -> item("message-notifications") { setting() } }
            item("landing-page") { LandingPageSetting(landingPage, onLandingPage) }
            item("content") { ContentVisibilitySetting(hideAdult, onHideAdult) }
            item("custom-categories") { CustomCategoriesSetting(customCategoriesEnabled, onCustomCategories) }
            item("downloads-section") { SettingsSectionLabel(stringResource(R.string.settings_downloads)) }
            item("downloads") { SettingsNavigationRow(stringResource(R.string.settings_downloads), stringResource(R.string.settings_downloads_summary),
                Icons.Outlined.Download, onDownloads) }
            if (developer.debridEnabled) item("premium") {
                SettingsNavigationRow(stringResource(R.string.settings_premium), stringResource(R.string.settings_premium_summary), Icons.Outlined.WorkspacePremium,
                    onClick = { premiumOpen = true })
            }
            item("privacy-section") { SettingsSectionLabel(stringResource(R.string.settings_privacy)) }
            item(key = "daily-usage") {
                SettingsToggleRow(stringResource(R.string.settings_usage), stringResource(R.string.settings_usage_summary), Icons.Outlined.Insights,
                    dailyUsageEnabled, onCheckedChange = onDailyUsage)
            }
            diagnostics?.let { logs -> item(key = "diagnostics") { DiagnosticLogsCard(logs) } }
            item("app-section") { SettingsSectionLabel(stringResource(R.string.settings_app)) }
            item(key = "app-updates") { AppUpdatesEntry(onUpdates) }
            if (developer.enabled) item(key = "developer") {
                SettingsNavigationRow(stringResource(R.string.settings_developer_options), stringResource(R.string.settings_developer_summary), Icons.Outlined.Code, onDeveloperOptions)
            }
            item(key = "about") {
                SettingsNavigationRow(stringResource(R.string.settings_about), stringResource(R.string.settings_version, org.mobilism.material.BuildConfig.VERSION_NAME),
                    Icons.Outlined.Info, onAbout)
            }
        }
    }
}

@Composable
internal fun CustomCategoriesSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingsToggleRow(stringResource(R.string.settings_custom_categories), null, Icons.Outlined.Category, enabled, onCheckedChange = onChange)
}

@Composable
internal fun ContentVisibilitySetting(hideAdult: Boolean, onHideAdult: (Boolean) -> Unit) {
    SettingsToggleRow(stringResource(R.string.settings_hide_adult), stringResource(R.string.settings_hide_adult_summary), Icons.Outlined.VisibilityOff,
        hideAdult, onCheckedChange = onHideAdult)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumSettingsScreen(state: DebridState, repository: DebridRepository, preferredHosts: Set<String>, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading(stringResource(R.string.settings_premium), HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
        })
    }) { insets ->
        SettingsList(Modifier.padding(insets)) {
            item { SettingsSectionLabel(stringResource(R.string.premium_accounts)) }
            item { SettingsSubtitle(stringResource(R.string.premium_support_summary), Modifier.padding(horizontal = 16.dp)) }
            items(Alphabetical.sorted(state.accounts) { it.provider.label }
                .sortedBy { it.provider != DebridProvider.Mobilism }, key = { it.provider }) { account ->
                DebridAccountCard(account, repository, state, preferredHosts)
            }
        }
    }
}

@Composable
private fun DebridAccountCard(account: DebridAccount, repository: DebridRepository, debrid: DebridState, preferredHosts: Set<String>) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val theme = LocalAppTheme.current
    var key by remember(account.provider) { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<ScreenError?>(null) }
    var signingInAgain by remember(account.provider) { mutableStateOf(false) }
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
            catch (_: Exception) { error = ScreenError(resources.getString(R.string.premium_update_error)) }
            finally { working = false }
        }
    }
    AppSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (account.configured) Icon(
                    if (account.validated) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    if (account.validated) stringResource(R.string.premium_verified) else stringResource(R.string.premium_needs_verification),
                    tint = if (account.validated) theme.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                )
                Box(Modifier.weight(1f)) {
                    SettingsTitle(account.provider.label)
                }
            }
            if (!account.configured || signingInAgain) {
              if (account.provider != DebridProvider.RealDebrid) {
                val mobilism = account.provider == DebridProvider.Mobilism
                val service = if (mobilism) stringResource(R.string.mobilism_name) else account.provider.label
                SettingsSubtitle(if (mobilism) stringResource(R.string.premium_mobilism_summary)
                    else stringResource(R.string.premium_other_summary))
                OutlinedTextField(
                    value = username, onValueChange = { if (it.length <= 256) username = it },
                    label = { Text(stringResource(R.string.premium_username, service)) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = password, onValueChange = { if (it.length <= 4096) password = it },
                    label = { Text(if (mobilism) stringResource(R.string.premium_mobilism_password) else stringResource(R.string.premium_password, service)) }, singleLine = true, enabled = !busy,
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
                        signingInAgain = false
                    } },
                    enabled = username.isNotBlank() && password.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.premium_sign_in, account.provider.label)) }
              } else {
              Text(stringResource(R.string.premium_no_key), color = MaterialTheme.colorScheme.onSurfaceVariant)
              OutlinedTextField(
                value = key, onValueChange = { if (it.length <= 4096) key = it },
                label = { Text(stringResource(R.string.premium_api_key, account.provider.label)) },
                placeholder = { Text(stringResource(R.string.premium_key_hint)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
              ExpressiveButton(
                onClick = { perform { repository.saveKey(account.provider, key); key = "" } },
                enabled = key.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
              ) { Text(stringResource(R.string.premium_verify_save)) }
              }
            }
            if (account.configured && !account.validated && account.provider == DebridProvider.MegaDebrid && !signingInAgain) {
                ExpressiveButton({ signingInAgain = true }, enabled = !busy) { Text(stringResource(R.string.premium_sign_in_again)) }
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
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.premium_refresh))
                }
                DebridHostMenu(account, debrid, preferredHosts, Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = { perform { repository.removeKey(account.provider); key = "" } },
                    modifier = Modifier.size(48.dp), enabled = !busy,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.premium_remove))
                }
            }
            if (busy) LoadingState(stringResource(R.string.premium_checking), Modifier.fillMaxWidth())
            error?.let { ErrorPanel(it) }
            account.error?.let { ErrorPanel(ScreenError(it)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DebridHostMenu(account: DebridAccount, debrid: DebridState, preferredHosts: Set<String>, modifier: Modifier = Modifier) {
    val hosts = HostOrder.sorted(account.hosts, preferredHosts, debrid, { it.domain }) { it.name }
        .distinctBy { it.name.lowercase(java.util.Locale.ROOT) }
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val menuWidth = (windowWidth - 24.dp).coerceIn(0.dp, 560.dp)
    var expanded by remember(account.provider) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "host menu chevron",
    )
    val expansionLabel = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Box(modifier, contentAlignment = Alignment.Center) {
        ExpressiveButton(
            { expanded = !expanded },
            Modifier.fillMaxWidth().semantics { stateDescription = expansionLabel },
            compact = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            val count = hosts.size
            Text(pluralStringResource(R.plurals.premium_mobilism_hosts, count, count), Modifier.weight(1f, fill = false),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp).size(20.dp).rotate(rotation))
        }
        if (expanded) Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            AppSurface(
                Modifier.width(menuWidth).heightIn(max = 360.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.premium_supported_hosts), Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    )
                    if (hosts.isEmpty()) Text(
                        if (account.busy) stringResource(R.string.premium_loading_hosts) else stringResource(R.string.premium_hosts_empty),
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    hosts.forEach { host ->
                        val name = host.name
                        val domains = account.hosts.filter { it.name.equals(name, ignoreCase = true) }.map { it.domain }
                        RoundedMenuItem(
                            label = name, onClick = { expanded = false }, accent = MaterialTheme.colorScheme.primary,
                            supportLabel = debrid.accounts.filter { other ->
                                other.configured && other.hosts.any { it.domain in domains }
                            }.map { it.provider.label }.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
