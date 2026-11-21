package org.mobilism.material.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mobilism.material.data.LinkedCommentPage
import org.mobilism.material.designsystem.*

internal class LinkedCommentViewModel(private val fetch: suspend () -> LinkedCommentPage) : ViewModel() {
    var page by mutableStateOf<LinkedCommentPage?>(null)
        private set
    var error by mutableStateOf<ScreenError?>(null)
        private set
    private var job: Job? = null
    init { load() }

    fun load() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            error = null
            try { page = fetch() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.forScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LinkedCommentScreen(model: LinkedCommentViewModel, postId: Long, onBack: () -> Unit, onTopic: (Long) -> Unit) {
    val page = model.page
    var gallery by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    gallery?.let { (urls, index) -> ReleaseImageViewer(urls, index, onDismiss = { gallery = null }) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(page?.title?.ifBlank { "Comment" } ?: "Comment", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
            actions = { if (page != null) AppIconButton(Icons.AutoMirrored.Outlined.ArrowForward, "Open full topic", { onTopic(page.topicId) }) })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val error = model.error
            if (error != null) ErrorPanel(error, Modifier.padding(16.dp), onRetry = model::load)
            else if (page == null) LoadingState("Opening comment…", Modifier.align(Alignment.Center))
            else {
                val list = rememberLazyListState()
                val reduced = LocalReducedMotion.current
                val fallback = LocalCommentLinkHandler.current
                var target by rememberSaveable { mutableLongStateOf(postId) }
                var moved by rememberSaveable { mutableStateOf(false) }
                var highlighted by remember { mutableStateOf<Long?>(null) }
                var sequence by remember { mutableIntStateOf(0) }
                LaunchedEffect(page, sequence) {
                    if (moved) return@LaunchedEffect
                    val index = page.posts.indexOfFirst { it.id == target }
                    if (index < 0) return@LaunchedEffect
                    if (reduced) list.scrollToItem(index) else list.animateScrollToItem(index)
                    highlighted = target
                    moved = true
                    delay(1600)
                    highlighted = null
                }
                CompositionLocalProvider(LocalCommentLinkHandler provides { ref ->
                    if ((ref.topicId == null || ref.topicId == page.topicId) && page.posts.any { it.id == ref.postId }) {
                        target = ref.postId
                        highlighted = null
                        moved = false
                        sequence++
                        true
                    } else fallback?.invoke(ref) == true
                }) {
                    LazyColumn(state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(page.posts, key = { it.id }) { post ->
                            ReleaseCommentCard(post, "https://forum.mobilism.org/viewtopic.php?t=${page.topicId}",
                                highlighted = highlighted == post.id, onImage = { urls, index -> gallery = urls to index })
                        }
                    }
                }
            }
        }
    }
}
