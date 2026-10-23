package com.shamil.launcher

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shamil.launcher.ui.HomeScreen
import com.shamil.launcher.ui.HomeState
import com.shamil.launcher.ui.theme.LauncherTheme
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.animatedLook
import com.shamil.launcher.ui.widgets.SeelieWidgetHost
import com.shamil.launcher.apps.VaultStore

class MainActivity : ComponentActivity() {
    /** What the home screen shows (favourites / list / search). Lives here so pressing Home can reset it. */
    private val home = HomeState()
    private var isDefault by mutableStateOf(true)
    /** His chosen theme and font; the settings screen changes them. */
    private lateinit var looks: LookStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        looks = LookStore.get(this)
        // Draw under the status and navigation bars, with light icons so they read on a dark wallpaper.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // The home screen has nowhere to go "back" to, so the back gesture does nothing here.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })
        setContent {
            LauncherTheme(look = animatedLook(looks.look)) {
                HomeScreen(state = home, isDefault = isDefault)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SeelieWidgetHost.start(this)
    }

    override fun onStop() {
        super.onStop()
        SeelieWidgetHost.stop(this)
        // Leaving the home (an app opens, the screen goes off) locks the vault again.
        VaultStore.get(this).lock()
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time we come to the front, e.g. after changing the Home app in Settings.
        isDefault = DefaultHome.isDefault(this)
    }

    /** Android calls this when Home is pressed while we're already open: go back to the favourites. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        home.reset()
    }
}
