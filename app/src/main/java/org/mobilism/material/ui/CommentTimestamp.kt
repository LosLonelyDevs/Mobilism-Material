package org.mobilism.material.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.delay

internal data class CommentAge(val label: String, val nextChange: Instant)

internal fun commentAge(posted: Instant, now: Instant, zone: ZoneId): CommentAge {
    val today = now.atZone(zone).toLocalDate()
    val midnight = today.plusDays(1).atStartOfDay(zone).toInstant()
    val days = ChronoUnit.DAYS.between(posted.atZone(zone).toLocalDate(), today)
    if (days > 0) return CommentAge("${days}d", midnight)
    val hours = Duration.between(posted, now).toHours().coerceAtLeast(0)
    val nextHour = posted.plus(hours + 1, ChronoUnit.HOURS)
    return CommentAge(if (hours == 0L) "<1h" else "${hours}h", minOf(nextHour, midnight))
}

@Composable
internal fun CommentTimestamp(epochSeconds: Long) {
    val posted = remember(epochSeconds) { Instant.ofEpochSecond(epochSeconds) }
    val zone = ZoneId.systemDefault()
    val label by produceState(commentAge(posted, Instant.now(), zone).label, posted, zone) {
        while (true) {
            val now = Instant.now()
            val age = commentAge(posted, now, zone)
            value = age.label
            delay(Duration.between(now, age.nextChange).toMillis().coerceAtLeast(1))
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val fullDate = remember(posted, zone, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date.from(posted))
    }
    Text("· $label", Modifier.clearAndSetSemantics { contentDescription = fullDate }, maxLines = 1, softWrap = false,
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
