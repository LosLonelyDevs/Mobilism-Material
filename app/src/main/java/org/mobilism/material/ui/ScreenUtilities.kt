package org.mobilism.material.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mobilism.material.designsystem.AppTextButton

@Composable
fun ErrorPanel(error: ScreenError, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(error.message, style = MaterialTheme.typography.bodyMedium)
            onRetry?.let { AppTextButton("Try again", it) }
            if (error.showNetworkSettings) AppTextButton("Open app permissions", {
                launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            })
        }
    }
}

fun openWeb(context: Context, url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank()) return false
    return launch(context, Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
}

private fun launch(context: Context, intent: Intent): Boolean {
    return try { context.startActivity(intent); true }
    catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No app is available to open this link.", Toast.LENGTH_SHORT).show()
        false
    }
}
