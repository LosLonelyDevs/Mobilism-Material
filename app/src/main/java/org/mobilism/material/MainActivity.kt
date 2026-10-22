package org.mobilism.material

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.mobilism.material.designsystem.MobilismTheme
import org.mobilism.material.ui.MobilismApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                MobilismApp(app)
            }
        }
    }
}
