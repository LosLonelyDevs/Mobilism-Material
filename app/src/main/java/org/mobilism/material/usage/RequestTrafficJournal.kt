package org.mobilism.material.usage

import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.mobilism.material.data.RequestTraffic
import org.mobilism.material.data.RequestTrafficProtocol

internal class RequestTrafficJournal(private val storage: UsageStorage, private val version: String, private val code: Int,
    private val newId: () -> String = { UUID.randomUUID().toString() }) {
    @Synchronized fun record(day: LocalDate, event: RequestTraffic) {
        if (event.endpoint !in RequestTrafficProtocol.endpoints || event.method !in RequestTrafficProtocol.methods || event.outcome !in 0..5) return
        prune(day)
        val key = "traffic-$day-$code"
        val existing = storage.get(key)
        if (existing == null && storage.keys().count { it.startsWith("traffic-") } >= 32) return
        val bucket = existing?.let(::JSONObject) ?: JSONObject().put("day", day.toString()).put("id", newId())
            .put("appVersion", version).put("versionCode", code).put("metrics", JSONArray())
        // A sealed day's ID always identifies the same totals, even if the device clock moves backward.
        if (bucket.optBoolean("sealed")) return
        val rows = bucket.getJSONArray("metrics")
        val row = (0 until rows.length()).map(rows::getJSONObject).firstOrNull {
            it.getString("endpoint") == event.endpoint && it.getString("method") == event.method
        } ?: run {
            if (rows.length() >= RequestTrafficProtocol.MAX_ROWS) return
            JSONObject().put("endpoint", event.endpoint).put("method", event.method)
                .put("counts", JSONArray(listOf(0, 0, 0, 0, 0, 0))).put("durationMs", 0).also(rows::put)
        }
        val counts = row.getJSONArray("counts")
        if ((0..5).sumOf { counts.getLong(it) } >= RequestTrafficProtocol.MAX_REQUESTS) return
        counts.put(event.outcome, counts.getLong(event.outcome) + 1)
        row.put("durationMs", row.getLong("durationMs") + event.durationMs.coerceIn(0, RequestTrafficProtocol.MAX_DURATION_MS))
        storage.put(key, bucket.toString())
    }

    @Synchronized fun closedDays(today: LocalDate): JSONArray {
        prune(today)
        val result = JSONArray()
        storage.keys().filter { it.startsWith("traffic-") }.sortedDescending().forEach { key ->
            if (result.length() >= RequestTrafficProtocol.MAX_DAYS) return@forEach
            val bucket = storage.get(key)?.let(::JSONObject) ?: return@forEach
            val day = LocalDate.parse(bucket.getString("day"))
            if (day >= today || day < today.minusDays(7)) return@forEach
            if (!bucket.optBoolean("sealed")) {
                bucket.put("sealed", true)
                if (!storage.put(key, bucket.toString())) return@forEach
            }
            result.put(JSONObject(bucket.toString()).apply { remove("sealed") })
        }
        return result
    }

    @Synchronized fun clear() = storage.remove(storage.keys().filter { it.startsWith("traffic-") })

    private fun prune(today: LocalDate) = storage.remove(storage.keys().filter { key ->
        key.startsWith("traffic-") && runCatching {
            LocalDate.parse(key.removePrefix("traffic-").take(10)).isBefore(today.minusDays(7))
        }.getOrDefault(true)
    })
}
