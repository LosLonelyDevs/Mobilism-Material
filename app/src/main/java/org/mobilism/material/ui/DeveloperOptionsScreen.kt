package org.mobilism.material.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mobilism.material.R
import androidx.compose.ui.Modifier
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.settings.DeveloperPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(state: DeveloperPreferences, onVerboseLogging: (Boolean) -> Unit,
    onDisable: () -> Unit, onBack: () -> Unit, onCustomCategories: (Boolean) -> Unit = {},
    onMarkdownSyntax: (Boolean) -> Unit = {}) {
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading(stringResource(R.string.settings_developer_options), HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
        })
    }) { insets ->
        SettingsList(Modifier.padding(insets)) {
            item { SettingsSectionLabel(stringResource(R.string.settings_preferences)) }
            item {
                SettingsToggleRow(stringResource(R.string.dev_mode), stringResource(R.string.dev_mode_summary),
                    null, state.enabled, onCheckedChange = { if (!it) onDisable() })
            }
            item {
                SettingsToggleRow(stringResource(R.string.dev_verbose_logging), stringResource(R.string.dev_verbose_logging_summary),
                    null, state.verboseLogging, onCheckedChange = onVerboseLogging)
            }
            item { SettingsSectionLabel(stringResource(R.string.settings_experiments)) }
            item {
                SettingsToggleRow(stringResource(R.string.dev_custom_categories), stringResource(R.string.dev_custom_categories_summary),
                    null, state.customCategoriesEnabled, onCheckedChange = onCustomCategories)
            }
            item {
                SettingsToggleRow(stringResource(R.string.dev_markdown), stringResource(R.string.dev_markdown_summary),
                    null, state.markdownSyntax, onCheckedChange = onMarkdownSyntax)
            }
        }
    }
}
