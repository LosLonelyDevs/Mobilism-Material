package org.mobilism.material.updates

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import org.mobilism.material.R
import org.mobilism.material.data.AppRelease

internal class AppUpdateNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Mobilism Material updates",
            NotificationManager.IMPORTANCE_DEFAULT).apply { description = "New Mobilism Material releases on GitHub" })
    }

    fun permissionNeeded(): Boolean = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun allowed(): Boolean = !permissionNeeded() && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun post(release: AppRelease): Boolean {
        if (!allowed()) return false
        val intent = Intent(Intent.ACTION_VIEW, release.pageUrl.toUri())
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app_update)
            .setContentTitle("Mobilism Material ${release.version} is available")
            .setContentText("Tap to view the release and download the update.")
            .setContentIntent(pending).setAutoCancel(true).build()
        return try { manager.notify(TAG, 1, notification); true } catch (_: SecurityException) { false }
    }

    fun cancel() = manager.cancel(TAG, 1)
    companion object {
        const val CHANNEL = "mobilism-material-updates"
        const val TAG = "mobilism-material-update"
    }
}
