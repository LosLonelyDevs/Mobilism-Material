package org.mobilism.material.following

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.mobilism.material.MainActivity
import org.mobilism.material.R
import org.mobilism.material.data.LibraryEntry
import org.mobilism.material.data.Release
import org.mobilism.material.data.ReleaseTitles
import org.mobilism.material.data.ReleaseTitle

class FollowingNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "New versions of apps you follow"
    }) }

    fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun post(entry: LibraryEntry, release: Release, baseline: ReleaseTitle, installed: Boolean): Boolean {
        if (!allowed()) return false
        val title = ReleaseTitles.parse(release.title)
        val intent = Intent(context, MainActivity::class.java).setData(Uri.parse("mobilism-material://release/${release.id}"))
            .putExtra(EXTRA_RELEASE, release.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val message = (if (installed) "Installed" else "Following") + " v${baseline.version} · Available v${title.version}" +
            (title.build?.let { " (build $it)" } ?: "")
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app_update)
            .setContentTitle("${title.name}: update available").setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(pending)
            .setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_RECOMMENDATION).build()
        return try {
            manager.notify(tag(entry.release.id), 1, notification)
            true
        } catch (_: SecurityException) { false }
    }

    fun cancel(id: Long) = manager.cancel(tag(id), 1)
    fun retain(ids: Set<Long>) {
        manager.activeNotifications.filter { it.tag?.startsWith("following:") == true && it.tag !in ids.map(::tag) }
            .forEach { manager.cancel(it.tag, it.id) }
    }
    private fun tag(id: Long) = "following:$id"
    companion object {
        const val CHANNEL = "followed-app-updates"
        const val EXTRA_RELEASE = "following_release_id"
    }
}
