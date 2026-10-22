package org.mobilism.material.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import org.mobilism.material.designsystem.LocalAppTheme

enum class HeadingLevel { Page, Section, Subsection }

/** Hierarchy determines heading color everywhere; feature accents belong to controls and surfaces. */
@Composable
fun AppHeading(
    text: String, level: HeadingLevel, modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(text, modifier.semantics { heading() }, style = style, maxLines = maxLines, overflow = overflow,
        color = when (level) {
            HeadingLevel.Page -> MaterialTheme.colorScheme.primary
            HeadingLevel.Section -> MaterialTheme.colorScheme.secondary
            HeadingLevel.Subsection -> LocalAppTheme.current.reading
        })
}
