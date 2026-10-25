package com.shamil.launcher.night

import android.os.Build
import android.service.dreams.DreamService
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.shamil.launcher.ui.theme.LookStore

/*
 * Seelie's night screen (25 Oct). A screen saver ("dream" in Android's words): the phone shows it by itself while
 * charging, once he picks Seelie under Settings → Display → Screen saver. The first test answered the big questions on
 * his S24 Ultra (Samsung lists it, it turns sideways with auto-rotate, swipes don't wake the phone, ✕ closes it); this
 * is the full version: his pages (NightPages.kt), skies (NightSky.kt), cream cards (NightCards.kt), all chosen in
 * Clock & date → Night screen (NightScreenSettings.kt).
 * Interactive: a touch does NOT close it (so the pages can swipe and the music buttons work). The ✕ or Back does.
 */
class SeelieDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    // Compose needs these three on the window (an Activity has them built in; a dream doesn't). Same idea as the
    // volume bar's OverlayOwner, but the dream itself plays the part.
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    /** True only while the dream is really on screen: the stars and the breathing colon only move then. */
    private var running by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true        // touches go to Seelie (swiping pages), they don't wake the phone
        isFullscreen = true         // no status bar
        isScreenBright = true       // the phone's own brightness; Seelie dims its picture itself (see NightScreen)
        val decor = window.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        decor.setViewTreeViewModelStoreOwner(this)
        val font = LookStore.get(this).look.font
        setContentView(ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SeelieDream)
            setViewTreeSavedStateRegistryOwner(this@SeelieDream)
            setViewTreeViewModelStoreOwner(this@SeelieDream)
            setContent { NightScreen(running = running, body = font, onClose = ::leave) }
        })
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        registry.currentState = Lifecycle.State.RESUMED
        running = true
    }

    override fun onDreamingStopped() {
        running = false
        // Below STARTED Compose stops asking for frames, so nothing draws while the dream is off screen.
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        running = false
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        super.onDetachedFromWindow()
    }

    /** Back closes it (Android does this for interactive dreams too; said here so Samsung can't skip it). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) leave()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Ends the night screen and wakes the phone to its lock screen (Android 10+), or just ends it on older phones. */
    private fun leave() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) wakeUp() else finish()
    }
}
