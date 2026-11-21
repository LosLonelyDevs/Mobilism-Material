package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.mobilism.material.ReleaseLinks
import org.mobilism.material.designsystem.LocalReducedMotion

internal val LocalCommentLinkHandler = staticCompositionLocalOf<((ReleaseLinks.Comment) -> Boolean)?> { null }

@Composable
internal fun CommentJumpList(topicId: Long?, tree: List<ThreadComment>, visible: List<ThreadComment>,
    collapsed: List<Long>, onExpand: (List<Long>) -> Unit, list: LazyListState, firstRow: () -> Int,
    modifier: Modifier, contentPadding: PaddingValues, content: LazyListScope.(highlight: Long?, followedLink: Boolean) -> Unit) {
    val openElsewhere = LocalCommentLinkHandler.current
    var target by remember(topicId) { mutableStateOf<Long?>(null) }
    var sequence by remember(topicId) { mutableIntStateOf(0) }
    var highlight by remember(topicId) { mutableStateOf<Long?>(null) }
    val reduced = LocalReducedMotion.current
    val row = target?.let { id -> visible.indexOfFirst { it.comment.id == id && !it.placeholder } } ?: -1
    LaunchedEffect(sequence, row) {
        val id = target ?: return@LaunchedEffect
        if (row < 0) return@LaunchedEffect
        withFrameNanos { }
        val index = (firstRow() + row).coerceAtLeast(0)
        if (reduced) list.scrollToItem(index) else list.animateScrollToItem(index)
        highlight = id
        delay(1600)
        highlight = null
        target = null
    }
    CompositionLocalProvider(LocalCommentLinkHandler provides { ref ->
        val path = if (ref.topicId == null || ref.topicId == topicId) commentPath(tree, ref.postId) else null
        if (path == null) openElsewhere?.invoke(ref) == true else {
            onExpand(collapsed.filterNot { it in path })
            highlight = null
            target = ref.postId
            sequence++
            true
        }
    }) {
        LazyColumn(state = list, modifier = modifier, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content(highlight, sequence != 0)
        }
    }
}

private fun commentPath(roots: List<ThreadComment>, id: Long): Set<Long>? {
    val parents = mutableMapOf<Long, Long>()
    val pending = ArrayDeque(roots)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        if (node.comment.id == id && !node.placeholder) return generateSequence(id) { parents[it] }.toSet()
        for (child in node.replies) {
            parents[child.comment.id] = node.comment.id
            pending.addLast(child)
        }
    }
    return null
}
