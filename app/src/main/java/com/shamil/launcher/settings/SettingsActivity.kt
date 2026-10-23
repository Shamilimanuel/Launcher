package com.shamil.launcher.settings

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.LauncherTheme
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.theme.animatedLook

/**
 * "Seelie settings" (Serenitea): a window inside Seelie, like Niagara's settings (no icon or task of its own).
 * Picks here change the home screen straight away (one shared [LookStore]).
 */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Fade in and out over the home (~0.2 s), however it was opened. Older Android: see [finish].
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.seelie_fade_in, R.anim.seelie_hold)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.seelie_hold, R.anim.seelie_fade_out)
        }
        val looks = LookStore.get(this)
        setContent {
            LauncherTheme(look = animatedLook(looks.look)) {
                SettingsApp(looks = looks, onClose = ::finish)
            }
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.seelie_hold, R.anim.seelie_fade_out)
        }
    }
}
