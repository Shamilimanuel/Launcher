package com.shamil.launcher.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.shamil.launcher.MainActivity
import com.shamil.launcher.apps.VaultStore

/*
 * Seelie app lock, everywhere (25 Oct, his find: a locked app opened from the recent-apps screen skipped the lock).
 * A second Accessibility service, separate from the volume one so that one keeps its "buttons only" promise. It only
 * learns WHICH app comes to the front (the package name, never what's on screen). A locked app, or an app hidden in
 * the vault, coming to the front from anywhere (recent apps, a notification, another app) brings Seelie's lock screen
 * over it; the right code sends him straight back in. Honest limits: the app can show for a split second before the
 * lock, and the recent-apps preview picture is Android's, not ours.
 */
class SeelieLockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    // The app he just unlocked (VaultStore.passed) stays open while he's in it; it locks again once he leaves it
    // for another app or the home, or the screen goes off.
    private val phoneEvents = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // Same process as the home: a mistake here must never take the home down.
            runCatching {
                when (i?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        handler.removeCallbacks(dropGrace)
                        VaultStore.get(this@SeelieLockService).passed = null
                    }
                    // The phone was unlocked (25 Oct, audit S12): coming back into a locked app this way doesn't always
                    // send a "new app in front" event, so check the app that was in front when the screen went off.
                    // (Asking Android which window is in front would mean reading screen content; this service never does.)
                    Intent.ACTION_USER_PRESENT -> lastFront?.let { onFront(it) }
                    // A keyboard was added or switched: read the list again so it never counts as "he left the app".
                    Intent.ACTION_INPUT_METHOD_CHANGED -> readKeyboards()
                }
            }
        }
    }

    // Packages whose windows never count as "he left the app": the status bar and shade, keyboards, system dialogs.
    private var keyboards: Set<String> = emptySet()
    private val launchable = HashMap<String, Boolean>()
    /** The last real app (or Seelie) that came to the front: checked again when the phone is unlocked. */
    private var lastFront: String? = null

    /*
     * A short visit to another app doesn't end the unlocked app's grace (25 Oct, audit S12): the share sheet, a
     * "complete action with" pop-up or a split-second system window would otherwise lock it again under his finger.
     * Only once that other app has stayed in front for a second does the grace go.
     */
    private var dropFor: String? = null
    private val dropGrace = Runnable {
        val vault = VaultStore.get(this)
        // Only the app it was meant for: if he has since opened another app the right way, that one keeps its grace.
        if (vault.passed != null && vault.passed == dropFor) vault.passed = null
        dropFor = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_INPUT_METHOD_CHANGED)
        }
        runCatching { ContextCompat.registerReceiver(this, phoneEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        readKeyboards()
        running = true
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(dropGrace)
        runCatching { unregisterReceiver(phoneEvents) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Same process as the home: a mistake here must never take the home down.
        runCatching { onFront(pkg) }
    }

    private fun readKeyboards() {
        keyboards = runCatching {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
        }.getOrNull() ?: keyboards
    }

    private fun onFront(pkg: String) {
        val vault = VaultStore.get(this)
        if (pkg == packageName) {
            lastFront = pkg
            handler.removeCallbacks(dropGrace)
            // Back on Seelie (the home, or its lock screen): the unlocked app locks again. Not in the first second
            // after unlocking, while Seelie's own lock screen is still closing.
            if (SystemClock.uptimeMillis() - vault.passedAt > 1000) vault.passed = null
            return
        }
        if (pkg in IGNORED) return
        // A package seen for the first time may be a keyboard he just added: read the list again (cheap).
        if (pkg !in launchable) readKeyboards()
        if (pkg in keyboards || !isApp(pkg)) return
        lastFront = pkg
        if (pkg == vault.passed) {
            // Back in the app he unlocked within the second: it keeps its grace.
            handler.removeCallbacks(dropGrace)
            return
        }
        if (!vault.guardsFromOutside(pkg)) {
            // Another app: whatever he unlocked before locks again, once this one has stayed a second (see dropGrace).
            if (vault.passed != null) {
                handler.removeCallbacks(dropGrace)
                dropFor = vault.passed
                handler.postDelayed(dropGrace, 1000)
            }
            return
        }
        handler.removeCallbacks(dropGrace)
        // Its lock screen is already up or on its way (e.g. both the window event and the phone unlock saw it).
        if (vault.pending?.packageName == pkg) return
        vault.passed = null
        // Seelie's home comes to the front with the lock screen up (it lives there, with the fingerprint). Started
        // first, before the name lookup, so the app shows for as short as possible (25 Oct, audit S12). Both run on the
        // main thread, so the lock is set before Seelie's screen draws.
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: pkg
        vault.askFromOutside(pkg, label) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { open ->
                runCatching { startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
            }
        }
    }

    /** Only real apps (ones with an icon to open) count; background services and overlays don't. */
    private fun isApp(pkg: String) = launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }

    companion object {
        private val IGNORED = setOf(
            "com.android.systemui", "android",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.samsung.android.app.cocktailbarservice",   // Samsung's edge panel
            "com.sec.android.app.launcher",                 // Samsung's recent-apps screen (One UI Home)
            "com.android.intentresolver",                   // Android 14+'s share sheet and "Open with" chooser
        )
        /** Compose-readable "is it on", so vault settings update when he comes back from Android's page. */
        var running by mutableStateOf(false)
            private set
    }
}
