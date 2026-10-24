package org.mobilism.material.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import org.mobilism.material.BuildConfig
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.ExpressiveCard

internal const val REPOSITORY_URL = "https://github.com/LosLonelyDevs/Mobilism-Material"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, openRepository: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val icon = remember(context) {
        context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap()
    }
    val notes = remember { BuildConfig.RELEASE_NOTES.lines().filter(String::isNotBlank) }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("About", HeadingLevel.Page) }, navigationIcon = {
            AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
        })
    }) { insets ->
        LazyColumn(Modifier.padding(insets), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(112.dp))
                    AppHeading("Mobilism Material", HeadingLevel.Page,
                        style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center))
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                        Text("v${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(BuildConfig.ABOUT_DESCRIPTION, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppHeading("What's new", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                        notes.forEach { note ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (note.startsWith("- ")) Text("•", color = MaterialTheme.colorScheme.primary)
                                Text(note.removePrefix("- "), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
            item {
                ExpressiveCard(onClick = { (openRepository ?: uriHandler::openUri)(REPOSITORY_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppHeading("GitHub", HeadingLevel.Section, style = MaterialTheme.typography.titleLarge)
                            Text("Source code, releases, and issues", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
