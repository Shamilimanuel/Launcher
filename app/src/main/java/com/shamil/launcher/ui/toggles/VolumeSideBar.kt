package com.shamil.launcher.ui.toggles

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shamil.launcher.ui.theme.LocalLauncherLook
import com.shamil.launcher.ui.theme.LookStore

/*
 * Seelie's volume side bar (25 Oct, his ask: "it doesn't come on the side bar"): a slim tall bar at the screen edge,
 * next to the real volume buttons. The volume service draws it over any app; the home draws it for the Volume
 * toggle when the service is off. Same looks as before, now standing up. More looks come from his mock-up picks.
 */

// Water blue at the bottom, wind green, earth gold, fire orange at the top (the Elements look).
private val Water = Color(0xFF5BC0EB)
private val Wind = Color(0xFF74C2A8)
private val Earth = Color(0xFFF0B232)
private val Fire = Color(0xFFF08A5D)
private val BarGold = Color(0xFFF3D79C)

/** The whole side bar: % on top, the tall slider, a round ringer button below. [left] = it sits on the left edge. */
@Composable
fun VolumeSideBar(
    level: Int,
    max: Int,
    look: VolumeLook,
    ringer: String?,
    left: Boolean,
    onChange: (Int) -> Unit,
    onRinger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherLook.current
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier
            .width(72.dp)
            .clip(shape)
            .background(theme.sheetTop.copy(alpha = 0.95f))
            .border(1.dp, theme.accent.copy(alpha = 0.35f), shape)
            .clickable(remember { MutableInteractionSource() }, null) {}   // taps on the frame don't fall through
            .semantics { contentDescription = if (left) "Volume, left edge" else "Volume" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(14.dp))
        Text("${(level * 100f / max.coerceAtLeast(1)).toInt()}%",
            style = TextStyle(fontFamily = theme.font, fontWeight = FontWeight.Bold, color = theme.accent, fontSize = 13.sp))
        Spacer(Modifier.height(10.dp))
        VolumeBar(level, max, look, Modifier.width(48.dp).height(236.dp), onChange)
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(theme.card).clickable(onClick = onRinger)
                .semantics { contentDescription = "Sound mode: ${ringer ?: "unknown"}. Tap to change" },
            contentAlignment = Alignment.Center,
        ) { RingerIcon(ringer, theme.accent) }
        Spacer(Modifier.height(13.dp))
    }
}

