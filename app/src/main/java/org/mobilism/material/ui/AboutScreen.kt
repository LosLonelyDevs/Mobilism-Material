package org.mobilism.material.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.mobilism.material.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.mobilism.material.BuildConfig
import org.mobilism.material.designsystem.AppIconButton

internal const val REPOSITORY_URL = "https://github.com/LosLonelyDevs/Mobilism-Material"
private const val DEVELOPER_UNLOCK_TAPS = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, openRepository: ((String) -> Unit)? = null,
    developerMode: Boolean = false, onEnableDeveloperMode: () -> Unit = {}) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var iconTaps by rememberSaveable(developerMode) { mutableIntStateOf(0) }
    val feedback = remember { arrayOfNulls<android.widget.Toast>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { feedback[0]?.cancel() }
    }
    val icon = remember(context) {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap()
    }
    val notes = rememberChangelogNotes(BuildConfig.VERSION_NAME)
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading(stringResource(R.string.settings_about_title), HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), onBack)
        })
    }) { insets ->
        SettingsList(Modifier.padding(insets)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(icon, contentDescription = stringResource(R.string.about_icon), modifier = Modifier.size(80.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .clickable {
                            val message = if (developerMode) {
                                resources.getString(R.string.dev_already_enabled)
                            } else {
                                iconTaps++
                                if (iconTaps >= DEVELOPER_UNLOCK_TAPS) {
                                    iconTaps = 0
                                    onEnableDeveloperMode()
                                    resources.getString(R.string.dev_enabled)
                                } else if (iconTaps >= DEVELOPER_UNLOCK_TAPS - 2) {
                                    val remaining = DEVELOPER_UNLOCK_TAPS - iconTaps
                                    resources.getQuantityString(R.plurals.about_developer_taps, remaining, remaining)
                                } else null
                            }
                            if (message != null) {
                                feedback[0]?.cancel()
                                feedback[0] = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT)
                                feedback[0]?.show()
                            }
                        })
                    AppHeading(stringResource(R.string.app_name), HeadingLevel.Page,
                        style = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                        Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(BuildConfig.ABOUT_DESCRIPTION, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { SettingsSectionLabel(stringResource(R.string.about_whats_new)) }
            item {
                AppChangelogNotes(notes, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item { SettingsSectionLabel(stringResource(R.string.about_links)) }
            item {
                SettingsRow(stringResource(R.string.about_github), stringResource(R.string.about_github_summary), Icons.Outlined.Code,
                    Modifier.clickable(role = Role.Button) { (openRepository ?: uriHandler::openUri)(REPOSITORY_URL) }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                }
            }
        }
    }
}
