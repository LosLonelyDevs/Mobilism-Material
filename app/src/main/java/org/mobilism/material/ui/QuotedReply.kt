package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jsoup.Jsoup
import org.mobilism.material.designsystem.AppTextButton

@Composable
internal fun QuotedReply(author: String, html: String, baseUrl: String,
    onRelease: ((Long) -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    val quote = remember(author, html) { QuotedComment(author, CommentContent.parse(html)) }
    QuotedReply(quote, baseUrl, onRelease, onRemove)
}

@Composable
internal fun QuotedReply(quote: QuotedComment, baseUrl: String, onRelease: ((Long) -> Unit)? = null,
    onRemove: (() -> Unit)? = null, depth: Int = 1) {
    var expanded by rememberSaveable(quote.author, quote.content.html) { mutableStateOf(false) }
    val bodyState = rememberSaveableStateHolder()
    val preview = remember(quote.content.html) { Jsoup.parse(quote.content.html).text() }
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.commentRail(depth).padding(start = if (depth <= 4) 12.dp else 4.dp, end = 4.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (quote.author.isBlank()) "Quoted message" else "Replying to ${quote.author}", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                AppTextButton(if (expanded) "Hide quote" else "Show quote", { expanded = !expanded })
            }
            if (expanded) {
                bodyState.SaveableStateProvider("quote") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        quote.content.quotes.forEach { QuotedReply(it, baseUrl, onRelease, depth = depth + 1) }
                        if (quote.content.html.isNotBlank()) ReleaseBody(quote.content.html, baseUrl, onRelease)
                    }
                }
            } else {
                if (preview.isNotBlank()) Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (quote.content.quotes.isNotEmpty()) Text("${quote.content.quotes.size} earlier quoted ${if (quote.content.quotes.size == 1) "reply" else "replies"}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            onRemove?.let { AppTextButton("Remove reply", it) }
        }
    }
}