/** The standing slider in one of the [VolumeLook]s, 0..[max] steps from the bottom up. Drag or tap anywhere on it. */
@Composable
fun VolumeBar(level: Int, max: Int, look: VolumeLook, modifier: Modifier = Modifier, onChange: (Int) -> Unit) {
    val context = LocalContext.current
    val theme = LocalLauncherLook.current
    val wallpaper = LookStore.get(context).wallpaperLook
    val fraction by animateFloatAsState(level.toFloat() / max.coerceAtLeast(1), spring(stiffness = 700f), label = "volume")
    fun step(y: Float, height: Float) = kotlin.math.round((1f - y / height).coerceIn(0f, 1f) * max).toInt()
    Canvas(
        modifier
            .pointerInput(max) { detectTapGestures { onChange(step(it.y, size.height.toFloat())) } }
            .pointerInput(max) { detectVerticalDragGestures { change, _ -> change.consume(); onChange(step(change.position.y, size.height.toFloat())) } }
    ) {
        val w = size.width
        val h = size.height
        val f = fraction
        when (look) {
            VolumeLook.LANTERN -> {
                // A stack of little lanterns: the lit ones (from the bottom) glow warm, the rest wait dark.
                val n = max.coerceIn(5, 25)
                val gap = h / n
                for (i in 0 until n) {
                    val lit = (i + 0.5f) / n <= f
                    val cy = h - gap * (i + 0.5f)
                    if (lit) drawCircle(Brush.radialGradient(listOf(Color(0x99FFB45E), Color.Transparent), center = Offset(w / 2, cy), radius = gap * 1.1f), radius = gap * 1.1f, center = Offset(w / 2, cy))
                    drawRoundRect(if (lit) Color(0xFFFFD58F) else Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(w * 0.3f, cy - gap * 0.32f), size = Size(w * 0.4f, gap * 0.64f), cornerRadius = CornerRadius(gap * 0.2f))
                }
            }
            else -> {
                val track = w * (if (look == VolumeLook.GLASS) 0.34f else 0.5f)
                val x0 = (w - track) / 2
                val r = CornerRadius(track / 2)
                drawRoundRect(Color.White.copy(alpha = if (look == VolumeLook.GLASS) 0.12f else 0.1f), Offset(x0, 0f), Size(track, h), r)
                val fill = when (look) {
                    VolumeLook.GOLD -> Brush.verticalGradient(listOf(BarGold, Color(0xFFD3BC8E)), startY = 0f, endY = h)
                    VolumeLook.GLASS -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.55f)), startY = 0f, endY = h)
                    VolumeLook.ELEMENT -> Brush.verticalGradient(listOf(Fire, Earth, Wind, Water), startY = 0f, endY = h)
                    VolumeLook.WALLPAPER -> {
                        val a = wallpaper?.accent ?: theme.accent
                        Brush.verticalGradient(listOf(a, lerp(a, Color.Black, 0.25f)), startY = 0f, endY = h)
                    }
                    VolumeLook.SYSTEM, VolumeLook.LANTERN -> Brush.verticalGradient(listOf(theme.accent, theme.accent))
                }
                if (f > 0f) drawRoundRect(fill, Offset(x0, h * (1f - f)), Size(track, h * f), r)
                // The thumb: a glowing orb (gold, elements, wallpaper) or a clean white one (glass).
                val y = (h * (1f - f)).coerceIn(w / 2, h - w / 2)
                val thumb = when (look) {
                    VolumeLook.GLASS -> Color.White
                    VolumeLook.ELEMENT -> lerp(lerp(Water, Wind, (f * 3).coerceIn(0f, 1f)), lerp(Earth, Fire, ((f - 0.66f) * 3).coerceIn(0f, 1f)), ((f - 0.33f) * 3).coerceIn(0f, 1f))
                    VolumeLook.WALLPAPER -> wallpaper?.accent ?: theme.accent
                    else -> BarGold
                }
                drawCircle(Brush.radialGradient(listOf(thumb.copy(alpha = 0.45f), Color.Transparent), center = Offset(w / 2, y), radius = w * 0.75f), radius = w * 0.75f, center = Offset(w / 2, y))
                drawCircle(thumb, radius = w * 0.3f, center = Offset(w / 2, y))
            }
        }
    }
}

/** A small bell (Ring), a buzzing phone (Vibrate) or a crossed-out bell (Silent), drawn in [tint]. */
@Composable
private fun RingerIcon(ringer: String?, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        if (ringer == "Vibrate") {
            drawRoundRect(tint, Offset(s * 0.34f, s * 0.14f), Size(s * 0.32f, s * 0.72f), CornerRadius(s * 0.08f), style = stroke)
            listOf(0.16f, 0.84f).forEach { x -> drawLine(tint, Offset(s * x, s * 0.34f), Offset(s * x, s * 0.66f), stroke.width, StrokeCap.Round) }
            return@Canvas
        }
        val bell = Path().apply {
            moveTo(s * 0.22f, s * 0.72f)
            lineTo(s * 0.26f, s * 0.46f)
            cubicTo(s * 0.28f, s * 0.2f, s * 0.72f, s * 0.2f, s * 0.74f, s * 0.46f)
            lineTo(s * 0.78f, s * 0.72f)
            close()
        }
        drawPath(bell, tint, style = stroke)
        drawLine(tint, Offset(s * 0.42f, s * 0.86f), Offset(s * 0.58f, s * 0.86f), stroke.width, StrokeCap.Round)
        if (ringer == "Silent") drawLine(tint, Offset(s * 0.14f, s * 0.14f), Offset(s * 0.86f, s * 0.86f), stroke.width, StrokeCap.Round)
    }
}

/** Ring → Vibrate → Silent → Ring. Silent (and leaving it) can need Do Not Disturb access on newer Android. */
fun cycleRinger(context: Context): String? {
    val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    val next = when (ToggleActions.ringer(context)) {
        "Ring" -> AudioManager.RINGER_MODE_VIBRATE
        "Vibrate" -> AudioManager.RINGER_MODE_SILENT
        else -> AudioManager.RINGER_MODE_NORMAL
    }
    val ok = runCatching { audio?.ringerMode = next }.isSuccess
    if (!ok) Toast.makeText(context, "Android needs Do Not Disturb access for that", Toast.LENGTH_SHORT).show()
    return ToggleActions.ringer(context)
}
