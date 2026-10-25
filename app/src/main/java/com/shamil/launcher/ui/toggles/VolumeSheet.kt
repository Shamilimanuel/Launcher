package com.shamil.launcher.ui.toggles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.shamil.launcher.R
import com.shamil.launcher.ui.theme.Cinzel
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore
import kotlinx.coroutines.delay

/*
 * Seelie's own volume (25 Oct, from a video he liked: "volume styles", colours that match the wallpaper, a little
 * sound). Since his "it doesn't come on the side bar" it's a side bar ([VolumeSideBar]); the phone's + / − buttons
 * open it everywhere through [com.shamil.launcher.volume.SeelieVolumeService]. This host is the fallback on the home
 * for the Volume toggle while that service is switched off.
 */

/** The looks. The ids are saved (in the quick toggles setup, so a save keeps it), never rename them. */
enum class VolumeLook(val id: String, val title: String) {
    GOLD("gold", "Gold bar"),
    GLASS("glass", "Glass"),
    LANTERN("lantern", "Lanterns"),
    ELEMENT("element", "Elements"),
    WALLPAPER("wallpaper", "Wallpaper colours"),
    SYSTEM("system", "Phone's own");

    companion object {
        fun byId(id: String?): VolumeLook = entries.firstOrNull { it.id == id } ?: GOLD
    }
}

/** Opened by the Volume toggle; drawn over the home by [VolumeSheetHost]. */
object VolumeSheet {
    var open by mutableStateOf(false)
    /** How many screens draw the sheet right now (the home). Settings has none, so its Volume button uses the phone's. */
    var hosts by mutableIntStateOf(0)
}

// Element colours for the Elements look: calm hydro at low volume, anemo, geo, then pyro at the top.
private val Hydro = Color(0xFF5BC0EB)
private val Anemo = Color(0xFF74C2A8)
private val Geo = Color(0xFFF0B232)
private val Pyro = Color(0xFFF08A5D)
private val Gold = Color(0xFFF3D79C)

/** The sheet, over everything on the home. Closes on a tap outside, Back, or after a few quiet seconds. */
@Composable
fun BoxScope.VolumeSheetHost() {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val setup = TogglePrefs.get(context).setup
    val look = VolumeLook.byId(setup.volumeLook)
    val max = remember(audio) { runCatching { audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: 15 }
    // Read again each time it opens, so the slider starts at the real level instead of springing to it.
    var level by remember(VolumeSheet.open) { mutableIntStateOf(runCatching { audio?.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: 0) }
    DisposableEffect(Unit) {
        VolumeSheet.hosts++
        onDispose { VolumeSheet.hosts--; VolumeSheet.open = false }
    }
    // Leaving the home (an app opens, the screen goes off) closes it.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) VolumeSheet.open = false }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    var ringer by remember { mutableStateOf(ToggleActions.ringer(context)) }
    // Touching the sheet pushes the "close by itself" moment back.
    var touched by remember { mutableIntStateOf(0) }

    // Keep up with the volume keys while it's open (Android announces every change).
    if (VolumeSheet.open) {
        DisposableEffect(Unit) {
            level = runCatching { audio?.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: 0
            ringer = ToggleActions.ringer(context)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, i: Intent?) {
                    level = runCatching { audio?.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrNull() ?: level
                    ringer = ToggleActions.ringer(context)
                }
            }
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply { addAction(AudioManager.RINGER_MODE_CHANGED_ACTION) }
            runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
            onDispose { runCatching { context.unregisterReceiver(receiver) } }
        }
        LaunchedEffect(touched) {
            delay(3000)
            VolumeSheet.open = false
        }
        BackHandler { VolumeSheet.open = false }
    }

    // A tap anywhere else puts it away (no dark scrim: it's a slim bar, like the phone's own).
    if (VolumeSheet.open) Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { VolumeSheet.open = false })
    val left = setup.volumeEdge == "left"
    val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    AnimatedVisibility(
        VolumeSheet.open,
        modifier = Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd).padding(top = (screenH * 0.2f).dp, start = 10.dp, end = 10.dp),
        enter = slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = 500f)) { if (left) -it * 2 else it * 2 } + fadeIn(),
        exit = slideOutHorizontally(tween(220)) { if (left) -it * 2 else it * 2 } + fadeOut(),
    ) {
        VolumeSideBar(level, max, look, ringer, left,
            onChange = { v ->
                touched++
                if (v != level) {
                    runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
                    if (setup.volumeClick) runCatching { audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.4f) }
                    level = v
                }
            },
            onRinger = { touched++; ringer = cycleRinger(context) })
    }
}

