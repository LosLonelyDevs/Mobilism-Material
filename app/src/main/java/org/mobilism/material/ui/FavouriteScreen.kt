package org.mobilism.material.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.mobilism.material.data.ContentSection
import org.mobilism.material.designsystem.ExpressiveButton
import org.mobilism.material.designsystem.ExpressiveCard

@Composable
fun FavouriteScreen(onChoose: (ContentSection) -> Unit) {
    var selection by rememberSaveable { mutableStateOf(ContentSection.Apps.name) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 480.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Favourite section", Modifier.padding(top = 48.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineLarge)
                ContentSection.entries.forEach { section ->
                    val chosen = section.name == selection
                    ExpressiveCard(
                        onClick = { selection = section.name },
                        modifier = Modifier.fillMaxWidth().semantics { selected = chosen; role = Role.RadioButton },
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RadioButton(selected = chosen, onClick = null)
                            Text(section.label, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                ExpressiveButton({ onChoose(ContentSection.valueOf(selection)) }, Modifier.fillMaxWidth()) { Text("Choose") }
            }
        }
    }
}
