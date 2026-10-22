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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRelease = (savedInstanceState?.getLong("pendingRelease")
            ?: intent.getLongExtra(FollowingNotifications.EXTRA_RELEASE, 0)).takeIf { it > 0 }
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
            MobilismTheme(theme, appearance.font, appearance.reduceMotion) {
                MobilismApp(app, notificationRelease) {
                    notificationRelease = null
                    intent.removeExtra(FollowingNotifications.EXTRA_RELEASE)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRelease = intent.getLongExtra(FollowingNotifications.EXTRA_RELEASE, 0).takeIf { it > 0 }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("pendingRelease", notificationRelease ?: 0)
        super.onSaveInstanceState(outState)
    }
}