/**
 * The slider in one of the [VolumeLook]s, 0..[max] steps. Drag or tap anywhere on it. Also used as the preview in
 * Quick toggles settings (with [onChange] doing nothing).
 */
@Composable
fun VolumeSlider(level: Int, max: Int, look: VolumeLook, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val theme = LocalLauncherLook.current
    val wallpaper = LookStore.get(context).wallpaperLook
    val fraction by animateFloatAsState(level.toFloat() / max.coerceAtLeast(1), spring(stiffness = 700f), label = "volume")
    fun step(x: Float, width: Float) = ((x / width).coerceIn(0f, 1f) * max).let { kotlin.math.round(it).toInt() }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(if (look == VolumeLook.LANTERN) 46.dp else 36.dp)
            .pointerInput(max) { detectTapGestures { onChange(step(it.x, size.width.toFloat())) } }
            .pointerInput(max) { detectHorizontalDragGestures { change, _ -> change.consume(); onChange(step(change.position.x, size.width.toFloat())) } }
    ) {
        val w = size.width
        val h = size.height
        val f = fraction
        when (look) {
            VolumeLook.LANTERN -> {
                // A row of little lanterns: the lit ones glow warm, the rest wait dark.
                val n = max.coerceIn(5, 25)
                val gap = w / n
                for (i in 0 until n) {
                    val lit = (i + 0.5f) / n <= f
                    val cx = gap * (i + 0.5f)
                    if (lit) drawCircle(Brush.radialGradient(listOf(Color(0x99FFB45E), Color.Transparent), center = Offset(cx, h / 2), radius = gap * 0.9f), radius = gap * 0.9f, center = Offset(cx, h / 2))
                    drawRoundRect(if (lit) Color(0xFFFFD58F) else Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(cx - gap * 0.22f, h * 0.22f), size = Size(gap * 0.44f, h * 0.56f), cornerRadius = CornerRadius(gap * 0.18f))
                }
            }
            else -> {
                val track = h * (if (look == VolumeLook.GLASS) 0.32f else 0.42f)
                val top = (h - track) / 2
                val r = CornerRadius(track / 2)
                drawRoundRect(Color.White.copy(alpha = if (look == VolumeLook.GLASS) 0.12f else 0.1f), Offset(0f, top), Size(w, track), r)
                val fill = when (look) {
                    VolumeLook.GOLD -> Brush.horizontalGradient(listOf(Color(0xFFD3BC8E), Gold))
                    VolumeLook.GLASS -> Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.9f)))
                    VolumeLook.ELEMENT -> Brush.horizontalGradient(listOf(Hydro, Anemo, Geo, Pyro), endX = w)
                    VolumeLook.WALLPAPER -> {
                        val a = wallpaper?.accent ?: theme.accent
                        Brush.horizontalGradient(listOf(lerp(a, Color.Black, 0.25f), a))
                    }
                    VolumeLook.SYSTEM, VolumeLook.LANTERN -> Brush.horizontalGradient(listOf(theme.accent, theme.accent))
                }
                if (f > 0f) drawRoundRect(fill, Offset(0f, top), Size(w * f, track), r)
                // The thumb: a glowing orb (gold, elements, wallpaper) or a clean white pill (glass).
                val x = (w * f).coerceIn(h / 2, w - h / 2)
                val thumbColour = when (look) {
                    VolumeLook.GLASS -> Color.White
                    VolumeLook.ELEMENT -> lerp(lerp(Hydro, Anemo, (f * 3).coerceIn(0f, 1f)), lerp(Geo, Pyro, ((f - 0.66f) * 3).coerceIn(0f, 1f)), ((f - 0.33f) * 3).coerceIn(0f, 1f))
                    VolumeLook.WALLPAPER -> wallpaper?.accent ?: theme.accent
                    else -> Gold
                }
                drawCircle(Brush.radialGradient(listOf(thumbColour.copy(alpha = 0.45f), Color.Transparent), center = Offset(x, h / 2), radius = h * 0.75f), radius = h * 0.75f, center = Offset(x, h / 2))
                drawCircle(thumbColour, radius = h * 0.32f, center = Offset(x, h / 2))
            }
        }
    }
}
