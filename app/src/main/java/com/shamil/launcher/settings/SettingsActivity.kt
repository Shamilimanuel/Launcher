package com.shamil.launcher.settings

import android.graphics.Color
import android.content.Intent
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
import com.shamil.launcher.ui.widgets.SeelieWidgetHost
import com.shamil.launcher.ui.widgets.WidgetSetupResults

/**
 * "Seelie settings" (Serenitea): a window inside Seelie, like Niagara's settings (no icon or task of its own).
 * Picks here change the home screen straight away (one shared [LookStore]).
 */
class SettingsActivity : ComponentActivity(), WidgetSetupResults {
    // A widget's own setup screen reports back here (see AppWidgetPickerContent).
    override var onWidgetSetupDone: ((Boolean) -> Unit)? = null
    override val widgetSetupRequestCode = 4201

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
        // Opened through the NightSettings alias (the gear on the phone's Screen saver page): start on the night screen tab.
        val startAt = intent.getStringExtra(SeelieSettings.EXTRA_PAGE)
            ?: SeelieSettings.PAGE_NIGHT.takeIf { intent.component?.className?.endsWith(".NightSettings") == true }
        setContent {
            LauncherTheme(look = animatedLook(looks.look)) {
                SettingsApp(looks = looks, startAt = startAt, onClose = ::finish)
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
    }

    @Deprecated("Widget setup screens can only report back this way (Android's AppWidgetHost API).")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == widgetSetupRequestCode) {
            onWidgetSetupDone?.invoke(resultCode == RESULT_OK)
            onWidgetSetupDone = null
            return
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.seelie_hold, R.anim.seelie_fade_out)
        }
    }
}
