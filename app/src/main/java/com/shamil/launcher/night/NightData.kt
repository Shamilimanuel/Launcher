package com.shamil.launcher.night

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.shamil.launcher.glance.AgendaSource
import com.shamil.launcher.glance.CalEvent
import com.shamil.launcher.glance.Weather
import com.shamil.launcher.glance.WeatherSource
import com.shamil.launcher.ui.media.NowPlaying
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/*
 * What the night screen shows, read while it runs (25 Oct). Everything stays on the phone; the only thing that goes out
 * is the weather question (Seelie's usual Open-Meteo one, with a rough location).
 * Nothing here runs while the screen saver is off screen ([running] false).
 */

/** What changes once a minute: the time, the next alarm and the battery. */
internal data class Minute(
    val now: LocalDateTime,
    /** When the next alarm rings (any clock app's, from Android), or null. */
    val alarm: Long?,
    val battery: Int,
    val charging: Boolean,
    /** Minutes until full while charging, when known. */
    val fullIn: Int?,
    /** Counts up every minute (the burn-in drift moves on it). */
    val count: Int,
)

/** Everything the pages read, handed down once (music has its own, below, because it changes every second). */
internal class NightData(
    /** The slow clock (~30 frames a second while running): every moving thing reads it in its drawing only. */
    val seconds: FloatState,
    val minute: Minute,
    val weather: Weather?,
    val weatherAllowed: Boolean,
    /** From now until the end of tomorrow. */
    val events: List<CalEvent>,
    val calendarAllowed: Boolean,
    /** "Hide event titles" (Clock & date → Glance line): the cards say "Event at 18:00". */
    val hideTitles: Boolean,
    /** His font for the card text (the clock is always Cinzel). */
    val body: FontFamily,
    /** False in the settings preview: nothing there reacts to taps. */
    val live: Boolean,
)

internal val LocalNight = compositionLocalOf<NightData?> { null }
internal val LocalMusic = compositionLocalOf<NowPlaying?> { null }

/**
 * The slow clock: seconds since it started, moving ~30 times a second ([frameMs] apart, plus the frame), only while
 * [running]. Settings' preview uses a slower one.
 */
@Composable
internal fun rememberSlowClock(running: Boolean, frameMs: Long = 30L): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.nanoTime() - (seconds.floatValue * 1e9f).toLong()
        while (true) {
            withFrameNanos { }
            seconds.floatValue = (System.nanoTime() - start) / 1e9f
            delay(frameMs)   // with the frame itself: at most ~30 frames a second (his cap for moving scenes)
        }
    }
    return seconds
}

/** Time, alarm and battery: read now, then again at the start of every minute. */
@Composable
internal fun rememberMinute(running: Boolean): Minute {
    val context = LocalContext.current
    val charge = remember { ChargeWatch() }
    val minute by produceState(remember { readMinute(context, 0, charge) }, running) {
        if (!running) return@produceState
        value = readMinute(context, value.count, charge)
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
            value = readMinute(context, value.count + 1, charge)
        }
    }
    return minute
}

/** The weather: the saved one at once, then fresh (Seelie asks at most every 30 minutes). */
@Composable
internal fun rememberNightWeather(running: Boolean): Weather? {
    val context = LocalContext.current
    val weather by produceState(remember { WeatherSource.cached(context) }, running) {
        if (!running) return@produceState
        while (true) {
            value = runCatching { WeatherSource.load(context) }.getOrNull() ?: value
            delay(30 * 60_000L)
        }
    }
    return weather
}

/** His events from now until the end of tomorrow, read every few minutes (a small query on the phone). */
@Composable
internal fun rememberNightEvents(running: Boolean, allowed: Boolean): List<CalEvent> {
    val context = LocalContext.current
    val events by produceState(emptyList<CalEvent>(), running, allowed) {
        if (!running || !allowed) return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { runCatching { AgendaSource.upcoming(context) }.getOrDefault(emptyList()) }
            delay(5 * 60_000L)
        }
    }
    return events
}

/**
 * How bright the room is, as one of four steps like the mock-up's Day / Evening / Night / Dark room, from the phone's
 * light sensor. Gentler than the mock-up's (1, 0.85, 0.65, 0.45 instead of down to 0.3): the phone's own
 * auto-brightness already turns the screen down in a dark room, and both together made it hard to read. Null when the phone has no light sensor (then Bedtime dimming is used).
 * The sensor is a cheap one Android keeps on for auto-brightness anyway; Seelie only listens while [running].
 */
@Composable
internal fun rememberRoomLight(running: Boolean, wanted: Boolean): Float? {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(SensorManager::class.java) }
    val sensor = remember { manager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    var step by remember { mutableIntStateOf(1) }
    DisposableEffect(running, wanted, sensor) {
        if (!running || !wanted || sensor == null || manager == null) return@DisposableEffect onDispose { }
        var smooth = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val lux = e.values.firstOrNull() ?: return
                smooth = if (smooth < 0f) lux else smooth + (lux - smooth) * 0.15f   // calm: a passing hand doesn't flash it
                // Hysteresis: a step only changes once the light is clearly past the line, so it doesn't flicker.
                val target = when {
                    smooth >= 160f -> 0
                    smooth >= 40f -> 1
                    smooth >= 6f -> 2
                    else -> 3
                }
                val lines = floatArrayOf(160f, 40f, 6f)
                if (target != step) {
                    val line = lines[minOf(target, step).coerceAtMost(2)]
                    if (kotlin.math.abs(smooth - line) > line * 0.25f) step = target
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { manager.unregisterListener(listener) }
    }
    if (sensor == null) return null
    return ROOM_STEPS[step]
}

private val ROOM_STEPS = floatArrayOf(1f, 0.85f, 0.65f, 0.45f)

/** True between [start] and [end] (minutes after midnight), also across midnight (23:30 → 07:00). */
internal fun inWindow(nowMinutes: Int, start: Int, end: Int): Boolean =
    if (start <= end) nowMinutes in start until end else nowMinutes >= start || nowMinutes < end

/** Remembers the first battery reading, to guess "full in N min" when Android can't say. */
internal class ChargeWatch {
    var firstAt = 0L
    var firstLevel = -1
}

/** The minute's facts: the time, the next alarm and the battery's last report. */
internal fun readMinute(context: Context, count: Int, watch: ChargeWatch): Minute {
    val alarm = runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
    // The battery's last status (a "sticky" broadcast: Android keeps the latest one for anyone to read).
    val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    val now = System.currentTimeMillis()

    // "Full in N min": Android's own guess (Android 9+), else from how fast it charged since the night screen started.
    var fullIn: Int? = null
    if (charging && percent < 100) {
        val ms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining() }.getOrNull() ?: -1L
        } else -1L
        if (ms > 0L) fullIn = (ms / 60_000L).toInt().coerceAtLeast(1)
        else if (watch.firstLevel in 0 until percent) {
            val perMin = (percent - watch.firstLevel) / ((now - watch.firstAt) / 60_000f).coerceAtLeast(1f)
            if (perMin > 0.05f) fullIn = ((100 - percent) / perMin).toInt().coerceAtLeast(1)
        }
    }
    if (!charging) watch.firstLevel = -1
    else if (watch.firstLevel < 0) { watch.firstLevel = percent; watch.firstAt = now }
    return Minute(LocalDateTime.now(), alarm, percent, charging, fullIn, count)
}
