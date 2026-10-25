package com.shamil.launcher.night

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LookStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * Seelie's night screen, first test (25 Oct, the "Seelie StandBy" he picked from the night-and-lock mock-up).
 * A screen saver ("dream" in Android's words): the phone shows it by itself while charging, once he picks Seelie
 * under Settings → Display → Screen saver. This spike answers three questions on his S24 Ultra:
 *   1. does Samsung list Seelie there, 2. does it start (and look right) sideways on the charger,
 *   3. can he swipe between the two pages without the phone waking up and closing it.
 * Interactive: a touch does NOT close it (so the pages can swipe). The ✕ in the corner or Back does.
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

// The mock-up's colours (night-and-lock.html, n2-standby).
private val SkyTop = Color(0xFF04071A)
private val SkyMid = Color(0xFF0C1633)
private val SkyLow = Color(0xFF1D1A47)
private val Gold = Color(0xFFD3BC8E)
private val CreamTop = Color(0xFFF8EFD9)
private val CreamLow = Color(0xFFEFE1C0)
private val Ink = Color(0xFF171C29)
private val StarCream = Color(0xFFFFF6E0)

/** The picture is dimmed to about 70 %: a black veil of 30 % over everything (cheaper than fading the whole layer). */
private const val VEIL = 0.30f

/** What changes once a minute: the time, the next alarm and the battery. */
private data class Minute(val now: LocalDateTime, val alarm: Long?, val battery: Int, val charging: Boolean, val count: Int)

@Composable
private fun NightScreen(running: Boolean, body: FontFamily, onClose: () -> Unit) {
    val context = LocalContext.current

    // One slow clock (about 25 frames a second) for the stars and the breathing colon, only while the dream is on screen.
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val start = System.nanoTime() - (seconds.floatValue * 1e9f).toLong()
        while (true) {
            withFrameNanos { }
            seconds.floatValue = (System.nanoTime() - start) / 1e9f
            delay(32)
        }
    }

    // Time, alarm and battery: read now, then again at the start of every minute.
    // (The value stays while the dream pauses; when it comes back it's read fresh straight away.)
    val minute by produceState(remember { readMinute(context, 0) }, running) {
        if (!running) return@produceState
        value = readMinute(context, value.count)
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
            value = readMinute(context, value.count + 1)
        }
    }

    // Burn-in care: everything drifts a few dp to a new spot every minute, gliding there over 3 s.
    val driftX by animateDpAsState((cos(minute.count * 0.9) * 7).dp, tween(3000), label = "drift x")
    val driftY by animateDpAsState((sin(minute.count * 1.3) * 5).dp, tween(3000), label = "drift y")

    val pager = rememberPagerState { 2 }
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha = VEIL)) }
    ) {
        // The sky on its own layer, so a twinkle redraws only the sky, not the clock and cards.
        Box(Modifier.matchParentSize().graphicsLayer { }.nightSky(seconds))

        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { with(density) { translationX = driftX.toPx(); translationY = driftY.toPx() } }
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                val wide = maxWidth > maxHeight
                if (page == 0) {
                    // Clock + date on the left, cards on the right (sideways); stacked when upright.
                    // A Cinzel "12:45" is about 3.4 times as wide as its size; the sizes keep it inside its half.
                    val clockSize = if (wide) minOf(maxHeight * 0.34f, maxWidth * 0.5f / 3.4f) else minOf(maxWidth * 0.9f / 3.4f, maxHeight * 0.16f)
                    if (wide) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1.15f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Clock(minute.now, clockSize, seconds)
                                DateLine(minute.now)
                            }
                            Spacer(Modifier.width(28.dp))
                            Cards(minute, body, Modifier.weight(1f).widthIn(max = 340.dp))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Clock(minute.now, clockSize, seconds)
                            DateLine(minute.now)
                            Spacer(Modifier.height(36.dp))
                            Cards(minute, body, Modifier.fillMaxWidth().widthIn(max = 380.dp))
                        }
                    }
                } else {
                    // Page 2: only the clock, as big as it fits.
                    val big = if (wide) minOf(maxHeight * 0.6f, maxWidth * 0.86f / 3.4f) else maxWidth * 0.9f / 3.4f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Clock(minute.now, big, seconds)
                        DateLine(minute.now)
                    }
                }
            }
        }

        // Page dots.
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { i ->
                Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) Gold else Gold.copy(alpha = 0.3f)))
            }
        }

        // The way out: a touch doesn't close an interactive screen saver, so a small ✕ (46dp to hit) does.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(46.dp)
                .clip(CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", style = TextStyle(color = Gold.copy(alpha = 0.55f), fontSize = 18.sp))
        }
    }
}

