package org.mobilism.material.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobilism.material.MobilismApplication
import org.mobilism.material.designsystem.AppTextButton
import org.mobilism.material.messages.MessageNotifications

@Composable
private fun rememberMessageNotificationPermission(app: MobilismApplication, onChanged: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val currentChanged by rememberUpdatedState(onChanged)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentChanged() }
    return {
        val canRequest = !app.messageAlerts.permissionRequested || (context as? Activity)?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } == true
        if (Build.VERSION.SDK_INT >= 33 && !app.messageNotifications.permissionGranted() && canRequest) {
            app.messageAlerts.permissionRequested = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, MessageNotifications.CHANNEL))
        }
    }
}

@Composable
internal fun MessageNotificationPermissionPrompt(app: MobilismApplication) {
    val request = rememberMessageNotificationPermission(app)
    LaunchedEffect(Unit) {
        if (app.messageAlerts.enabled.value && !app.messageAlerts.permissionRequested && !app.messageNotifications.permissionGranted()) request()
    }
}

@Composable
internal fun MessageNotificationSetting(app: MobilismApplication) {
    val enabled by app.messageAlerts.enabled.collectAsStateWithLifecycle()
    var allowed by remember { mutableStateOf(app.messageNotifications.allowed()) }
    val request = rememberMessageNotificationPermission(app) { allowed = app.messageNotifications.allowed() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = app.messageNotifications.allowed() }
    SettingsToggleRow("Message notifications", "Periodic alerts for new private messages.", Icons.Outlined.Notifications,
        enabled, { value ->
            app.messageAlerts.setEnabled(value)
            if (value && !allowed) request()
        })
    if (enabled && !allowed) AppTextButton("Allow notifications", request)
}
