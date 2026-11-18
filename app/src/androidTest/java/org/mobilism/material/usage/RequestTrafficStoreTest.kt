package org.mobilism.material.usage

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mobilism.material.BuildConfig
import org.mobilism.material.data.RequestTraffic

@RunWith(AndroidJUnit4::class)
class RequestTrafficStoreTest {
    @Test fun reportCarriesCompletedTrafficAndOptOutRemovesItFromQueuedPayload() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "traffic-test-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(ignored: String, mode: Int) = base.getSharedPreferences(name, mode)
        }
        val prefs = context.getSharedPreferences("daily-usage", Context.MODE_PRIVATE)
        val today = LocalDate.now(ZoneOffset.UTC)
        val yesterday = today.minusDays(1)
        try {
            val metric = JSONObject().put("endpoint", "api/search").put("method", "POST")
                .put("counts", JSONArray(listOf(7, 0, 0, 2, 0, 0))).put("durationMs", 900)
            val bucket = JSONObject().put("day", yesterday.toString()).put("id", UUID.randomUUID().toString())
                .put("appVersion", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE)
                .put("metrics", JSONArray().put(metric))
            prefs.edit().putString("traffic-$yesterday-${BuildConfig.VERSION_CODE}", bucket.toString()).commit()
            val store = DailyUsageStore(context)
            val first = JSONObject(store.report(today)!!)
            assertEquals(1, first.getJSONArray("requestTraffic").length())
            assertEquals(bucket.getString("id"), first.getJSONArray("requestTraffic").getJSONObject(0).getString("id"))
            assertEquals(first.toString(), DailyUsageStore(context).storedReport(today))
            store.setEnabled(context, false)
            store.recordTraffic(RequestTraffic("api/search", "POST", 0, 5))
            assertTrue(prefs.all.keys.none { it.startsWith("traffic-") })
            val queued = JSONObject(store.storedReport(today)!!)
            assertFalse(queued.has("requestTraffic"))
            assertEquals(first.getString("pingId"), queued.getString("pingId"))
            store.setEnabled(context, true)
            assertEquals(queued.toString(), store.report(today))
            store.recordTraffic(RequestTraffic("api/search", "POST", 0, 5))
            assertEquals(1, prefs.all.keys.count { it.startsWith("traffic-") })
        } finally {
            prefs.edit().clear().commit()
            base.deleteSharedPreferences(name)
        }
    }
}
