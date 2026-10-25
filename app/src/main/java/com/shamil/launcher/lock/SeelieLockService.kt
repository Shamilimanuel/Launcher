package com.shamil.launcher.lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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

    // The app he just unlocked (VaultStore.passed) stays open while he's in it; it locks again once he leaves it
    // for another app or the home, or the screen goes off.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { VaultStore.get(this@SeelieLockService).passed = null }
    }

    // Packages whose windows never count as "he left the app": the status bar and shade, keyboards, system dialogs.
    private var keyboards: Set<String> = emptySet()
    private val launchable = HashMap<String, Boolean>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching { ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED) }
        keyboards = runCatching {
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet()
        }.getOrNull().orEmpty()
        running = true
    }

    override fun onDestroy() {
        running = false
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Same process as the home: a mistake here must never take the home down.
        runCatching { onFront(pkg) }
    }

    private fun onFront(pkg: String) {
        val vault = VaultStore.get(this)
        if (pkg == packageName) {
            // Back on Seelie (the home, or its lock screen): the unlocked app locks again. Not in the first second
            // after unlocking, while Seelie's own lock screen is still closing.
            if (SystemClock.uptimeMillis() - vault.passedAt > 1000) vault.passed = null
            return
        }
        if (pkg in IGNORED || pkg in keyboards || !isApp(pkg)) return
        if (pkg == vault.passed) return
        if (!vault.guardsFromOutside(pkg)) {
            vault.passed = null   // another app: whatever he unlocked before locks again
            return
        }
        vault.passed = null
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: pkg
        vault.askFromOutside(pkg, label) {
            packageManager.getLaunchIntentForPackage(pkg)?.let { open ->
                runCatching { startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)) }
            }
        }
        // Seelie's home comes to the front with the lock screen up (it lives there, with the fingerprint).
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Only real apps (ones with an icon to open) count; background services and overlays don't. */
    private fun isApp(pkg: String) = launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }

    companion object {
        private val IGNORED = setOf(
            "com.android.systemui", "android",
            "com.google.android.permissioncontroller", "com.android.permissioncontroller",
            "com.samsung.android.app.cocktailbarservice",   // Samsung's edge panel
        )
        /** Compose-readable "is it on", so vault settings update when he comes back from Android's page. */
        var running by mutableStateOf(false)
            private set
    }
}
