package org.mobilism.material.ui

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
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
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
        TopAppBar(title = { AppHeading("Appearance", HeadingLevel.Page) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) })
    }) { insets ->
        LazyColumn(Modifier.padding(insets), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { AppHeading("Theme", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall) }
            item {
                ThemeOption(state.theme, false, { showThemes = true }, Modifier.fillMaxWidth(), chooser = true)
            }
            item { AppHeading("Font", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall) }
            item { FontPicker(state, store) }
            item { AppHeading("Motion", HeadingLevel.Section, style = MaterialTheme.typography.headlineSmall) }
            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text("Reduce motion", style = MaterialTheme.typography.titleMedium)
                            Text("Limit animations.", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(state.reduceMotion, store::reduceMotion,
                            Modifier.semantics { contentDescription = "Reduce motion" })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePicker(state: AppearanceState, onDismiss: () -> Unit, onSelect: (ThemeDefinition) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppHeading("Choose theme", HeadingLevel.Section, Modifier.weight(1f))
                AppIconButton(Icons.Outlined.Close, "Close themes", onDismiss)
            }
            LazyColumn(Modifier.weight(1f).testTag("theme-options").selectableGroup(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf(false, true), key = { if (it) "dark" else "light" }) { dark ->
                    CollapsibleSection(
                        id = if (dark) "dark-themes" else "light-themes",
                        title = if (dark) "Dark" else "Light",
                        icon = if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        accent = MaterialTheme.colorScheme.primary,
                        initiallyExpanded = state.theme.isDark == dark,
                        headingLevel = HeadingLevel.Subsection,
                    ) {
                        org.mobilism.material.data.Alphabetical.sorted(ThemeCatalog.forMode(dark, state.customThemes)) { it.name }.forEach { theme ->
                            ThemeOption(theme, state.theme.id == theme.id, { onSelect(theme) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(theme: ThemeDefinition, active: Boolean, onClick: () -> Unit, modifier: Modifier, chooser: Boolean = false) {
    ExpressiveCard(onClick, modifier.semantics {
        contentDescription = if (chooser) "Choose theme" else theme.name
        if (!chooser) selected = active
        role = if (chooser) Role.Button else Role.RadioButton
    }, colors = CardDefaults.cardColors(containerColor = theme.colors.surface, contentColor = theme.colors.onSurface),
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) theme.colors.primary else theme.colors.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(theme.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (chooser) Icon(Icons.Outlined.ExpandMore, null, Modifier.size(24.dp), tint = theme.colors.primary)
                else if (active) Icon(Icons.Outlined.Check, "Selected", Modifier.size(24.dp), tint = theme.colors.primary)
                else Spacer(Modifier.size(20.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(theme.colors.primary, theme.colors.secondary, theme.colors.tertiary,
                    theme.reading, theme.featured, theme.personalization, theme.success, theme.colors.error,
                    theme.colors.background, theme.colors.surface, theme.colors.surfaceContainerHigh, theme.colors.onSurface)
                    .distinct().forEach { color ->
                    Box(Modifier.size(24.dp).clip(MaterialTheme.shapes.small).background(color)
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            try { store.importFont(uri) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: IllegalArgumentException) { error = failure.message }
            catch (_: Exception) { error = "Could not load that font." }
            finally { importing = false }
        }
    }
    val selectedName = if (state.font == AppFont.Custom) state.customFontName ?: "Custom font" else state.font.title
    val options = org.mobilism.material.data.Alphabetical.sorted(AppFont.entries.filter { it != AppFont.Custom || state.customFontFile != null }) {
        if (it == AppFont.Custom) state.customFontName ?: it.title else it.title
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded, onExpandedChange = { if (!importing) expanded = it }) {
            OutlinedTextField(selectedName, onValueChange = {}, readOnly = true, enabled = !importing,
                label = { Text(if (importing) "Loading font" else "Typeface") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamilyFor(state.font, state.customFontFile)),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .semantics { contentDescription = "Choose font" })
            ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 420.dp), shape = MaterialTheme.shapes.large) {
                options.forEach { font ->
                    val active = state.font == font
                    DropdownMenuItem(
                        text = { Text(if (font == AppFont.Custom) state.customFontName ?: font.title else font.title,
                            fontFamily = fontFamilyFor(font, state.customFontFile), style = MaterialTheme.typography.bodyLarge) },
                        onClick = { expanded = false; store.selectFont(font) },
                        trailingIcon = { if (active) Icon(Icons.Outlined.Check, "Selected") },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .semantics { selected = active },
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                DropdownMenuItem(text = { Text("Use custom font") },
                    leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                    onClick = { expanded = false; picker.launch(arrayOf("*/*")) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}
