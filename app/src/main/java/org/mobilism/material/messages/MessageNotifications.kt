package org.mobilism.material.messages

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
import org.mobilism.material.data.PrivateMessage

data class MessageNotificationTarget(val accountKey: String, val conversationId: Long, val messageId: Long)

class MessageNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "New private messages"
        })
    }

    fun permissionGranted(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun allowed(): Boolean = permissionGranted() && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun post(username: String, message: PrivateMessage): Boolean {
        if (!allowed()) return false
        val key = MessageAlertStore.accountKey(username)
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("mobilism-material://messages/$key/${message.messageId}"))
            .putExtra(EXTRA_ACCOUNT, key).putExtra(EXTRA_CONVERSATION, message.id).putExtra(EXTRA_MESSAGE, message.messageId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_message)
            .setContentTitle("New message").setContentText("Open Mobilism to read it.").build()
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_message)
            .setContentTitle(message.from.ifBlank { "New message" }).setContentText(message.subject)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(message.subject, message.preview).filter { it.isNotBlank() }.joinToString("\n")))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setGroup("messages:$key")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
        return try { manager.notify(tag(key, message.messageId), 1, notification); true }
        catch (_: SecurityException) { false }
    }

    fun cancelRead(username: String, ids: Set<Long>) {
        val key = MessageAlertStore.accountKey(username)
        ids.forEach { manager.cancel(tag(key, it), 1) }
    }

    fun retainAccount(username: String?) {
        val prefix = username?.let { "messages:${MessageAlertStore.accountKey(it)}:" }
        manager.activeNotifications.filter { it.tag?.startsWith("messages:") == true && (prefix == null || !it.tag.startsWith(prefix)) }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    private fun tag(key: String, id: Long) = "messages:$key:$id"

    companion object {
        const val CHANNEL = "private-messages"
        const val EXTRA_ACCOUNT = "message_account"
        const val EXTRA_CONVERSATION = "message_conversation"
        const val EXTRA_MESSAGE = "message_id"
        fun target(intent: Intent): MessageNotificationTarget? {
            val account = intent.getStringExtra(EXTRA_ACCOUNT)?.takeIf { it.matches(Regex("[a-f0-9]{64}")) } ?: return null
            val conversation = intent.getLongExtra(EXTRA_CONVERSATION, 0).takeIf { it > 0 } ?: return null
            val message = intent.getLongExtra(EXTRA_MESSAGE, 0).takeIf { it > 0 } ?: return null
            return MessageNotificationTarget(account, conversation, message)
        }
    }
}
