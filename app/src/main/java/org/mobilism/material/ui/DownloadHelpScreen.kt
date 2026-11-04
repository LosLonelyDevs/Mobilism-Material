package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import org.mobilism.material.data.DiscussionTopic
import org.mobilism.material.data.DownloadHelp
import org.mobilism.material.designsystem.AppIconButton
import org.mobilism.material.designsystem.LoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadHelpScreen(load: suspend () -> DiscussionTopic, onBack: () -> Unit, onRelease: (Long) -> Unit) {
    val context = LocalContext.current
    var topic by remember { mutableStateOf<DiscussionTopic?>(null) }
    var error by remember { mutableStateOf<ScreenError?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var gallery by remember { mutableStateOf<Int?>(null) }
    var verify by remember { mutableStateOf(false) }
    var canVerify by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        error = null
        try { topic = load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            error = failure.forScreen()
            canVerify = failure is org.mobilism.material.data.ApiFailure &&
                (failure.kind == org.mobilism.material.data.FailureKind.Challenge || failure.httpStatus == 403)
        }
    }
    if (verify) ForumAccessVerification(DownloadHelp.URL, onRead = { html ->
        runCatching { DownloadHelp.parse(html) }.getOrNull()?.let { topic = it; error = null; true } ?: false
    }, onDismiss = { verify = false })
    gallery?.let { index -> topic?.let { ReleaseImageViewer(it.images, index, onDismiss = { gallery = null }) } }
    Scaffold(topBar = {
        TopAppBar(title = { AppHeading("Download help", HeadingLevel.Page) },
            navigationIcon = { AppIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack) },
            actions = { AppIconButton(Icons.Outlined.OpenInBrowser, "Open in browser", { openForumBrowser(context, DownloadHelp.URL) }) })
    }) { insets ->
        LazyColumn(Modifier.padding(insets), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            error?.let { item { ErrorPanel(it, onRetry = { attempt++ }) } }
            if (error != null && canVerify) item {
                org.mobilism.material.designsystem.ExpressiveButton({ verify = true }) { androidx.compose.material3.Text("Verify forum access") }
            }
            if (topic == null && error == null) item { LoadingState("Loading download help…") }
            topic?.let { article ->
                item { AppHeading(article.title, HeadingLevel.Section) }
                if (article.images.isNotEmpty()) item { ReleaseImageCarousel(article.images) { gallery = it } }
                item { ReleaseBody(article.contentHtml, article.browserUrl, onRelease) }
            }
        }
    }
}
