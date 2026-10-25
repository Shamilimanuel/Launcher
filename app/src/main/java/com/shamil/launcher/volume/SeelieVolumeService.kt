package com.shamil.launcher.volume

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shamil.launcher.ui.theme.LauncherTheme
import com.shamil.launcher.ui.theme.LookStore
import com.shamil.launcher.ui.toggles.ToggleActions
import com.shamil.launcher.ui.toggles.TogglePrefs
import com.shamil.launcher.ui.toggles.VolumeLook
import com.shamil.launcher.ui.toggles.VolumeSideBar
import com.shamil.launcher.ui.toggles.cycleRinger

/*
 * Seelie's volume buttons (25 Oct, his ask: "it doesn't come on the side bar" → the real thing, everywhere).
 * An Accessibility service, the only way Android lets an app take the + / − buttons: it hears ONLY those two keys
 * (no screen reading, no events), sets the media volume itself and slides Seelie's side bar in from the edge,
 * over any app. Screen off, calls and a ringing phone keep the phone's own handling, and "Phone's own" in
 * settings hands the buttons straight back.
 */
class SeelieVolumeService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val owner = OverlayOwner()
    private var view: FrameLayout? = null
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val power by lazy { getSystemService(PowerManager::class.java) }

    // What the bar shows. Compose watches these.
    private val visible = MutableTransitionState(false)
    private var level by mutableIntStateOf(0)
    private var ringer by mutableStateOf<String?>(null)
    private var max = 15

    // Decided on the first press, kept until the key comes up, so a press is never half ours and half the phone's.
    private val taking = HashMap<Int, Boolean>()

    private val hide = Runnable { visible.targetState = false; main.postDelayed(detach, 400) }
    private val detach = Runnable { if (!visible.targetState) removeView() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        owner.start()
        runCatching { getSystemService(android.hardware.camera2.CameraManager::class.java)?.registerAvailabilityCallback(cameraWatch, main) }
        max = runCatching { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: 15
        instance = this
        running = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        running = false
        main.removeCallbacksAndMessages(null)
        runCatching { getSystemService(android.hardware.camera2.CameraManager::class.java)?.unregisterAvailabilityCallback(cameraWatch) }
        removeView()
        owner.stop()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = try {
        handleKey(event)
    } catch (_: Throwable) {
        // Anything going wrong here would take the home down with it (same process): hand the key to the phone instead.
        stopRepeat()
        false
    }

    private fun handleKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) taking[code] = takesKeys()
        val ours = taking[code] ?: false
        if (event.action == KeyEvent.ACTION_UP) taking.remove(code)
        if (!ours) return false
        when {
            // One step now, then our own repeat while held: a key we take never reaches Android's own repeat.
            event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> {
                stepKey(code)
                stopRepeat()
                repeatKey = code
                repeatUntil = android.os.SystemClock.uptimeMillis() + REPEAT_CAP_MS
                main.postDelayed(repeat, REPEAT_START_MS)
            }
            event.action == KeyEvent.ACTION_UP -> if (repeatKey == code) stopRepeat()
        }
        return true
    }

    private fun stepKey(code: Int) {
        val dir = if (code == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        runCatching { audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0) }
        if (TogglePrefs.get(this).setup.volumeClick) runCatching { audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f) }
        show()
    }

    // Holding a button: a step every 100 ms after 0.4 s, until it comes up, the screen goes off, or 4 s pass.
    private var repeatKey = 0
    private var repeatUntil = 0L
    private val repeat = object : Runnable {
        override fun run() {
            if (repeatKey == 0 || power?.isInteractive != true || android.os.SystemClock.uptimeMillis() > repeatUntil) { stopRepeat(); return }
            runCatching { stepKey(repeatKey) }
            main.postDelayed(this, REPEAT_EVERY_MS)
        }
    }

    private fun stopRepeat() {
        repeatKey = 0
        main.removeCallbacks(repeat)
    }

    /** Ours only with the screen on, nothing ringing or calling, and "Seelie's bar" chosen in settings. */
    private fun takesKeys(): Boolean {
        val setup = TogglePrefs.get(this).setup
        if (!setup.volumeKeys || setup.volumeLook == VolumeLook.SYSTEM.id) return false
        if (power?.isInteractive != true) return false
        if ((audio?.mode ?: AudioManager.MODE_NORMAL) != AudioManager.MODE_NORMAL) return false
        if (camerasInUse.isNotEmpty()) return false
        val alarm = runCatching {
            audio?.activePlaybackConfigurations?.any { it.audioAttributes.usage == android.media.AudioAttributes.USAGE_ALARM }
        }.getOrNull() ?: false
        return !alarm
    }

    // Which cameras an app has open right now (Android tells anyone, no permission): their volume keys are theirs.
    private val camerasInUse = HashSet<String>()
    private val cameraWatch = object : android.hardware.camera2.CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) { camerasInUse += cameraId }
        override fun onCameraAvailable(cameraId: String) { camerasInUse -= cameraId }
    }

    /** Slides the bar in (or keeps it up) and starts the quiet-seconds countdown again. Also used by the Volume toggle. */
    fun show() {
        level = runCatching { audio?.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: level
        ringer = ToggleActions.ringer(this)
        if (view == null) addView()
        visible.targetState = true
        poke()
    }

    private fun poke() {
        main.removeCallbacks(hide)
        main.removeCallbacks(detach)
        main.postDelayed(hide, HIDE_MS)
    }

    private fun addView() {
        val wm = getSystemService(WindowManager::class.java) ?: return
        val left = TogglePrefs.get(this).setup.volumeEdge == "left"
        // A tap anywhere else on the screen puts it away (the tap itself still reaches the app below).
        val v = object : FrameLayout(this) {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { main.removeCallbacks(hide); hide.run(); return true }
                return super.dispatchTouchEvent(e)
            }
        }
        v.setViewTreeLifecycleOwner(owner)
        v.setViewTreeSavedStateRegistryOwner(owner)
        v.addView(ComposeView(this).apply { setContent { Bar(left) } })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (left) Gravity.START else Gravity.END) or Gravity.TOP
            // Next to where a Galaxy's real volume buttons sit.
            val screen = resources.displayMetrics.heightPixels
            val bar = (380 * resources.displayMetrics.density).toInt()
            y = minOf((screen * 0.2f).toInt(), screen - bar).coerceAtLeast(0)
        }
        if (runCatching { wm.addView(v, params) }.isSuccess) view = v
    }

    private fun removeView() {
        val v = view ?: return
        view = null
        runCatching { getSystemService(WindowManager::class.java)?.removeView(v) }
    }

    @Composable
    private fun Bar(left: Boolean) {
        val store = remember { LookStore.get(this) }
        val look = VolumeLook.byId(TogglePrefs.get(this).setup.volumeLook)
        LauncherTheme(look = store.look) {
            AnimatedVisibility(
                visible,
                enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 500f)) { if (left) -it else it } + fadeIn(),
                exit = slideOutHorizontally(tween(220)) { if (left) -it else it } + fadeOut(tween(200)),
            ) {
                VolumeSideBar(
                    level, max, look, ringer, left,
                    onChange = { v ->
                        poke()
                        if (v != level) {
                            runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
                            if (TogglePrefs.get(this@SeelieVolumeService).setup.volumeClick) runCatching { audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f) }
                            level = v
                        }
                    },
                    onRinger = { poke(); ringer = cycleRinger(this@SeelieVolumeService) },
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
    }

    companion object {
        private const val HIDE_MS = 3000L
        private const val REPEAT_START_MS = 400L
        private const val REPEAT_EVERY_MS = 100L
        private const val REPEAT_CAP_MS = 4000L
        /** The running service, or null when it's switched off in Android's Accessibility settings. */
        var instance: SeelieVolumeService? = null
            private set
        /** Compose-readable "is it on", so settings updates when he comes back from Android's page. */
        var running by mutableStateOf(false)
            private set
    }
}

/** A window outside any activity still needs a lifecycle for Compose to run in. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun start() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) return
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }
    fun stop() {
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.DESTROYED
    }
}
