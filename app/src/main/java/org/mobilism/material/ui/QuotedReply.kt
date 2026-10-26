package org.mobilism.material.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jsoup.Jsoup
import org.mobilism.material.designsystem.AppTextButton

@Composable
internal fun QuotedReply(author: String, html: String, baseUrl: String,
    onRelease: ((Long) -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    var expanded by rememberSaveable(author, html) { mutableStateOf(false) }
    val preview = remember(html) { Jsoup.parse(html).text() }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (author.isBlank()) "Quoted message" else "Replying to $author",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            if (expanded) ReleaseBody(html, baseUrl, onRelease)
            else Text(preview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextButton(if (expanded) "Hide quote" else "Show quote", { expanded = !expanded })
            onRemove?.let { AppTextButton("Remove reply", it) }
        }
    }
}
