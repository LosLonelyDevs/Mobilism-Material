package org.mobilism.material.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import org.json.JSONException
import org.mobilism.material.R
import org.mobilism.material.data.AppReleaseUpdates
import org.mobilism.material.updates.AppChangelog

@Composable
internal fun AppChangelogDialog(entry: AppChangelog, onDismiss: () -> Unit) {
    AppUpdateModal(stringResource(R.string.about_whats_new), entry.version, Icons.Outlined.NewReleases,
        stringResource(R.string.changelog_got_it), Icons.Outlined.Check,
        onConfirm = onDismiss, onDismiss = onDismiss) {
        AppChangelogNotes(rememberChangelogNotes(entry.version))
    }
}

internal val LocalChangelogLoader = staticCompositionLocalOf<suspend (String) -> List<String>> { AppReleaseUpdates()::notesFor }

@Composable
internal fun rememberChangelogNotes(version: String): List<String>? {
    val load = LocalChangelogLoader.current
    val fallback = stringResource(R.string.changelog_fallback)
    val notes by produceState<List<String>?>(null, version, fallback, load) {
        val published = try {
            load(version)
        } catch (_: IOException) {
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        }
        value = published.ifEmpty { listOf(fallback) }
    }
    return notes
}

@Composable
internal fun AppChangelogNotes(lines: List<String>?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lines == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else lines.forEach { bullet ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.padding(top = 8.dp).size(7.dp), shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary) { }
                Text(bullet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
