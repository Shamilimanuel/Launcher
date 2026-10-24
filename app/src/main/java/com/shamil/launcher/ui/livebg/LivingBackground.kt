package com.shamil.launcher.ui.livebg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.shamil.launcher.glance.Weather
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

/*
 * The living background on the home: a full-screen layer drawn over his own wallpaper and behind the home.
 * It runs ONE frame loop, and only while it has a reason to:
 *  - the home is on screen and in front ("RESUMED") and the screen is on;
 *  - battery saver is off and Android's animations are on (Developer options → Animator duration scale);
 *  - at least one effect is on. With nothing on it draws nothing and runs nothing.
 * When everything has faded out (say only Stars is on and it's daytime) the loop sleeps and peeks every 15 s.
 * Each frame only moves the particles and asks for a redraw: the drawing happens in the draw phase,
 * so Compose never rebuilds ("recomposes") the screen for the animation.
 */

/** Lets the home pass taps on its empty background to the living background (for Touch sparkle). */
class LiveBgController {
    internal var world: LiveWorld? = null

    /** A tap at ([x], [y]) in pixels from the background's top-left (the screen's, when it fills the screen). */
    fun onTap(x: Float, y: Float) {
        world?.tap(x, y)
    }
}

@Composable
fun rememberLiveBgController(): LiveBgController = remember { LiveBgController() }

/**
 * The living background. Put it full-screen at the very back of the home (above nothing: the wallpaper shows through).
 * [weather] = the glance's weather (for "follow the real weather"); [active] = false while something covers the whole
 * home (the app list, a full sheet) so it pauses; [controller] = where the home forwards taps (Touch sparkle).
 */
@Composable
fun LivingBackground(modifier: Modifier, weather: Weather?, active: Boolean = true, controller: LiveBgController? = null) {
    LiveBgLayer(modifier, weather?.code, active, controller, preview = false)
}

/** The engine behind [LivingBackground] and the settings preview ([preview] = true keeps running in battery saver). */
@Composable
internal fun LiveBgLayer(modifier: Modifier, weatherCode: Int?, active: Boolean, controller: LiveBgController?, preview: Boolean) {
    val context = LocalContext.current
    val store = remember { LiveBgStore.get(context) }
    val style = store.style
    val gate = rememberSystemGate()
    if (!style.anythingOn() || !gate.animationsOn || (!preview && gate.powerSave)) return

    val world = remember { LiveWorld() }
    // The sky shader needs Android 13; below that (or if it fails to build) the sky effects just don't draw.
    val sky = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) SkyShader.create() else null }
    val skyLayer = rememberGraphicsLayer()
    // A number that goes up every frame. Only the drawing reads it, so only the drawing repeats.
    val tick = remember { mutableIntStateOf(0) }

    SideEffect {
        world.style = style
        world.realWeather = weatherKind(weatherCode)
        world.skyOn = sky != null
    }
    DisposableEffect(controller, world) {
        controller?.world = world
        onDispose { if (controller != null && controller.world === world) controller.world = null }
    }

    val owner = LocalLifecycleOwner.current
    val running = active && gate.screenOn
    LaunchedEffect(owner, running) {
        if (!running) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val tilt = TiltSensor(context, world)
            try {
                var last = 0L
                while (true) {
                    if (!world.busy) {
                        // Nothing moves: let go of the sensor and sleep until a tap or a new style, or 15 s.
                        tilt.want(false)
                        withTimeoutOrNull(15_000L) { world.wake.receive() }
                        last = 0L
                    }
                    tilt.want(world.wantsTilt)
                    val interval = if (world.style.fps60) 16_666_667L else 33_333_333L
                    withFrameNanos { now ->
                        val gap = now - last
                        // 30 fps (or 60): skip screen refreshes that come too soon (his screen refreshes up to 120×/s).
                        if (last == 0L || gap >= interval - 4_000_000L) {
                            val dt = if (last == 0L) 1f / 60f else min(.05f, gap / 1_000_000_000f)
                            last = now
                            world.step(dt)
                            tick.intValue++
                        }
                    }
                    // Rest until shortly before the next frame is due, instead of waking at every refresh.
                    delay((interval / 1_000_000L - 10L).coerceAtLeast(0L))
                }
            } finally {
                tilt.want(false)
            }
        }
    }

    Spacer(
        modifier.drawBehind {
            tick.intValue   // read only to redraw on every frame
            world.glintGuideAt = store.glintGuideAt
            world.draw(this, sky, skyLayer)
        }
    )
}

