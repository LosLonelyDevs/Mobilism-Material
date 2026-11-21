package org.mobilism.material.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.mobilism.material.R
import org.mobilism.material.designsystem.*
import org.mobilism.material.settings.AppearanceState
import org.mobilism.material.settings.AppearanceStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(state: AppearanceState, store: AppearanceStore, onBack: () -> Unit) {
    var showThemes by remember { mutableStateOf(false) }
    if (showThemes) {
        ThemePicker(state, onDismiss = { showThemes = false }, onSelect = {
            store.selectTheme(it)
            showThemes = false
        })
    }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading(stringResource(R.string.appearance_title), HeadingLevel.Page) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack) })
    }) { insets ->
        SettingsList(Modifier.padding(insets)) {
            item { SettingsSectionLabel(stringResource(R.string.appearance_theme)) }
            item {
                ThemeOption(resolveAppTheme(state.theme), false, { showThemes = true }, Modifier.fillMaxWidth(), chooser = true)
            }
            item { SettingsSectionLabel(stringResource(R.string.appearance_releases)) }
            item {
                AppearanceToggle(stringResource(R.string.appearance_compact_lists), stringResource(R.string.appearance_compact_lists_summary),
                    state.compactFeed, store::compactFeed)
            }
            item {
                AppearanceToggle(stringResource(R.string.appearance_format_release_text), stringResource(R.string.appearance_format_release_text_summary),
                    state.releaseTextCleanup, store::setReleaseTextCleanup)
            }
            item { SettingsSectionLabel(stringResource(R.string.appearance_font)) }
            item { Box(Modifier.padding(horizontal = 16.dp)) { FontPicker(state, store) } }
            item { SettingsSectionLabel(stringResource(R.string.appearance_motion)) }
            item {
                AppearanceToggle(stringResource(R.string.appearance_reduce_motion), stringResource(R.string.appearance_reduce_motion_summary),
                    state.reduceMotion, store::reduceMotion)
            }
        }
    }
}

@Composable
private fun AppearanceToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsToggleRow(title, description, null, checked, Modifier.semantics { contentDescription = title }, onCheckedChange)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePicker(state: AppearanceState, onDismiss: () -> Unit, onSelect: (ThemeDefinition) -> Unit) {
    val selectedTheme = resolveAppTheme(state.theme)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppHeading(stringResource(R.string.appearance_choose_theme), HeadingLevel.Section, Modifier.weight(1f))
                AppIconButton(Icons.Outlined.Close, stringResource(R.string.appearance_close_themes), onDismiss)
            }
            LazyColumn(Modifier.weight(1f).testTag("theme-options").selectableGroup(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) item(key = MaterialYou.id) {
                    ThemeOption(resolveAppTheme(MaterialYou), state.themeId == MaterialYou.id,
                        { onSelect(MaterialYou) }, Modifier.fillMaxWidth())
                }
                items(listOf(false, true), key = { if (it) "dark" else "light" }) { dark ->
                    SettingsExpandableSection(
                        title = stringResource(if (dark) R.string.appearance_dark_themes else R.string.appearance_light_themes),
                        initiallyExpanded = selectedTheme.isDark == dark,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            org.mobilism.material.data.Alphabetical.sorted(ThemeCatalog.forMode(dark, state.customThemes)) { it.name }.forEach { theme ->
                                ThemeOption(theme, state.theme.id == theme.id, { onSelect(theme) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(theme: ThemeDefinition, active: Boolean, onClick: () -> Unit, modifier: Modifier, chooser: Boolean = false) {
    val description = if (chooser) stringResource(R.string.appearance_choose_theme) else theme.name
    ExpressiveCard(onClick, modifier.semantics {
        contentDescription = description
        if (!chooser) selected = active
        role = if (chooser) Role.Button else Role.RadioButton
    }, colors = CardDefaults.cardColors(containerColor = theme.colors.surface, contentColor = theme.colors.onSurface),
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) theme.colors.primary else theme.colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (chooser) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(24.dp), tint = theme.colors.primary)
                else if (active) Icon(Icons.Outlined.Check, stringResource(R.string.state_selected), Modifier.size(24.dp), tint = theme.colors.primary)
                else Spacer(Modifier.size(20.dp))
            }
            if (theme.id == MaterialYou.id) SettingsSubtitle(stringResource(R.string.appearance_wallpaper_colors_summary),
                color = theme.colors.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(theme.colors.primary, theme.colors.secondary, theme.colors.tertiary,
                    theme.reading, theme.featured, theme.personalization, theme.success, theme.colors.error,
                    theme.colors.background, theme.colors.surface, theme.colors.surfaceContainerHigh, theme.colors.onSurface)
                    .distinct().dropLast(if (theme.id == CatppuccinFrappe.id) 1 else 0).forEach { color ->
                    Box(Modifier.weight(1f, fill = false).widthIn(max = 24.dp).aspectRatio(1f)
                        .clip(MaterialTheme.shapes.small).background(color)
                        .border(1.dp, theme.colors.outlineVariant, MaterialTheme.shapes.small))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontPicker(state: AppearanceState, store: AppearanceStore) {
    var expanded by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            try { store.importFont(uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IllegalArgumentException) { error = failure.message }
            catch (_: Exception) { error = resources.getString(R.string.appearance_font_load_error) }
            finally { importing = false }
        }
    }
    fun displayName(font: AppFont): String {
        if (font == AppFont.Custom) state.customFontName?.let { return it }
        return resources.getString(font.titleRes)
    }
    val options = org.mobilism.material.data.Alphabetical.sorted(
        AppFont.entries.filter { it != AppFont.Custom || state.customFontFile != null }, ::displayName)
    val chooseFont = stringResource(R.string.appearance_choose_font)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded, onExpandedChange = { if (!importing) expanded = it }) {
            OutlinedTextField(displayName(state.font), onValueChange = {}, readOnly = true, enabled = !importing,
                label = { Text(stringResource(if (importing) R.string.appearance_loading_font else R.string.appearance_typeface)) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamilyFor(state.font, state.customFontFile)),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .semantics { contentDescription = chooseFont })
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 420.dp), shape = MaterialTheme.shapes.large) {
                options.forEach { font ->
                    val active = state.font == font
                    DropdownMenuItem(
                        text = { Text(displayName(font),
                            fontFamily = fontFamilyFor(font, state.customFontFile), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { expanded = false; store.selectFont(font) },
                        trailingIcon = { if (active) Icon(Icons.Outlined.Check, stringResource(R.string.state_selected)) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .semantics { selected = active },
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                DropdownMenuItem(text = { Text(stringResource(R.string.appearance_use_custom_font)) },
                    leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                    onClick = { expanded = false; picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}