/** The big gold Cinzel clock; only the colon breathes (its fade follows the slow clock, no recomposing). */
@Composable
private fun Clock(now: LocalDateTime, size: Dp, seconds: FloatState) {
    val context = LocalContext.current
    val h24 = DateFormat.is24HourFormat(context)
    val hour = if (h24) "%02d".format(now.hour) else (if (now.hour % 12 == 0) 12 else now.hour % 12).toString()
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.SemiBold, color = Gold, fontSize = fontSize,
        shadow = androidx.compose.ui.graphics.Shadow(Gold.copy(alpha = 0.45f), blurRadius = 28f))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(hour, style = style, maxLines = 1)
        Text(":", style = style, maxLines = 1, modifier = Modifier.graphicsLayer {
            alpha = 0.3f + 0.7f * (0.5f + 0.5f * cos(2f * PI.toFloat() * seconds.floatValue / 4f))
        })
        Text("%02d".format(now.minute), style = style, maxLines = 1)
    }
}

/** "SATURDAY · 25 OCTOBER" in small gold capitals. */
@Composable
private fun DateLine(now: LocalDateTime) {
    val text = remember(now.toLocalDate()) {
        now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.getDefault())).uppercase(Locale.getDefault())
    }
    Text(text, maxLines = 1, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Medium, color = Gold, fontSize = 15.sp, letterSpacing = 3.sp))
}

/** The cream cards: next alarm, battery, and a placeholder for weather. */
@Composable
private fun Cards(m: Minute, body: FontFamily, modifier: Modifier) {
    val context = LocalContext.current
    val h24 = DateFormat.is24HourFormat(context)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val alarm = m.alarm
        if (alarm != null) {
            val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(alarm), ZoneId.systemDefault())
            val time = at.format(DateTimeFormatter.ofPattern(if (h24) "HH:mm" else "h:mm a", Locale.getDefault()))
            val mins = ((alarm - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0)
            val left = if (mins >= 60) "in ${mins / 60} h ${mins % 60} min" else "in $mins min"
            Card(R.drawable.ic_schedule, "Next alarm", time, left, body)
        } else {
            Card(R.drawable.ic_schedule, "Next alarm", "No alarm set", null, body)
        }
        Card(R.drawable.ic_battery_full, "Battery", "${m.battery} %", if (m.charging) "Charging" else "Not charging", body)
        Card(R.drawable.ic_clear_night, "Weather", "Weather comes in the full version", null, body)
    }
}

@Composable
private fun Card(@DrawableRes icon: Int, label: String, value: String, sub: String?, body: FontFamily) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CreamTop, CreamLow)))
            .drawWithContent { drawContent(); drawRect(Gold, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .padding(start = 18.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Ink.copy(alpha = 0.75f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), maxLines = 1, style = TextStyle(fontFamily = Cinzel, fontWeight = FontWeight.Bold, color = Ink.copy(alpha = 0.6f), fontSize = 10.sp, letterSpacing = 1.8.sp))
            Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 17.sp, lineHeight = 21.sp))
            if (sub != null) Text(sub, maxLines = 1, style = TextStyle(fontFamily = body, color = Ink.copy(alpha = 0.65f), fontSize = 13.sp))
        }
    }
}

/** One star: where (0–1 of the screen), how big, and its own twinkle speed and starting point. */
private class Star(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)

/** The night sky: a dark gradient and ~70 stars that twinkle with the slow clock (only the drawing redoes, nothing else). */
private fun Modifier.nightSky(seconds: FloatState): Modifier = drawWithCache {
    val sky = Brush.verticalGradient(listOf(SkyTop, SkyMid, SkyLow))
    val rnd = Random(7)
    val stars = List(70) {
        Star(rnd.nextFloat(), rnd.nextFloat() * 0.92f, (0.6f + rnd.nextFloat() * 1.3f).dp.toPx(), 0.4f + rnd.nextFloat() * 1.2f, rnd.nextFloat() * 6.28f)
    }
    onDrawBehind {
        drawRect(sky)
        val t = seconds.floatValue
        stars.forEach { s ->
            val a = 0.25f + 0.6f * (0.5f + 0.5f * sin(t * s.speed + s.phase))
            val c = Offset(s.x * size.width, s.y * size.height)
            if (s.r > 1.5.dp.toPx()) drawCircle(StarCream.copy(alpha = a * 0.18f), s.r * 3.2f, c)
            drawCircle(StarCream.copy(alpha = a), s.r, c)
        }
    }
}

/** The minute's facts: the time, the next alarm (any clock app's, from Android) and the battery's last report. */
private fun readMinute(context: Context, count: Int): Minute {
    val alarm = runCatching { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }.getOrNull()
    // The battery's last status (a "sticky" broadcast: Android keeps the latest one for anyone to read).
    val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Minute(LocalDateTime.now(), alarm, percent, charging, count)
}
