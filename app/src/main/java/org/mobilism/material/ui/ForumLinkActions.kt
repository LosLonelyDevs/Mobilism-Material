package org.mobilism.material.ui

import android.content.ClipData
import android.text.Spanned
import android.text.style.URLSpan
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Consume link gestures before TextView starts its native selection action mode. */
internal class ForumLinkTouchListener(private val view: TextView, private val onHold: (String) -> Unit) : View.OnTouchListener {
    private var pressed: URLSpan? = null
    private var tracking = false
    private var held = false
    private var downX = 0f
    private var downY = 0f
    private val slop = ViewConfiguration.get(view.context).scaledTouchSlop
    private val hold = Runnable {
        pressed?.let {
            held = true
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onHold(it.url)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view.removeCallbacks(hold)
                pressed = linkAt(event.x, event.y)
                tracking = pressed != null
                held = false
                downX = event.x
                downY = event.y
                if (tracking) view.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                view.removeCallbacks(hold)
                pressed = null
            }
            MotionEvent.ACTION_UP -> {
                val consumed = tracking
                view.removeCallbacks(hold)
                if (!held && pressed != null && pressed === linkAt(event.x, event.y)) {
                    v.performClick()
                    pressed?.onClick(view)
                }
                pressed = null
                tracking = false
                return consumed
            }
            MotionEvent.ACTION_CANCEL -> {
                val consumed = tracking
                view.removeCallbacks(hold)
                pressed = null
                tracking = false
                return consumed
            }
        }
        return tracking
    }

    private fun linkAt(x: Float, y: Float): URLSpan? {
        val text = view.text as? Spanned ?: return null
        val layout = view.layout ?: return null
        val localX = x - view.totalPaddingLeft + view.scrollX
        val localY = y - view.totalPaddingTop + view.scrollY
        if (localY < 0 || localY >= layout.height) return null
        val line = layout.getLineForVertical(localY.toInt())
        if (localX < layout.getLineLeft(line) || localX > layout.getLineRight(line)) return null
        val offset = layout.getOffsetForHorizontal(line, localX)
        return text.getSpans(offset, offset, URLSpan::class.java).firstOrNull {
            offset >= text.getSpanStart(it) && offset < text.getSpanEnd(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForumLinkActions(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Link options", Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge)
            Text(url, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), maxLines = 3, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            ListItem(headlineContent = { Text("Copy to clipboard") },
                leadingContent = { Icon(Icons.Outlined.ContentCopy, null, tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("URL", url)))
                        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                })
            ListItem(headlineContent = { Text("Open in browser") },
                leadingContent = { Icon(Icons.Outlined.OpenInBrowser, null, tint = MaterialTheme.colorScheme.tertiary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onDismiss(); openForumBrowser(context, url) })
        }
    }
}
