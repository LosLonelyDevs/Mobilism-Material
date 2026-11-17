package org.mobilism.material.remote

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
import org.mobilism.material.data.AppNotice
import org.mobilism.material.data.DiagnosticLogger
import org.mobilism.material.data.DiagnosticEvent
import org.mobilism.material.data.DiagnosticSource
import org.mobilism.material.data.DiagnosticOperation
import org.mobilism.material.data.DiagnosticPhase

internal class RemoteNoticeNotifications(private val context: Context, private val diagnostics: DiagnosticLogger) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("remote-notification-delivery", Context.MODE_PRIVATE)
    private val delivery = NoticeNotificationDelivery(
        readSent = { preferences.getStringSet("sent", emptySet()).orEmpty().toSet() },
        saveSent = { preferences.edit().putStringSet("sent", it).commit() },
        post = ::post,
        retain = ::retain,
    )

    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "App notices", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notices and update reminders from Mobilism"
        })
    }

    fun sync(account: String?, notices: List<AppNotice>) = delivery.sync(account, notices)

    private fun allowed(): Boolean = (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun post(account: String, notice: AppNotice): Boolean {
        if (!allowed()) return false
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("mobilism-material://notices/$account/${Uri.encode(notice.id)}"))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app_update)
            .setContentTitle("App notice").setContentText("Open Mobilism to read it.").build()
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_app_update)
            .setContentTitle(notice.title).setContentText(notice.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS).setGroup("app-notices:$account")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        return try {
            manager.notify(tag(account, notice.id), 1, notification)
            diagnostics.record(DiagnosticEvent(DiagnosticSource.App, DiagnosticOperation.DeviceNotification,
                DiagnosticPhase.Posted, noticeKey = notice.dismissalKey))
            true
        }
        catch (_: SecurityException) { false }
    }

    private fun retain(account: String?, ids: Set<String>) {
        val keep = if (account == null) emptySet() else ids.map { tag(account, it) }.toSet()
        manager.activeNotifications.filter { it.tag?.startsWith(PREFIX) == true && it.tag !in keep }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun tag(account: String, id: String) = "$PREFIX$account:$id"

    companion object {
        const val CHANNEL = "app-notices"
        private const val PREFIX = "remote-notice:"
    }
}
