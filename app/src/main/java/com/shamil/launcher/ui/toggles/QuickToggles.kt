package com.shamil.launcher.ui.toggles

import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shamil.launcher.R
import com.shamil.launcher.notify.NotifyHub
import com.shamil.launcher.ui.search.Torch

/*
 * Quick toggles (his locked feature #6, 23 Oct; his review: "it would be nice to have that"):
 * a small row of round buttons sitting inside the favourites list.
 *
 * The honest part: since Android 10 an app may NOT switch Wi-Fi, Bluetooth, mobile data or airplane mode itself.
 * So those buttons open Android's own small panel (or the settings page) one tap away, and only light up
 * with the real state where Android lets Seelie read it without asking for anything new.
 * Torch and Quiet (Do Not Disturb) really switch right here.
 */

/** How a toggle works, so the settings page can say it plainly. */
enum class ToggleHow(val tag: String, val what: String) {
    SWITCH("Switches here", "Switches right here"),
    PANEL("Opens a panel", "Opens Android's own small panel over your home"),
    PAGE("Opens settings", "Opens its settings page"),
}

/** One quick toggle. [id] is what gets saved, so never rename one. [short] is the name under/beside the icon. */
enum class QuickToggle(val id: String, val title: String, val short: String, @DrawableRes val icon: Int, val how: ToggleHow) {
    WIFI("wifi", "Wi-Fi", "Wi-Fi", R.drawable.ic_wifi, ToggleHow.PANEL),
    BLUETOOTH("bluetooth", "Bluetooth", "Bluetooth", R.drawable.ic_bluetooth, ToggleHow.PAGE),
    TORCH("torch", "Torch", "Torch", R.drawable.ic_flashlight_on, ToggleHow.SWITCH),
    QUIET("quiet", "Quiet (Do Not Disturb)", "Quiet", R.drawable.ic_do_not_disturb_on, ToggleHow.SWITCH),
    DATA("data", "Internet & data", "Internet", R.drawable.ic_signal_cellular_alt, ToggleHow.PANEL),
    AIRPLANE("airplane", "Airplane mode", "Flight", R.drawable.ic_airplanemode_active, ToggleHow.PAGE),
    VOLUME("volume", "Volume", "Volume", R.drawable.ic_volume_up, ToggleHow.PANEL),
    SAVER("saver", "Battery saver", "Saver", R.drawable.ic_battery_saver, ToggleHow.PAGE),
    HOTSPOT("hotspot", "Hotspot", "Hotspot", R.drawable.ic_wifi_tethering, ToggleHow.PAGE),
    LOCATION("location", "Location", "Location", R.drawable.ic_location_on, ToggleHow.PAGE),
    NFC("nfc", "NFC", "NFC", R.drawable.ic_nfc, ToggleHow.PANEL);

    companion object {
        fun byId(id: String?): QuickToggle? = entries.firstOrNull { it.id == id }

        /** What he gets before changing anything (like the prototype): Wi-Fi, Bluetooth, Torch, Quiet. */
        val DEFAULT = listOf(WIFI, BLUETOOTH, TORCH, QUIET)
    }
}

/** Reading each toggle's real state, and what a tap or long-press does. */
object ToggleActions {

    /** False when this phone simply doesn't have it (no flash, no NFC chip…): then the row leaves it out. */
    fun isAvailable(context: Context, t: QuickToggle): Boolean {
        val pm = context.packageManager
        return when (t) {
            QuickToggle.TORCH -> Torch.hasFlash(context)
            QuickToggle.NFC -> pm.hasSystemFeature(PackageManager.FEATURE_NFC)
            QuickToggle.BLUETOOTH -> pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
            else -> true
        }
    }

    /**
     * The real state: true = on, false = off, null = nothing to show (it's just a doorway, like Volume,
     * or Android won't tell us). Every read here needs no new permission. Cheap, so it's fine to call often.
     */
    @SuppressLint("MissingPermission")   // Bluetooth's isEnabled needs no permission on Android 12+; if a phone disagrees we show no state
    fun read(context: Context, t: QuickToggle): Boolean? = runCatching {
        // The app-wide context: WifiManager leaks memory when fetched from a screen's context on old Androids.
        val app = context.applicationContext
        when (t) {
            QuickToggle.WIFI -> app.getSystemService(WifiManager::class.java)?.isWifiEnabled
            QuickToggle.BLUETOOTH -> app.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
            QuickToggle.TORCH -> Torch.isOn
            QuickToggle.QUIET -> app.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter?.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
            QuickToggle.AIRPLANE -> Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
            QuickToggle.SAVER -> app.getSystemService(PowerManager::class.java)?.isPowerSaveMode
            QuickToggle.LOCATION -> locationOn(app)
            QuickToggle.NFC -> NfcAdapter.getDefaultAdapter(app)?.isEnabled
            QuickToggle.DATA, QuickToggle.VOLUME, QuickToggle.HOTSPOT -> null
        }
    }.getOrNull()

