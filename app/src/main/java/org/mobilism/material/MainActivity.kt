package org.mobilism.material

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.designsystem.resolveAppTheme
import org.mobilism.material.ui.MobilismApp
import org.mobilism.material.following.FollowingNotifications
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.mobilism.material.usage.DailyUsageSchedule
import org.mobilism.material.messages.MessageNotificationTarget
import org.mobilism.material.messages.MessageNotifications
import org.mobilism.material.data.AuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine

class MainActivity : ComponentActivity() {
    private var notificationRelease by mutableStateOf<Long?>(null)
    private var notificationMessage by mutableStateOf<MessageNotificationTarget?>(null)
    private var fromNotification by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRelease = (savedInstanceState?.getLong("pendingRelease")
            ?: releaseFrom(intent)).takeIf { it > 0 }
        fromNotification = savedInstanceState?.getBoolean("fromNotification") ?: (intent.action != Intent.ACTION_VIEW)
        notificationMessage = savedInstanceState?.getString("pendingMessageAccount")?.let {
            MessageNotificationTarget(it, savedInstanceState.getLong("pendingMessageConversation"), savedInstanceState.getLong("pendingMessageId"))
        } ?: MessageNotifications.target(intent)
        enableEdgeToEdge()
        val app = application as MobilismApplication
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.repository.auth, app.messageAlerts.enabled) { auth, enabled -> auth to enabled }
                    .collectLatest { (auth, enabled) ->
                        if (auth is AuthState.SignedIn && enabled) while (true) {
                            try { app.messageChecker.check() }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* Retry transient failures on the next poll. */ }
                            delay(60_000)
                        }
                    }
            }
        }
        if (DailyUsageSchedule.configured) lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.dailyUsage.enabled.collectLatest { enabled ->
                    if (enabled) while (true) {
                        DailyUsageSchedule.recordForegroundUse(app)
                        val now = Instant.now()
                        val tomorrow = now.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
                        delay(Duration.between(now, tomorrow).toMillis().coerceAtLeast(1_000))
                    }
                }
            }
        }
        app.checkAppUpdatesOnColdStart()
        setContent {
            val appearance by app.appearance.state.collectAsStateWithLifecycle()
            val theme = resolveAppTheme(appearance.theme)
            SideEffect {
                val bars = if (theme.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MobilismTheme(theme, appearance.font, appearance.reduceMotion, appearance.customFontFile) {
                MobilismApp(app, notificationRelease, fromNotification = fromNotification, notificationMessage = notificationMessage) {
                    notificationRelease = null
                    notificationMessage = null
                    intent.removeExtra(FollowingNotifications.EXTRA_RELEASE)
                    intent.removeExtra(MessageNotifications.EXTRA_ACCOUNT)
                    intent.removeExtra(MessageNotifications.EXTRA_CONVERSATION)
                    intent.removeExtra(MessageNotifications.EXTRA_MESSAGE)
                    intent.data = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        fromNotification = intent.action != Intent.ACTION_VIEW
        notificationRelease = releaseFrom(intent).takeIf { it > 0 }
        notificationMessage = MessageNotifications.target(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pendingRelease", notificationRelease ?: 0)
        outState.putBoolean("fromNotification", fromNotification)
        notificationMessage?.let {
            outState.putString("pendingMessageAccount", it.accountKey)
            outState.putLong("pendingMessageConversation", it.conversationId)
            outState.putLong("pendingMessageId", it.messageId)
        }
        super.onSaveInstanceState(outState)
    }

    private fun releaseFrom(intent: Intent): Long =
        if (intent.action == Intent.ACTION_VIEW) ReleaseLinks.topicId(intent.dataString) ?: 0
        else intent.getLongExtra(FollowingNotifications.EXTRA_RELEASE, 0)
}
