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
import org.mobilism.material.ui.MobilismApp
import org.mobilism.material.following.FollowingNotifications

class MainActivity : ComponentActivity() {
    private var notificationRelease by mutableStateOf<Long?>(null)
    private var fromNotification by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRelease = (savedInstanceState?.getLong("pendingRelease")
            ?: releaseFrom(intent)).takeIf { it > 0 }
        fromNotification = savedInstanceState?.getBoolean("fromNotification") ?: (intent.action != Intent.ACTION_VIEW)
        enableEdgeToEdge()
        val app = application as MobilismApplication
        setContent {
            val appearance by app.appearance.state.collectAsStateWithLifecycle()
            val theme = appearance.theme
            SideEffect {
                val bars = if (theme.isDark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MobilismTheme(theme, appearance.font, appearance.reduceMotion, appearance.customFontFile) {
                MobilismApp(app, notificationRelease, fromNotification = fromNotification) {
                    notificationRelease = null
                    intent.removeExtra(FollowingNotifications.EXTRA_RELEASE)
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pendingRelease", notificationRelease ?: 0)
        outState.putBoolean("fromNotification", fromNotification)
        super.onSaveInstanceState(outState)
    }

    private fun releaseFrom(intent: Intent): Long =
        if (intent.action == Intent.ACTION_VIEW) ReleaseLinks.topicId(intent.dataString) ?: 0
        else intent.getLongExtra(FollowingNotifications.EXTRA_RELEASE, 0)
}
