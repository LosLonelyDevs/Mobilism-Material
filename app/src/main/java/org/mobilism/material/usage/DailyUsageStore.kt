package org.mobilism.material.usage

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.mobilism.material.BuildConfig
import java.time.LocalDate
import java.util.UUID

class DailyUsageStore(context: Context) {
    private val preferences = context.getSharedPreferences("daily-usage", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled = mutableEnabled.asStateFlow()
    internal val journal = DailyUsageJournal(object : UsageStorage {
        override fun get(key: String): String? = preferences.getString(key, null)
        override fun put(key: String, value: String): Boolean = preferences.edit().putString(key, value).commit()
        override fun keys(): Set<String> = preferences.all.keys
        override fun remove(keys: List<String>) {
            if (keys.isNotEmpty()) preferences.edit().also { edit -> keys.forEach(edit::remove) }.apply()
        }
    })

    fun setEnabled(context: Context, enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
        mutableEnabled.value = enabled
        if (!enabled) androidx.work.WorkManager.getInstance(context).cancelAllWorkByTag(DailyUsageSchedule.TAG)
    }

    internal fun report(day: LocalDate): String? {
        return journal.prepare(day) {
            JSONObject().put("schema", 1).put("day", day.toString()).put("pingId", UUID.randomUUID().toString())
                .put("appVersion", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
                .put("androidApi", Build.VERSION.SDK_INT).put("manufacturer", Build.MANUFACTURER.take(80))
                .put("model", Build.MODEL.take(120)).toString()
        }
    }
}
