package org.mobilism.material.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mobilism.material.data.ReleaseComment
import java.text.DateFormat
import java.util.Date

@Composable
fun ReleaseCommentCard(comment: ReleaseComment, baseUrl: String, onImage: (List<String>, Int) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (comment.avatar != null) AsyncImage(comment.avatar, null, Modifier.size(36.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                Column {
                    AppHeading(comment.author, HeadingLevel.Subsection, style = MaterialTheme.typography.titleSmall)
                    if (comment.publishedAt > 0) {
                        val date = remember(comment.publishedAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(comment.publishedAt * 1000)) }
                        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            comment.quotes.forEach { quote ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(quote.author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        ReleaseBody(quote.html, baseUrl)
                    }
                }
            }
            ReleaseBody(comment.contentHtml, baseUrl)
            if (comment.images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(comment.images) { index, url ->
                    AsyncImage(url, "Open comment image ${index + 1}", Modifier.size(120.dp).clip(MaterialTheme.shapes.large)
                        .clickable { onImage(comment.images, index) }, contentScale = ContentScale.Fit)
                }
            }
        }
    }
}
