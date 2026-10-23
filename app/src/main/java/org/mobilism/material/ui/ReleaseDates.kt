package org.mobilism.material.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.designsystem.*

@Composable
private fun dateFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])

@Composable
fun ReleasePostDate(timestamp: Long, modifier: Modifier = Modifier) {
    if (timestamp <= 0) return
    val date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text("Posted ${date.format(dateFormatter())}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActiveFilterSummary(filters: FeedFilters, onEdit: () -> Unit, onClear: () -> Unit) {
    if (!filters.isActive) return
    val formatter = dateFormatter()
    val labels = buildList {
        if (filters.categoryIds.isNotEmpty()) add("${filters.categoryIds.size} categories")
        filters.postedFrom?.let { add("From ${it.format(formatter)}") }
        filters.postedThrough?.let { add("Through ${it.format(formatter)}") }
    }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { label ->
            ExpressiveButton(onEdit, compact = true, colors = ButtonDefaults.filledTonalButtonColors()) { Text(label) }
        }
        AppIconButton(Icons.Outlined.Close, "Clear filters", onClear)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostDateFilters(filters: FeedFilters, onChange: (FeedFilters) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    val formatter = dateFormatter()
    Column(Modifier.fillMaxWidth().then(if (LocalReducedMotion.current) Modifier else
        Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec())),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterSectionHeader("Post date", "Use either date or both. Selected days are included, in your time zone.")
        listOf("On or after" to filters.postedFrom, "On or before" to filters.postedThrough).forEach { (label, date) ->
            ExpressiveCard({ editing = label }, Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                containerColor = if (date == null) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(date?.format(formatter) ?: "Any date", style = MaterialTheme.typography.bodyLarge)
                    }
                    if (date != null) AppIconButton(Icons.Outlined.Close, "Clear $label", {
                        onChange(if (label == "On or after") filters.copy(postedFrom = null) else filters.copy(postedThrough = null))
                    })
                }
            }
        }
        if (!filters.validDates) Text("The end date must be on or after the start date.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    editing?.let { label ->
        val current = if (label == "On or after") filters.postedFrom else filters.postedThrough
        val picker = rememberDatePickerState(initialSelectedDateMillis = current?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { editing = null }, shape = MaterialTheme.shapes.extraLarge,
            confirmButton = {
                AppTextButton("Set date", {
                    val selected = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    onChange(if (label == "On or after") filters.copy(postedFrom = selected) else filters.copy(postedThrough = selected))
                    editing = null
                }, enabled = picker.selectedDateMillis != null)
            }, dismissButton = { AppTextButton("Cancel", { editing = null }) }) {
            DatePicker(picker, title = { Text(label, Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.verticalScroll(rememberScrollState()))
        }
    }
}
