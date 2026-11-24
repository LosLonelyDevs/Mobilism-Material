package org.mobilism.material.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.mobilism.material.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.settings.DeveloperPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperOptionsScreen(state: DeveloperPreferences, onVerboseLogging: (Boolean) -> Unit,
    onDisable: () -> Unit, onBack: () -> Unit,
    onMarkdownSyntax: (Boolean) -> Unit = {}, onManualNoticeChecks: (Boolean) -> Unit = {},
    onCheckNotices: suspend () -> Int? = { null }) {
    val scope = rememberCoroutineScope()
    var noticeStatus by remember(state.manualNoticeChecks) { mutableStateOf<Int?>(null) }
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
            item {
                SettingsToggleRow(stringResource(R.string.dev_manual_notice_checks), stringResource(R.string.dev_manual_notice_checks_summary),
                    null, state.manualNoticeChecks, onCheckedChange = onManualNoticeChecks)
            }
            if (state.enabled && state.manualNoticeChecks) item("check-notices") {
                SettingsRow(stringResource(R.string.updates_check_notices),
                    stringResource(noticeStatus ?: R.string.updates_check_notices_summary), null,
                    Modifier.clickable(enabled = noticeStatus != R.string.updates_notices_checking, role = Role.Button) {
                        noticeStatus = R.string.updates_notices_checking
                        scope.launch { noticeStatus = onCheckNotices() }
                    })
            }
            item { SettingsSectionLabel(stringResource(R.string.settings_experiments)) }
            item {
                SettingsToggleRow(stringResource(R.string.dev_markdown), stringResource(R.string.dev_markdown_summary),
                    null, state.markdownSyntax, onCheckedChange = onMarkdownSyntax)
            }
        }
    }
}
