package org.mobilism.material.following

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.mobilism.material.MobilismApplication
import org.mobilism.material.data.BackgroundMobilismRead
import java.util.concurrent.TimeUnit

class FollowingWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as MobilismApplication
        if (!app.followPreferences.state.value.backgroundChecks) Result.success()
        else {
            app.remoteControl.refresh()
            withContext(BackgroundMobilismRead) { app.following.check() }
            Result.success()
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.success() }
}

object FollowingSchedule {
    const val NAME = "followed-app-updates"
    fun update(context: Context, enabled: Boolean, hours: Long) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<FollowingWorker>(maxOf(hours, FollowCheckPolicy.MIN_BACKGROUND_INTERVAL_HOURS), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInitialDelay(kotlin.random.Random.nextLong(15, 61), TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