/** Is the screen on, is battery saver on, are Android's animations on. Refreshed by Android's broadcasts and on every return to the home. */
internal class SystemGate {
    var screenOn by mutableStateOf(true)
    var powerSave by mutableStateOf(false)
    var animationsOn by mutableStateOf(true)
}

@Composable
internal fun rememberSystemGate(): SystemGate {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val gate = remember { SystemGate() }
    DisposableEffect(owner) {
        val power = context.getSystemService(PowerManager::class.java)
        fun refresh() {
            gate.screenOn = power?.isInteractive ?: true
            gate.powerSave = power?.isPowerSaveMode ?: false
            gate.animationsOn = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = refresh()
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        var listening = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!listening) {
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    listening = true
                    refresh()
                }
                // He may have switched battery saver or animations while away: check again on every return.
                Lifecycle.Event.ON_RESUME -> refresh()
                Lifecycle.Event.ON_STOP -> if (listening) {
                    context.unregisterReceiver(receiver)
                    listening = false
                }
                else -> {}
            }
        }
        refresh()
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (listening) context.unregisterReceiver(receiver)
        }
    }
    return gate
}

/**
 * Tilt parallax: reads how the phone leans, from the "game rotation vector" sensor (a smooth mix of the gyroscope
 * and accelerometer) or, if the phone has none, the accelerometer. Only listens while [want] says so.
 * The middle slowly follows how he holds the phone (over ~4 s), so only the tilting itself moves things.
 */
internal class TiltSensor(context: Context, private val world: LiveWorld) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listening = false
    private val rot = FloatArray(9)
    /** "Up" in the phone's own directions (x right, y up the screen, z out of the screen). */
    private val up = FloatArray(3)
    private var baseRoll = 0f
    private var basePitch = 0f
    private var hasBase = false
    private var lastNs = 0L

    fun want(on: Boolean) {
        val m = manager ?: return
        val s = sensor ?: return
        if (on == listening) return
        listening = on
        if (on) {
            hasBase = false
            lastNs = 0L
            m.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
        } else {
            m.unregisterListener(this)
            world.tiltTx = 0f
            world.tiltTy = 0f
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rot, e.values)
            // The third row of the rotation matrix = the world's "up" seen from the phone.
            up[0] = rot[6]; up[1] = rot[7]; up[2] = rot[8]
        } else {
            val x = e.values[0]
            val y = e.values[1]
            val z = e.values[2]
            val n = sqrt(x * x + y * y + z * z)
            if (n < 1f) return
            // Smooth the shaky accelerometer a little.
            up[0] += (x / n - up[0]) * .2f; up[1] += (y / n - up[1]) * .2f; up[2] += (z / n - up[2]) * .2f
        }
        val roll = -asin(up[0].coerceIn(-1f, 1f)) * DEG   // + = right edge down (like the mockup's gamma)
        val pitch = atan2(up[2], up[1]) * DEG               // 0 = upright, 90 = flat on a table
        val dt = if (lastNs == 0L) 0f else (e.timestamp - lastNs) / 1_000_000_000f
        lastNs = e.timestamp
        if (!hasBase) {
            baseRoll = roll; basePitch = pitch; hasBase = true
        } else {
            val f = 1f - exp(-dt / 4f)
            baseRoll += (roll - baseRoll) * f
            basePitch += (pitch - basePitch) * f
        }
        world.tiltTx = ((roll - baseRoll) / 22f).coerceIn(-1f, 1f)
        world.tiltTy = (-(pitch - basePitch) / 22f).coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val DEG = 57.29578f
    }
}
