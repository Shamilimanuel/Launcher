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
import androidx.compose.runtime.remember
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
    /** The rescue screen instead of the home (safe mode): after crashes, or opened by hand ([rescueByHand]). */
    private var rescue by mutableStateOf(false)
    private var rescueByHand by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First of all, before anything that could crash: note crashes, and check if it keeps crashing.
        SafeMode.install(this)
        rescueByHand = intent?.getBooleanExtra(SafeMode.EXTRA_RESCUE, false) == true
        rescue = rescueByHand || SafeMode.shouldRescue(this)
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
            if (rescue) {
                SafeModeScreen(byHand = rescueByHand) { rescue = false; rescueByHand = false }
            } else {
                // Only now: in safe mode even reading his look is skipped, in case that's what breaks.
                val store = remember { LookStore.get(this).also { looks = it } }
                LauncherTheme(look = animatedLook(store.look)) {
                    HomeScreen(state = home, isDefault = isDefault)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        SeelieWidgetHost.start(this)
    }

    /** True while Seelie is on screen (from onResume until onStop): tells a Home press apart from coming back from an app. */
    private var inFront = false

    override fun onStop() {
        super.onStop()
        inFront = false
        SeelieWidgetHost.stop(this)
        // Leaving the home (an app opens, the screen goes off) locks the vault again.
        VaultStore.get(this).lock()
    }

    override fun onResume() {
        super.onResume()
        inFront = true
        // Re-check every time we come to the front, e.g. after changing the Home app in Settings.
        isDefault = DefaultHome.isDefault(this)
        // His wallpaper may have changed while he was away ("Match my wallpaper" theme).
        if (::looks.isInitialized) looks.refreshWallpaper()
    }

    /**
     * Android calls this on every Home press. Already on Seelie: back to the favourites. Coming back from an app
     * (24 Oct: "I go to G, open Google, press Home: keep it where I entered"): stay in the A–Z where he was.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(SafeMode.EXTRA_RESCUE, false)) {
            rescueByHand = true
            rescue = true
            return
        }
        if (inFront) home.reset() else home.returnFromApp()
    }
}
