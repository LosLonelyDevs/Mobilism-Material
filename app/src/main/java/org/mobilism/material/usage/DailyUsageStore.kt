package org.mobilism.material.usage

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.mobilism.material.BuildConfig
import java.time.LocalDate
import java.util.UUID
import java.time.ZoneOffset
import org.mobilism.material.data.RequestTraffic

class DailyUsageStore(context: Context) {
    private val preferences = context.getSharedPreferences("daily-usage", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = mutableEnabled.asStateFlow()
    private val storage = object : UsageStorage {
        override fun get(key: String): String? = preferences.getString(key, null)
        override fun put(key: String, value: String): Boolean = preferences.edit().putString(key, value).commit()
        override fun keys(): Set<String> = preferences.all.keys
        override fun remove(keys: List<String>) {
            if (keys.isNotEmpty()) preferences.edit().also { edit -> keys.forEach(edit::remove) }.apply()
        }
    }
    internal val journal = DailyUsageJournal(storage)
    private val traffic = RequestTrafficJournal(storage, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

    @Synchronized fun setEnabled(context: Context, enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableEnabled.value = enabled
        if (!enabled) {
            androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(DailyUsageSchedule.TAG)
            traffic.clear()
            storage.keys().filter { it.startsWith("report-") }.forEach { key ->
                runCatching { JSONObject(storage.get(key)!!).apply { remove("requestTraffic") } }
                    .getOrNull()?.let { storage.put(key, it.toString()) }
            }
        }
    }

    @Synchronized fun recordTraffic(event: RequestTraffic) {
        if (enabled.value) traffic.record(LocalDate.now(ZoneOffset.UTC), event)
    }

    internal fun storedReport(day: LocalDate): String? = storage.get("report-$day")

    @Synchronized internal fun report(day: LocalDate): String? {
        return journal.prepare(day) {
            JSONObject().put("schema", 1).put("day", day.toString()).put("pingId", UUID.randomUUID().toString())
                .put("appVersion", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
                .put("androidApi", Build.VERSION.SDK_INT).put("manufacturer", Build.MANUFACTURER.take(80))
                .put("model", Build.MODEL.take(120)).apply {
                    val completed = traffic.closedDays(day)
                    if (completed.length() > 0) put("requestTraffic", completed)
                }.toString()
        }
    }
}
