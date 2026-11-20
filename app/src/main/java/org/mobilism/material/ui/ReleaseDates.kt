package org.mobilism.material.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import org.mobilism.material.data.Alphabetical
import org.mobilism.material.data.FeedFilters
import org.mobilism.material.data.ReleaseCategory
import org.mobilism.material.designsystem.*

@Composable
private fun dateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}

@Composable
fun ReleasePostDate(timestamp: Long, modifier: Modifier = Modifier, textStyle: TextStyle = MaterialTheme.typography.bodySmall) {
    if (timestamp <= 0) return
    val instant = Instant.ofEpochSecond(timestamp)
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate().format(dateFormatter())
    val context = LocalContext.current
    val timeFormat = remember(context, LocalConfiguration.current.locales[0], zone, DateFormat.is24HourFormat(context)) {
        DateFormat.getTimeFormat(context)
    }
    val time = timeFormat.format(Date.from(instant))
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text("$date · $time", style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActiveFilterSummary(filters: FeedFilters, onEdit: () -> Unit, onClear: () -> Unit,
    availableCategories: List<ReleaseCategory> = emptyList(), showSearchSort: Boolean = false) {
    if (!filters.isActive && !showSearchSort) return
    val formatter = dateFormatter()
    val forum = LocalForumCategoryNames.current
    val custom = LocalReleaseAssignments.current?.categories.orEmpty()
    val labels = buildList {
        if (showSearchSort) add(filters.searchSort.label)
        val selectedCustom = custom.filter { it.id in filters.customCategoryIds }
        val names = filters.categoryIds.map { id ->
            forum[id] ?: availableCategories.firstOrNull { it.id == id }?.name ?: "Category #$id"
        } + selectedCustom.map { it.name }
        addAll(Alphabetical.sorted(names.distinctBy { it.lowercase(Locale.ROOT) }) { it })
        val missing = filters.customCategoryIds.size - selectedCustom.size
        if (missing > 0) add(if (missing == 1) "Custom category" else "$missing custom categories")
        org.mobilism.material.data.BookFormat.entries.filter { it in filters.bookFormats }.forEach { add(it.label) }
        filters.postedFrom?.let { add("From ${it.format(formatter)}") }
        filters.postedThrough?.let { add("Through ${it.format(formatter)}") }
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically) {
            val style = MaterialTheme.typography.labelLarge
            labels.forEach { label ->
                ExpressiveButton(onEdit, modifier = Modifier.padding(horizontal = 4.dp), compact = true, minHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()) {
                    Text(label, style = style, fontSize = (style.fontSize.value - 1).sp, maxLines = 1, softWrap = false)
                }
            }
        }
        AppIconButton(Icons.Outlined.Close, "Clear filters", onClear)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostDateFilters(filters: FeedFilters, onChange: (FeedFilters) -> Unit) {
    var editing by remember { mutableStateOf<String?>(null) }
    val formatter = dateFormatter()
    CollapsibleSection("post-date-filter", "Post date", Icons.Outlined.CalendarMonth,
        MaterialTheme.colorScheme.tertiary, initiallyExpanded = filters.hasDates) {
        Text("Use either date or both. Selected days are included, in your time zone.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
