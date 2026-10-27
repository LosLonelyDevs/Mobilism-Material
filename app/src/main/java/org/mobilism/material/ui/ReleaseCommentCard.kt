package org.mobilism.material.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import org.mobilism.material.designsystem.AppSurface
import org.mobilism.material.designsystem.LocalReducedMotion
import org.mobilism.material.designsystem.focusOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mobilism.material.data.ReleaseComment
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReleaseCommentCard(comment: ReleaseComment, baseUrl: String, onRelease: ((Long) -> Unit)? = null,
    onReply: (() -> Unit)? = null, replyEnabled: Boolean = true,
    onImage: (List<String>, Int) -> Unit) {
    var expanded by rememberSaveable(baseUrl, comment.id) { mutableStateOf(true) }
    val bodyState = rememberSaveableStateHolder()
    val interaction = remember { MutableInteractionSource() }
    val reduced = LocalReducedMotion.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(), label = "comment chevron")
    AppSurface(Modifier.fillMaxWidth().testTag("comment-${comment.id}"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("comment-toggle-${comment.id}")
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .focusOutline(interaction).clickable(interactionSource = interaction, indication = ripple(), role = Role.Button,
                    onClickLabel = if (expanded) "Collapse comment" else "Expand comment", onClick = { expanded = !expanded })
                .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (comment.avatar != null) AsyncImage(comment.avatar, null, Modifier.size(36.dp).clip(MaterialTheme.shapes.large), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    AppHeading(comment.author, HeadingLevel.Subsection, style = MaterialTheme.typography.titleSmall)
                    if (comment.publishedAt > 0) {
                        val date = remember(comment.publishedAt) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(comment.publishedAt * 1000)) }
                        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(24.dp).rotate(if (reduced) if (expanded) 180f else 0f else rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(expanded,
                enter = if (reduced) EnterTransition.None else expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = if (reduced) ExitTransition.None else shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec())) {
                bodyState.SaveableStateProvider("body") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp).testTag("comment-body-${comment.id}"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        comment.quotes.forEach { quote ->
                            QuotedReply(quote.author, quote.html, baseUrl, onRelease)
                        }
                        ReleaseBody(comment.contentHtml, baseUrl, onRelease)
                        if (comment.images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(comment.images) { index, url ->
                                AsyncImage(url, "Open comment image ${index + 1}", Modifier.size(120.dp).clip(MaterialTheme.shapes.large)
                                    .clickable { onImage(comment.images, index) }, contentScale = ContentScale.Fit)
                            }
                        }
                        onReply?.let { org.mobilism.material.designsystem.AppTextButton("Reply", it, enabled = replyEnabled) }
                    }
                }
            }
        }
    }
}
