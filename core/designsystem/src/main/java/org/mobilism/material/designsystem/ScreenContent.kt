package org.mobilism.material.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun <T> AppScreenContent(state: T, key: (T) -> String, forward: Boolean, content: @Composable (T) -> Unit) {
    val motion = screenMotion()
    AnimatedContent(state, contentKey = key, transitionSpec = {
        ((if (forward) motion.enter else motion.popEnter) togetherWith
            (if (forward) motion.exit else motion.popExit)).using(null)
    }, label = "screen hierarchy") { content(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppChoiceTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    PrimaryScrollableTabRow(selectedTabIndex = selected, edgePadding = 16.dp, containerColor = MaterialTheme.colorScheme.background) {
        labels.forEachIndexed { index, label ->
            val interaction = remember { MutableInteractionSource() }
            Tab(selected = selected == index, onClick = { onSelect(index) }, text = { Text(label) },
                interactionSource = interaction, modifier = Modifier.heightIn(min = 48.dp).focusOutline(interaction))
        }
    }
}

@Composable
fun AppSearchField(value: String, onValueChange: (String) -> Unit, label: String, supportingText: String,
    onSearch: () -> Unit, searchIcon: ImageVector, submitIcon: ImageVector, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedTextField(value, onValueChange, modifier = modifier.fillMaxWidth().focusOutline(interaction),
        interactionSource = interaction, label = { Text(label) }, singleLine = true,
        shape = MaterialTheme.shapes.extraLarge, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        leadingIcon = { Icon(searchIcon, null) },
        trailingIcon = { AppIconButton(submitIcon, label, onSearch, enabled = value.isNotBlank()) },
        supportingText = { Text(supportingText) })
}
