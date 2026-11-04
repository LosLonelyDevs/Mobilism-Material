package org.mobilism.material.usage

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mobilism.material.BuildConfig
import org.mobilism.material.MobilismApplication
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class DailyUsageWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = (applicationContext as MobilismApplication).dailyUsage
        if (!DailyUsageSchedule.configured || !store.enabled.value) return@withContext Result.success()
        val body = inputData.getString("report") ?: return@withContext Result.failure()
        try {
            val report = JSONObject(body)
            val day = LocalDate.parse(report.getString("day"))
            if (!reportDayIsValid(day, LocalDate.now(ZoneOffset.UTC)) || store.journal.wasSent(day)) {
                return@withContext Result.success()
            }
            val request = Request.Builder().url(BuildConfig.DAILY_USAGE_ENDPOINT)
                .header("User-Agent", "Mobilism-Usage/1")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val source = response.body?.source() ?: return@withContext Result.retry()
                    source.request(4097)
                    if (source.buffer.size > 4096) return@withContext Result.failure()
                    val receipt = JSONObject(source.readUtf8())
                    if (receipt.optBoolean("accepted") && receipt.optString("pingId") == report.getString("pingId") &&
                        receipt.optString("day") == day.toString()) {
                        if (store.journal.markSent(day)) Result.success() else Result.retry()
                    } else Result.retry()
                } else if (response.code in listOf(408, 429) || response.code >= 500) Result.retry()
                else Result.failure()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }

    private companion object {
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .callTimeout(20, TimeUnit.SECONDS).build()
    }
}

object DailyUsageSchedule {
    const val TAG = "daily-anonymous-usage"
    val configured: Boolean get() = !BuildConfig.DEBUG && BuildConfig.DAILY_USAGE_ENDPOINT.isNotBlank()

    suspend fun recordForegroundUse(context: Context) = withContext(Dispatchers.IO) {
        val store = (context.applicationContext as MobilismApplication).dailyUsage
        if (!configured || !store.enabled.value) return@withContext
        val day = LocalDate.now(ZoneOffset.UTC)
        val report = store.report(day) ?: return@withContext
        val work = OneTimeWorkRequestBuilder<DailyUsageWorker>()
            .setInputData(workDataOf("report" to report)).addTag(TAG)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG-$day", ExistingWorkPolicy.KEEP, work)
    }
}
