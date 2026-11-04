package org.mobilism.material.messages

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.mobilism.material.MobilismApplication
import org.mobilism.material.data.ApiFailure
import org.mobilism.material.data.AuthState
import org.mobilism.material.data.FailureKind

class MessageWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as MobilismApplication
        if (app.repository.auth.value == AuthState.Restoring) app.repository.restoreSession()
        app.messageChecker.check()
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: ApiFailure) {
        if (failure.kind in setOf(FailureKind.Credentials, FailureKind.Session)) Result.success() else Result.retry()
    } catch (_: Exception) { Result.retry() }
}

object MessageSchedule {
    const val NAME = "incoming-private-messages"
    fun update(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<MessageWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
