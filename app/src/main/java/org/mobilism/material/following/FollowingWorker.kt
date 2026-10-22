package org.mobilism.material.following

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import org.mobilism.material.MobilismApplication
import java.util.concurrent.TimeUnit

class FollowingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as MobilismApplication
        if (!app.followPreferences.state.value.backgroundChecks) Result.success()
        else if (app.following.check().failed > 0) Result.retry() else Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.retry() }
}

object FollowingSchedule {
    const val NAME = "followed-app-updates"
    fun update(context: Context, enabled: Boolean, hours: Long) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<FollowingWorker>(hours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