    /** The ring mode for the Volume button's little label: "Ring", "Vibrate" or "Silent". */
    fun ringer(context: Context): String? = runCatching {
        when (context.applicationContext.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Ring"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            else -> null
        }
    }.getOrNull()

    @Suppress("DEPRECATION")   // LOCATION_MODE is the only way on Android 8 (minSdk 26); 9+ uses isLocationEnabled
    private fun locationOn(context: Context): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled
        } else {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF) !=
                Settings.Secure.LOCATION_MODE_OFF
        }

    /**
     * A tap. [on] is what the button shows right now.
     * Returns the new state when it switched something right here (Torch, Quiet), so the row can light up at once;
     * null when it opened a panel or page instead (the row catches up by itself when he comes back).
     */
    @SuppressLint("InlinedApi")   // the Settings.Panel names are only used on Android 10+ (see panel())
    fun tap(context: Context, t: QuickToggle, on: Boolean?): Boolean? {
        when (t) {
            QuickToggle.TORCH -> {
                if (Torch.toggle(context)) return Torch.isOn
                toast(context, "Couldn't switch the torch (is the camera in use?)")
            }
            QuickToggle.QUIET -> return switchQuiet(context, on == true)
            QuickToggle.WIFI -> open(context, panel(Settings.Panel.ACTION_WIFI), Intent(Settings.ACTION_WIFI_SETTINGS))
            QuickToggle.BLUETOOTH -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            QuickToggle.DATA -> open(context, panel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            QuickToggle.AIRPLANE -> open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            // Volume: Seelie's own sheet in his chosen look (25 Oct); "Phone's own" = Android's slider, like a volume key.
            QuickToggle.VOLUME -> if (TogglePrefs.get(context).setup.volumeLook != VolumeLook.SYSTEM.id && com.shamil.launcher.volume.SeelieVolumeService.instance != null) {
                com.shamil.launcher.volume.SeelieVolumeService.instance?.show()
            } else if (TogglePrefs.get(context).setup.volumeLook != VolumeLook.SYSTEM.id && VolumeSheet.hosts > 0) VolumeSheet.open = true
            else if (!showVolumeSlider(context)) {
                open(context, panel(Settings.Panel.ACTION_VOLUME), Intent(Settings.ACTION_SOUND_SETTINGS))
            }
            QuickToggle.SAVER -> open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            QuickToggle.HOTSPOT -> open(context, Intent(TETHER_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            QuickToggle.LOCATION -> open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            QuickToggle.NFC -> open(context, panel(Settings.Panel.ACTION_NFC), Intent(Settings.ACTION_NFC_SETTINGS))
        }
        return null
    }

    /** A long-press: the full settings page for it (like long-pressing a tile in Android's quick settings). */
    @SuppressLint("InlinedApi")   // ACTION_DATA_USAGE_SETTINGS is Android 9+; on 8 the next intent takes over
    fun openSettings(context: Context, t: QuickToggle) {
        when (t) {
            QuickToggle.WIFI -> open(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            QuickToggle.BLUETOOTH -> open(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            QuickToggle.TORCH -> tap(context, t, Torch.isOn)   // the torch has no settings page: a long-press just switches it too
            QuickToggle.QUIET -> open(context, Intent(ZEN_MODE_SETTINGS), Intent(Settings.ACTION_SOUND_SETTINGS))
            QuickToggle.DATA -> open(context, Intent(Settings.ACTION_DATA_USAGE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            QuickToggle.AIRPLANE -> open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            QuickToggle.VOLUME -> open(context, Intent(Settings.ACTION_SOUND_SETTINGS))
            QuickToggle.SAVER -> open(context, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            QuickToggle.HOTSPOT -> open(context, Intent(TETHER_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
            QuickToggle.LOCATION -> open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            QuickToggle.NFC -> open(context, Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    /**
     * Quiet = Do Not Disturb ("Priority only"). The notification listener may switch it without any extra permission
     * while it's connected; apps with Do Not Disturb access may too. Otherwise: explain, and open the access page.
     */
    private fun switchQuiet(context: Context, on: Boolean): Boolean? {
        // The listener's INTERRUPTION_FILTER_* numbers are the same as NotificationManager's (1 = all, 2 = priority).
        val target = if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        val listener = NotifyHub.listener
        if (listener != null && runCatching { listener.requestInterruptionFilter(target) }.isSuccess) return !on
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm != null && nm.isNotificationPolicyAccessGranted && runCatching { nm.setInterruptionFilter(target) }.isSuccess) return !on
        toast(context, "Turn on Notification access for Seelie, then Quiet switches right here")
        open(context, NotifyHub.accessIntent(context))
        return null
    }

    /** Pops up Android's volume slider (the same one the volume keys show). False if this phone refused. */
    private fun showVolumeSlider(context: Context): Boolean = runCatching {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        // ADJUST_SAME = don't change anything, FLAG_SHOW_UI = but do show the slider.
        audio.adjustSuggestedStreamVolume(AudioManager.ADJUST_SAME, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
        true
    }.getOrDefault(false)

    /** Android's small "Settings Panel" (slides up over the home) exists from Android 10; null before that. */
    private fun panel(action: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Intent(action) else null

    /** Tries each screen in order until one opens (phones differ: Samsung hides some). */
    fun open(context: Context, vararg intents: Intent?): Boolean {
        val activity = context.findActivity()
        for (intent in intents) {
            if (intent == null) continue
            // Started from outside a screen, Android needs NEW_TASK; from the home screen itself it isn't needed.
            if (activity == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                (activity ?: context).startActivity(intent)
                return true
            } catch (e: Exception) {
                // Not on this phone: try the next one.
            }
        }
        toast(context, "Couldn't open that on this phone")
        return false
    }

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    /** Compose's LocalContext can be a wrapper around the screen; this finds the screen (Activity) inside. */
    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /** Settings screens that work on almost every phone but have no public name in Android's code. */
    private const val TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    private const val ZEN_MODE_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
}

/** The live on/off state of every toggle, as Compose state (the row redraws when it changes). */
@Stable
class ToggleStates internal constructor(private val app: Context) {
    private var values by mutableStateOf<Map<QuickToggle, Boolean?>>(emptyMap())

    /** "Ring", "Vibrate" or "Silent", for the Volume button. */
    var ringer by mutableStateOf<String?>(null)
        private set

    /** true = on, false = off, null = no state to show. The torch reads Torch's own live state. */
    fun isOn(t: QuickToggle): Boolean? = if (t == QuickToggle.TORCH) Torch.isOn else values[t]

    /** Reads everything again (a handful of quick system calls). */
    fun refresh() {
        values = QuickToggle.entries.associateWith { ToggleActions.read(app, it) }
        ringer = ToggleActions.ringer(app)
    }

    /** Shows the new state straight away after a tap; Android's broadcast confirms it a moment later. */
    internal fun guess(t: QuickToggle, on: Boolean) {
        values = values + (t to on)
    }

    internal companion object {
        /** Android's "this changed" announcements we listen to while the row is on screen. */
        fun filter() = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
            addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
    }
}

/**
 * Keeps [ToggleStates] live, but only while the screen is visible ("STARTED"): it listens to Android's
 * change broadcasts and the torch callback, and reads everything again each time he comes back (onResume).
 * Nothing runs while the home is hidden, so it costs no battery.
 */
@Composable
fun rememberToggleStates(): ToggleStates {
    val context = LocalContext.current
    val app = context.applicationContext
    val owner = LocalLifecycleOwner.current
    val states = remember { ToggleStates(app) }
    DisposableEffect(owner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = states.refresh()
        }
        var listening = false
        fun start() {
            if (!listening) {
                // EXPORTED so the Bluetooth and NFC services (not "the system" itself) can reach us. Safe: every action
                // in the filter is a protected system broadcast, so other apps can't fake one.
                runCatching { ContextCompat.registerReceiver(app, receiver, ToggleStates.filter(), ContextCompat.RECEIVER_EXPORTED) }
                    .onSuccess { listening = true }
            }
            Torch.watch(app)
            states.refresh()
        }
        fun stop() {
            if (listening) runCatching { app.unregisterReceiver(receiver) }
            listening = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> states.refresh()   // back from a panel or settings page
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        // addObserver replays ON_START/ON_RESUME when the screen is already showing, so this also starts it.
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
    return states
}
