package org.mobilism.material.remote

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import org.mobilism.material.MobilismApplication
import org.mobilism.material.data.AuthState

class RemoteNoticeWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as MobilismApplication
        if (app.repository.auth.value == AuthState.Restoring) app.repository.restoreSession()
        val auth = app.repository.auth.value
        app.remoteControl.accountChanged((auth as? AuthState.SignedIn)?.username, known = auth != AuthState.Restoring)
        app.remoteControl.refresh(force = true)
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { Result.success() }
}

internal object RemoteNoticeSchedule {
    fun start(context: Context) {
        val request = PeriodicWorkRequestBuilder<RemoteNoticeWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("remote-app-notices", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
